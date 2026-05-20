/**
 * llama4j — Java bindings for llama.cpp
 *
 * Copyright (c) 2024 llama4j contributors
 * SPDX-License-Identifier: MIT
 *
 * JNI 实现 — llama.cpp 原生桥接层
 * ==================================
 * 本文件实现了 llama4j.h 中声明的所有 JNI 原生方法，
 * 将 Java 调用桥接到 llama.cpp 的 C API。
 *
 * 核心设计：
 *   - LlamaSession 结构体封装所有推理状态（模型、上下文、KV 缓存等）
 *   - Java 端只持有 LlamaSession 的不透明指针（jlong），不直接访问内部字段
 *   - 每个会话包含 std::mutex，确保并发推理请求被正确序列化
 *   - 所有 JNI 函数在入口处检查 nativeHandle 有效性
 *
 * 内存管理：
 *   - loadModel 中通过 new 创建 LlamaSession
 *   - freeModel 中通过 delete 销毁 LlamaSession
 *   - LlamaSession 析构函数自动释放 llama_model 和 llama_context
 *   - Java 端的 LlamaContext 实现 AutoCloseable，确保资源释放
 */

#include "llama4j.h"
#include "llama.h"
#include "ggml.h"

#include <cstring>
#include <cstdlib>
#include <memory>
#include <mutex>
#include <string>
#include <vector>
#include <functional>

/* ──────────────────────────────────────────────────────────────
 *  内部会话结构体
 *  ────────────────────────────────────────────────────────────── */
struct LlamaSession {
    llama_model  *model;
    llama_context *ctx;
    std::vector<llama_token> lastTokens;
    std::string   chatTemplate;
    int           nCtx;
    std::mutex    mutex;

    ~LlamaSession() {
        if (ctx)  llama_free(ctx);
        if (model) llama_model_free(model);
    }
};

/* ──────────────────────────────────────────────────────────────
 *  辅助函数
 *  ────────────────────────────────────────────────────────────── */

static void throwJavaException(JNIEnv *env, const char *className, const char *message) {
    jclass exClass = env->FindClass(className);
    if (exClass != nullptr) {
        env->ThrowNew(exClass, message);
    }
}

static std::string tokenToPiece(const llama_vocab *vocab, llama_token token) {
    char buf[256];
    int len = llama_token_to_piece(vocab, token, buf, sizeof(buf), 0, true);
    if (len < 0) {
        std::vector<char> bigBuf(-len + 1);
        len = llama_token_to_piece(vocab, token, bigBuf.data(), -len, 0, true);
        return std::string(bigBuf.data(), len);
    }
    return std::string(buf, len);
}

static std::vector<llama_token> tokenizeHelper(
    const llama_vocab *vocab, const std::string &text, bool addBos)
{
    int nMax = text.size() + 128;
    std::vector<llama_token> tokens(nMax);
    int nTokens = llama_tokenize(vocab, text.c_str(), text.size(),
        tokens.data(), nMax, addBos, true);
    if (nTokens < 0) {
        nMax = -nTokens;
        tokens.resize(nMax);
        nTokens = llama_tokenize(vocab, text.c_str(), text.size(),
            tokens.data(), nMax, addBos, true);
    }
    tokens.resize(nTokens);
    return tokens;
}

/* ──────────────────────────────────────────────────────────────
 *  模型生命周期
 *  ────────────────────────────────────────────────────────────── */

JNIEXPORT jlong JNICALL
Java_com_llama4j_native_1_LlamaContext_loadModel(
    JNIEnv *env, jclass clazz,
    jstring modelPath, jint nCtx, jint nGpuLayers, jint nThreads)
{
    const char *pathStr = env->GetStringUTFChars(modelPath, nullptr);
    if (!pathStr) {
        throwJavaException(env, "java/lang/IllegalArgumentException", "模型路径不能为空");
        return 0;
    }
    std::string path(pathStr);
    env->ReleaseStringUTFChars(modelPath, pathStr);

    auto modelParams = llama_model_default_params();
    modelParams.n_gpu_layers = nGpuLayers;

    llama_model *model = llama_model_load_from_file(path.c_str(), modelParams);
    if (!model) {
        throwJavaException(env, "java/io/IOException",
            ("无法加载模型: " + path).c_str());
        return 0;
    }

    auto ctxParams = llama_context_default_params();
    ctxParams.n_ctx   = nCtx;
    ctxParams.n_batch = 512;
    ctxParams.n_threads = nThreads;
    ctxParams.n_threads_batch = nThreads;

    llama_context *ctx = llama_init_from_model(model, ctxParams);
    if (!ctx) {
        llama_model_free(model);
        throwJavaException(env, "java/io/IOException",
            "无法创建推理上下文，可能是内存不足");
        return 0;
    }

    std::string chatTemplate;
    int tmplLen = llama_model_meta_val_str(model, "chat_template", nullptr, 0);
    if (tmplLen > 0) {
        chatTemplate.resize(tmplLen + 1);
        llama_model_meta_val_str(model, "chat_template",
            chatTemplate.data(), chatTemplate.size());
    }

    auto *session = new LlamaSession();
    session->model = model;
    session->ctx = ctx;
    session->chatTemplate = chatTemplate;
    session->nCtx = nCtx;

    return reinterpret_cast<jlong>(session);
}

JNIEXPORT void JNICALL
Java_com_llama4j_native_1_LlamaContext_freeModel(
    JNIEnv *env, jclass clazz, jlong nativeHandle)
{
    if (nativeHandle == 0) return;
    auto *session = reinterpret_cast<LlamaSession *>(nativeHandle);
    delete session;
}

/* ──────────────────────────────────────────────────────────────
 *  分词
 *  ────────────────────────────────────────────────────────────── */

JNIEXPORT jintArray JNICALL
Java_com_llama4j_native_1_LlamaContext_tokenize(
    JNIEnv *env, jclass clazz,
    jlong nativeHandle, jstring text, jboolean addBos)
{
    if (nativeHandle == 0) return env->NewIntArray(0);
    auto *session = reinterpret_cast<LlamaSession *>(nativeHandle);

    const char *textStr = env->GetStringUTFChars(text, nullptr);
    std::string input(textStr);
    env->ReleaseStringUTFChars(text, textStr);

    const llama_vocab *vocab = llama_model_get_vocab(session->model);
    std::vector<llama_token> tokens = tokenizeHelper(vocab, input, addBos);

    jintArray result = env->NewIntArray(static_cast<jsize>(tokens.size()));
    env->SetIntArrayRegion(result, 0, static_cast<jsize>(tokens.size()),
        reinterpret_cast<const jint *>(tokens.data()));
    return result;
}

JNIEXPORT jstring JNICALL
Java_com_llama4j_native_1_LlamaContext_tokenToStr(
    JNIEnv *env, jclass clazz,
    jlong nativeHandle, jint tokenId)
{
    if (nativeHandle == 0) return env->NewStringUTF("");
    auto *session = reinterpret_cast<LlamaSession *>(nativeHandle);

    const llama_vocab *vocab = llama_model_get_vocab(session->model);
    std::string piece = tokenToPiece(vocab, tokenId);
    return env->NewStringUTF(piece.c_str());
}

/* ──────────────────────────────────────────────────────────────
 *  推理生成
 *  ────────────────────────────────────────────────────────────── */

JNIEXPORT jstring JNICALL
Java_com_llama4j_native_1_LlamaContext_generate(
    JNIEnv *env, jclass clazz,
    jlong nativeHandle, jstring prompt,
    jint maxTokens, jfloat temperature,
    jint topK, jfloat topP,
    jfloat repeatPenalty, jlong seed)
{
    if (nativeHandle == 0) return env->NewStringUTF("");
    auto *session = reinterpret_cast<LlamaSession *>(nativeHandle);

    std::lock_guard<std::mutex> lock(session->mutex);

    const char *promptStr = env->GetStringUTFChars(prompt, nullptr);
    std::string promptText(promptStr);
    env->ReleaseStringUTFChars(prompt, promptStr);

    const llama_vocab *vocab = llama_model_get_vocab(session->model);
    std::vector<llama_token> tokens = tokenizeHelper(vocab, promptText, true);

    llama_batch batch = llama_batch_get_one(tokens.data(), tokens.size());
    llama_decode(session->ctx, batch);

    auto *smpl = llama_sampler_chain_init(llama_sampler_chain_default_params());
    llama_sampler_chain_add(smpl, llama_sampler_init_penalties(
        64, repeatPenalty, 0.0f, 0.0f));
    if (temperature > 0.0f) {
        llama_sampler_chain_add(smpl, llama_sampler_init_temp(temperature));
        llama_sampler_chain_add(smpl, llama_sampler_init_top_k(topK));
        llama_sampler_chain_add(smpl, llama_sampler_init_top_p(topP, 1));
        llama_sampler_chain_add(smpl, llama_sampler_init_dist(seed >= 0 ? seed : (uint32_t)time(nullptr)));
    } else {
        llama_sampler_chain_add(smpl, llama_sampler_init_greedy());
    }

    std::string result;
    session->lastTokens = tokens;

    for (int i = 0; i < maxTokens; i++) {
        llama_token newToken = llama_sampler_sample(smpl, session->ctx, -1);

        if (llama_vocab_is_eog(vocab, newToken)) break;

        result += tokenToPiece(vocab, newToken);

        batch = llama_batch_get_one(&newToken, 1);
        llama_decode(session->ctx, batch);
        session->lastTokens.push_back(newToken);
    }

    llama_sampler_free(smpl);
    return env->NewStringUTF(result.c_str());
}

JNIEXPORT void JNICALL
Java_com_llama4j_native_1_LlamaContext_generateStream(
    JNIEnv *env, jclass clazz,
    jlong nativeHandle, jstring prompt,
    jint maxTokens, jfloat temperature,
    jint topK, jfloat topP,
    jfloat repeatPenalty, jlong seed,
    jobject callback)
{
    if (nativeHandle == 0 || !callback) return;
    auto *session = reinterpret_cast<LlamaSession *>(nativeHandle);

    std::lock_guard<std::mutex> lock(session->mutex);

    jclass callbackClass = env->GetObjectClass(callback);
    jmethodID onTokenMethod = env->GetMethodID(callbackClass, "onToken", "(Ljava/lang/String;)V");

    const char *promptStr = env->GetStringUTFChars(prompt, nullptr);
    std::string promptText(promptStr);
    env->ReleaseStringUTFChars(prompt, promptStr);

    const llama_vocab *vocab = llama_model_get_vocab(session->model);
    std::vector<llama_token> tokens = tokenizeHelper(vocab, promptText, true);
    llama_batch batch = llama_batch_get_one(tokens.data(), tokens.size());
    llama_decode(session->ctx, batch);

    auto *smpl = llama_sampler_chain_init(llama_sampler_chain_default_params());
    llama_sampler_chain_add(smpl, llama_sampler_init_penalties(
        64, repeatPenalty, 0.0f, 0.0f));
    if (temperature > 0.0f) {
        llama_sampler_chain_add(smpl, llama_sampler_init_temp(temperature));
        llama_sampler_chain_add(smpl, llama_sampler_init_top_k(topK));
        llama_sampler_chain_add(smpl, llama_sampler_init_top_p(topP, 1));
        llama_sampler_chain_add(smpl, llama_sampler_init_dist(seed >= 0 ? seed : (uint32_t)time(nullptr)));
    } else {
        llama_sampler_chain_add(smpl, llama_sampler_init_greedy());
    }

    session->lastTokens = tokens;

    for (int i = 0; i < maxTokens; i++) {
        llama_token newToken = llama_sampler_sample(smpl, session->ctx, -1);
        if (llama_vocab_is_eog(vocab, newToken)) break;

        std::string piece = tokenToPiece(vocab, newToken);

        jstring jPiece = env->NewStringUTF(piece.c_str());
        env->CallVoidMethod(callback, onTokenMethod, jPiece);
        env->DeleteLocalRef(jPiece);

        batch = llama_batch_get_one(&newToken, 1);
        llama_decode(session->ctx, batch);
        session->lastTokens.push_back(newToken);
    }

    llama_sampler_free(smpl);
}

/* ──────────────────────────────────────────────────────────────
 *  KV 缓存管理
 *  ────────────────────────────────────────────────────────────── */

JNIEXPORT jbyteArray JNICALL
Java_com_llama4j_native_1_LlamaContext_saveSession(
    JNIEnv *env, jclass clazz, jlong nativeHandle)
{
    if (nativeHandle == 0) return env->NewByteArray(0);
    auto *session = reinterpret_cast<LlamaSession *>(nativeHandle);

    size_t dataSize = llama_state_get_size(session->ctx);
    std::vector<uint8_t> buffer(dataSize);

    size_t written = llama_state_get_data(session->ctx, buffer.data(), dataSize);

    jbyteArray result = env->NewByteArray(static_cast<jsize>(written));
    env->SetByteArrayRegion(result, 0, static_cast<jsize>(written),
        reinterpret_cast<const jbyte *>(buffer.data()));
    return result;
}

JNIEXPORT void JNICALL
Java_com_llama4j_native_1_LlamaContext_loadSession(
    JNIEnv *env, jclass clazz,
    jlong nativeHandle, jbyteArray sessionData)
{
    if (nativeHandle == 0 || !sessionData) return;
    auto *session = reinterpret_cast<LlamaSession *>(nativeHandle);

    jsize dataLen = env->GetArrayLength(sessionData);
    jbyte *data = env->GetByteArrayElements(sessionData, nullptr);

    size_t loaded = llama_state_set_data(session->ctx,
        reinterpret_cast<const uint8_t *>(data), dataLen);
    env->ReleaseByteArrayElements(sessionData, data, JNI_ABORT);

    if (loaded == 0) {
        throwJavaException(env, "java/io/IOException", "恢复会话状态失败");
    }
}

/* ──────────────────────────────────────────────────────────────
 *  模型元数据查询
 *  ────────────────────────────────────────────────────────────── */

JNIEXPORT jstring JNICALL
Java_com_llama4j_native_1_LlamaContext_getChatTemplate(
    JNIEnv *env, jclass clazz, jlong nativeHandle)
{
    if (nativeHandle == 0) return env->NewStringUTF("");
    auto *session = reinterpret_cast<LlamaSession *>(nativeHandle);
    return env->NewStringUTF(session->chatTemplate.c_str());
}

JNIEXPORT jint JNICALL
Java_com_llama4j_native_1_LlamaContext_getVocabSize(
    JNIEnv *env, jclass clazz, jlong nativeHandle)
{
    if (nativeHandle == 0) return 0;
    auto *session = reinterpret_cast<LlamaSession *>(nativeHandle);
    const llama_vocab *vocab = llama_model_get_vocab(session->model);
    return llama_vocab_n_tokens(vocab);
}

JNIEXPORT jint JNICALL
Java_com_llama4j_native_1_LlamaContext_getContextSize(
    JNIEnv *env, jclass clazz, jlong nativeHandle)
{
    if (nativeHandle == 0) return 0;
    auto *session = reinterpret_cast<LlamaSession *>(nativeHandle);
    return session->nCtx;
}

JNIEXPORT jint JNICALL
Java_com_llama4j_native_1_LlamaContext_getKvCacheTokenCount(
    JNIEnv *env, jclass clazz, jlong nativeHandle)
{
    if (nativeHandle == 0) return 0;
    auto *session = reinterpret_cast<LlamaSession *>(nativeHandle);
    return static_cast<jint>(session->lastTokens.size());
}
