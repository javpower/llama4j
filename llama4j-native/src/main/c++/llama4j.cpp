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
#include "mtmd.h"
#include "mtmd-helper.h"

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
    int           nBatch;
    std::mutex    mutex;
    int           lastPromptTokens = 0;
    int           lastCompletionTokens = 0;
    mtmd_context *mtmdCtx = nullptr;

    ~LlamaSession() {
        if (mtmdCtx) mtmd_free(mtmdCtx);
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

/**
 * 分批将 prompt tokens 送入 llama_decode。
 *
 * 当 token 数量超过 n_batch 时，自动按 n_batch 大小切分，
 * 避免触发 n_tokens_all <= cparams.n_batch 断言。
 */
static void decodePromptInBatches(llama_context *ctx,
                                   llama_token *tokens, int nTokens,
                                   int nBatch) {
    for (int i = 0; i < nTokens; i += nBatch) {
        int batchSize = std::min(nBatch, nTokens - i);
        llama_batch batch = llama_batch_get_one(tokens + i, batchSize);
        int32_t res = llama_decode(ctx, batch);
        if (res < 0) {
            fprintf(stderr, "llama4j: llama_decode fatal error (%d) at prompt batch offset %d\n", res, i);
            return;
        }
    }
}

/**
 * 分批将 prompt tokens 送入 llama_encode（用于 embeddings）。
 */
static void encodeInBatches(llama_context *ctx,
                             llama_token *tokens, int nTokens,
                             int nBatch) {
    for (int i = 0; i < nTokens; i += nBatch) {
        int batchSize = std::min(nBatch, nTokens - i);
        llama_batch batch = llama_batch_get_one(tokens + i, batchSize);
        int32_t res = llama_encode(ctx, batch);
        if (res < 0) {
            fprintf(stderr, "llama4j: llama_encode fatal error (%d) at batch offset %d\n", res, i);
            return;
        }
    }
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
    const char* keys[] = {"tokenizer.chat_template", "chat_template"};
    for (auto key : keys) {
        int tmplLen = llama_model_meta_val_str(model, key, nullptr, 0);
        if (tmplLen > 0) {
            chatTemplate.resize(tmplLen + 1);
            llama_model_meta_val_str(model, key, &chatTemplate[0], tmplLen + 1);
            chatTemplate.resize(tmplLen);
            break;
        }
    }

    auto *session = new LlamaSession();
    session->model = model;
    session->ctx = ctx;
    session->chatTemplate = chatTemplate;
    session->nCtx = nCtx;
    session->nBatch = ctxParams.n_batch;

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
Java_com_llama4j_native_1_LlamaContext_detokenize(
    JNIEnv *env, jclass clazz,
    jlong nativeHandle, jintArray tokens)
{
    if (nativeHandle == 0) return env->NewStringUTF("");
    auto *session = reinterpret_cast<LlamaSession *>(nativeHandle);

    const llama_vocab *vocab = llama_model_get_vocab(session->model);
    jsize n = env->GetArrayLength(tokens);
    jint *elems = env->GetIntArrayElements(tokens, nullptr);

    std::string result;
    for (jsize i = 0; i < n; i++) {
        result += tokenToPiece(vocab, elems[i]);
    }

    env->ReleaseIntArrayElements(tokens, elems, JNI_ABORT);
    return env->NewStringUTF(result.c_str());
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
    jfloat repeatPenalty, jlong seed,
    jstring stopToken)
{
    if (nativeHandle == 0) return env->NewStringUTF("");
    auto *session = reinterpret_cast<LlamaSession *>(nativeHandle);

    std::lock_guard<std::mutex> lock(session->mutex);

    const char *promptStr = env->GetStringUTFChars(prompt, nullptr);
    std::string promptText(promptStr);
    env->ReleaseStringUTFChars(prompt, promptStr);

    std::string stopTokenStr;
    if (stopToken != nullptr) {
        const char *stopStr = env->GetStringUTFChars(stopToken, nullptr);
        stopTokenStr = stopStr;
        env->ReleaseStringUTFChars(stopToken, stopStr);
    }

    const llama_vocab *vocab = llama_model_get_vocab(session->model);
    std::vector<llama_token> tokens = tokenizeHelper(vocab, promptText, true);

    llama_memory_seq_rm(llama_get_memory(session->ctx), -1, -1, -1);
    decodePromptInBatches(session->ctx, tokens.data(), static_cast<int>(tokens.size()), session->nBatch);
    llama_batch batch;

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
    int promptTokenCount = static_cast<int>(tokens.size());

    for (int i = 0; i < maxTokens; i++) {
        llama_token newToken = llama_sampler_sample(smpl, session->ctx, -1);

        if (llama_vocab_is_eog(vocab, newToken)) break;

        std::string piece = tokenToPiece(vocab, newToken);
        result += piece;

        if (!stopTokenStr.empty() && result.find(stopTokenStr) != std::string::npos) {
            size_t pos = result.find(stopTokenStr);
            result = result.substr(0, pos);
            break;
        }

        batch = llama_batch_get_one(&newToken, 1);
        if (llama_decode(session->ctx, batch) != 0) break;
        session->lastTokens.push_back(newToken);
    }

    session->lastPromptTokens = promptTokenCount;
    session->lastCompletionTokens = static_cast<int>(session->lastTokens.size()) - promptTokenCount;

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
    jstring stopToken,
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

    std::string stopTokenStr;
    if (stopToken != nullptr) {
        const char *stopStr = env->GetStringUTFChars(stopToken, nullptr);
        stopTokenStr = stopStr;
        env->ReleaseStringUTFChars(stopToken, stopStr);
    }

    const llama_vocab *vocab = llama_model_get_vocab(session->model);
    std::vector<llama_token> tokens = tokenizeHelper(vocab, promptText, true);
    llama_memory_seq_rm(llama_get_memory(session->ctx), -1, -1, -1);
    decodePromptInBatches(session->ctx, tokens.data(), static_cast<int>(tokens.size()), session->nBatch);
    llama_batch batch;

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
    int promptTokenCount = static_cast<int>(tokens.size());

    for (int i = 0; i < maxTokens; i++) {
        llama_token newToken = llama_sampler_sample(smpl, session->ctx, -1);
        if (llama_vocab_is_eog(vocab, newToken)) break;

        std::string piece = tokenToPiece(vocab, newToken);
        result += piece;

        if (!stopTokenStr.empty() && result.find(stopTokenStr) != std::string::npos) {
            break;
        }

        jstring jPiece = env->NewStringUTF(piece.c_str());
        env->CallVoidMethod(callback, onTokenMethod, jPiece);
        env->DeleteLocalRef(jPiece);

        batch = llama_batch_get_one(&newToken, 1);
        if (llama_decode(session->ctx, batch) != 0) break;
        session->lastTokens.push_back(newToken);
    }

    session->lastPromptTokens = promptTokenCount;
    session->lastCompletionTokens = static_cast<int>(session->lastTokens.size()) - promptTokenCount;

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

/* ──────────────────────────────────────────────────────────────
 *  聊天模板渲染 — 使用 llama.cpp 内置模板引擎
 *  ────────────────────────────────────────────────────────────── */

JNIEXPORT jstring JNICALL
Java_com_llama4j_native_1_LlamaContext_applyChatTemplate(
    JNIEnv *env, jclass clazz,
    jlong nativeHandle, jobjectArray roles, jobjectArray contents, jboolean addAssistant)
{
    if (nativeHandle == 0) return env->NewStringUTF("");
    auto *session = reinterpret_cast<LlamaSession *>(nativeHandle);

    jsize nMsg = env->GetArrayLength(roles);
    std::vector<llama_chat_message> messages(nMsg);

    for (jsize i = 0; i < nMsg; i++) {
        auto roleStr = (jstring) env->GetObjectArrayElement(roles, i);
        auto contentStr = (jstring) env->GetObjectArrayElement(contents, i);
        messages[i].role = env->GetStringUTFChars(roleStr, nullptr);
        messages[i].content = env->GetStringUTFChars(contentStr, nullptr);
        env->DeleteLocalRef(roleStr);
        env->DeleteLocalRef(contentStr);
    }

    const char *tmpl = session->chatTemplate.empty() ? nullptr : session->chatTemplate.c_str();

    int len = llama_chat_apply_template(tmpl, messages.data(), nMsg, addAssistant, nullptr, 0);
    std::vector<char> buf(len + 1);
    llama_chat_apply_template(tmpl, messages.data(), nMsg, addAssistant, buf.data(), buf.size());

    for (jsize i = 0; i < nMsg; i++) {
        auto roleStr = (jstring) env->GetObjectArrayElement(roles, i);
        auto contentStr = (jstring) env->GetObjectArrayElement(contents, i);
        env->ReleaseStringUTFChars(roleStr, messages[i].role);
        env->ReleaseStringUTFChars(contentStr, messages[i].content);
        env->DeleteLocalRef(roleStr);
        env->DeleteLocalRef(contentStr);
    }

    return env->NewStringUTF(buf.data());
}

/* ──────────────────────────────────────────────────────────────
 *  Grammar 约束生成
 *  ────────────────────────────────────────────────────────────── */

JNIEXPORT jlong JNICALL
Java_com_llama4j_native_1_LlamaContext_createGrammarSampler(
    JNIEnv *env, jclass clazz, jlong nativeHandle, jstring grammarStr, jstring grammarRoot)
{
    if (nativeHandle == 0 || !grammarStr) return 0;
    auto *session = reinterpret_cast<LlamaSession *>(nativeHandle);

    const char *gStr = env->GetStringUTFChars(grammarStr, nullptr);
    const char *gRoot = grammarRoot ? env->GetStringUTFChars(grammarRoot, nullptr) : "root";

    const llama_vocab *vocab = llama_model_get_vocab(session->model);
    auto *sampler = llama_sampler_init_grammar(vocab, gStr, gRoot);

    env->ReleaseStringUTFChars(grammarStr, gStr);
    if (grammarRoot) env->ReleaseStringUTFChars(grammarRoot, gRoot);

    return reinterpret_cast<jlong>(sampler);
}

JNIEXPORT void JNICALL
Java_com_llama4j_native_1_LlamaContext_freeGrammarSampler(
    JNIEnv *env, jclass clazz, jlong samplerHandle)
{
    if (samplerHandle == 0) return;
    llama_sampler_free(reinterpret_cast<llama_sampler *>(samplerHandle));
}

/* ──────────────────────────────────────────────────────────────
 *  带 Grammar 约束的推理生成
 *  ────────────────────────────────────────────────────────────── */

JNIEXPORT jstring JNICALL
Java_com_llama4j_native_1_LlamaContext_generateWithGrammar(
    JNIEnv *env, jclass clazz,
    jlong nativeHandle, jstring prompt,
    jint maxTokens, jfloat temperature,
    jint topK, jfloat topP,
    jfloat repeatPenalty, jlong seed,
    jstring stopToken, jlong grammarSamplerHandle)
{
    if (nativeHandle == 0) return env->NewStringUTF("");
    auto *session = reinterpret_cast<LlamaSession *>(nativeHandle);

    std::lock_guard<std::mutex> lock(session->mutex);

    const char *promptStr = env->GetStringUTFChars(prompt, nullptr);
    std::string promptText(promptStr);
    env->ReleaseStringUTFChars(prompt, promptStr);

    std::string stopTokenStr;
    if (stopToken != nullptr) {
        const char *stopStr = env->GetStringUTFChars(stopToken, nullptr);
        stopTokenStr = stopStr;
        env->ReleaseStringUTFChars(stopToken, stopStr);
    }

    const llama_vocab *vocab = llama_model_get_vocab(session->model);
    std::vector<llama_token> tokens = tokenizeHelper(vocab, promptText, true);

    llama_memory_seq_rm(llama_get_memory(session->ctx), -1, -1, -1);
    decodePromptInBatches(session->ctx, tokens.data(), static_cast<int>(tokens.size()), session->nBatch);
    llama_batch batch;

    auto *smpl = llama_sampler_chain_init(llama_sampler_chain_default_params());
    llama_sampler_chain_add(smpl, llama_sampler_init_penalties(
        64, repeatPenalty, 0.0f, 0.0f));
    if (temperature > 0.0f) {
        llama_sampler_chain_add(smpl, llama_sampler_init_temp(temperature));
        llama_sampler_chain_add(smpl, llama_sampler_init_top_k(topK));
        llama_sampler_chain_add(smpl, llama_sampler_init_top_p(topP, 1));
    }

    // 插入 grammar sampler（在 distribution 之前）
    if (grammarSamplerHandle != 0) {
        llama_sampler_chain_add(smpl,
            reinterpret_cast<llama_sampler *>(grammarSamplerHandle));
    }

    if (temperature > 0.0f) {
        llama_sampler_chain_add(smpl, llama_sampler_init_dist(seed >= 0 ? seed : (uint32_t)time(nullptr)));
    } else {
        llama_sampler_chain_add(smpl, llama_sampler_init_greedy());
    }

    std::string result;
    session->lastTokens = tokens;
    int promptTokenCount = static_cast<int>(tokens.size());

    for (int i = 0; i < maxTokens; i++) {
        llama_token newToken = llama_sampler_sample(smpl, session->ctx, -1);

        if (llama_vocab_is_eog(vocab, newToken)) break;

        std::string piece = tokenToPiece(vocab, newToken);
        result += piece;

        if (!stopTokenStr.empty() && result.find(stopTokenStr) != std::string::npos) {
            size_t pos = result.find(stopTokenStr);
            result = result.substr(0, pos);
            break;
        }

        batch = llama_batch_get_one(&newToken, 1);
        if (llama_decode(session->ctx, batch) != 0) break;
        session->lastTokens.push_back(newToken);
    }

    session->lastPromptTokens = promptTokenCount;
    session->lastCompletionTokens = static_cast<int>(session->lastTokens.size()) - promptTokenCount;

    llama_sampler_free(smpl);
    return env->NewStringUTF(result.c_str());
}

JNIEXPORT void JNICALL
Java_com_llama4j_native_1_LlamaContext_generateStreamWithGrammar(
    JNIEnv *env, jclass clazz,
    jlong nativeHandle, jstring prompt,
    jint maxTokens, jfloat temperature,
    jint topK, jfloat topP,
    jfloat repeatPenalty, jlong seed,
    jstring stopToken, jlong grammarSamplerHandle,
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

    std::string stopTokenStr;
    if (stopToken != nullptr) {
        const char *stopStr = env->GetStringUTFChars(stopToken, nullptr);
        stopTokenStr = stopStr;
        env->ReleaseStringUTFChars(stopToken, stopStr);
    }

    const llama_vocab *vocab = llama_model_get_vocab(session->model);
    std::vector<llama_token> tokens = tokenizeHelper(vocab, promptText, true);
    llama_memory_seq_rm(llama_get_memory(session->ctx), -1, -1, -1);
    decodePromptInBatches(session->ctx, tokens.data(), static_cast<int>(tokens.size()), session->nBatch);
    llama_batch batch;

    auto *smpl = llama_sampler_chain_init(llama_sampler_chain_default_params());
    llama_sampler_chain_add(smpl, llama_sampler_init_penalties(
        64, repeatPenalty, 0.0f, 0.0f));
    if (temperature > 0.0f) {
        llama_sampler_chain_add(smpl, llama_sampler_init_temp(temperature));
        llama_sampler_chain_add(smpl, llama_sampler_init_top_k(topK));
        llama_sampler_chain_add(smpl, llama_sampler_init_top_p(topP, 1));
    }

    if (grammarSamplerHandle != 0) {
        llama_sampler_chain_add(smpl,
            reinterpret_cast<llama_sampler *>(grammarSamplerHandle));
    }

    if (temperature > 0.0f) {
        llama_sampler_chain_add(smpl, llama_sampler_init_dist(seed >= 0 ? seed : (uint32_t)time(nullptr)));
    } else {
        llama_sampler_chain_add(smpl, llama_sampler_init_greedy());
    }

    std::string result;
    session->lastTokens = tokens;
    int promptTokenCount = static_cast<int>(tokens.size());

    for (int i = 0; i < maxTokens; i++) {
        llama_token newToken = llama_sampler_sample(smpl, session->ctx, -1);
        if (llama_vocab_is_eog(vocab, newToken)) break;

        std::string piece = tokenToPiece(vocab, newToken);
        result += piece;

        if (!stopTokenStr.empty() && result.find(stopTokenStr) != std::string::npos) {
            break;
        }

        jstring jPiece = env->NewStringUTF(piece.c_str());
        env->CallVoidMethod(callback, onTokenMethod, jPiece);
        env->DeleteLocalRef(jPiece);

        batch = llama_batch_get_one(&newToken, 1);
        if (llama_decode(session->ctx, batch) != 0) break;
        session->lastTokens.push_back(newToken);
    }

    session->lastPromptTokens = promptTokenCount;
    session->lastCompletionTokens = static_cast<int>(session->lastTokens.size()) - promptTokenCount;

    llama_sampler_free(smpl);
}

/* ──────────────────────────────────────────────────────────────
 *  生成统计查询
 *  ────────────────────────────────────────────────────────────── */

JNIEXPORT jintArray JNICALL
Java_com_llama4j_native_1_LlamaContext_getGenerateStats(
    JNIEnv *env, jclass clazz, jlong nativeHandle)
{
    jintArray result = env->NewIntArray(2);
    if (nativeHandle == 0) return result;
    auto *session = reinterpret_cast<LlamaSession *>(nativeHandle);

    jint stats[2] = {
        session->lastPromptTokens,
        session->lastCompletionTokens
    };
    env->SetIntArrayRegion(result, 0, 2, stats);
    return result;
}

/* ──────────────────────────────────────────────────────────────
 *  Embeddings 嵌入向量
 *  ────────────────────────────────────────────────────────────── */

JNIEXPORT jfloatArray JNICALL
Java_com_llama4j_native_1_LlamaContext_embed(
    JNIEnv *env, jclass clazz, jlong nativeHandle, jstring text)
{
    if (nativeHandle == 0 || !text) return env->NewFloatArray(0);
    auto *session = reinterpret_cast<LlamaSession *>(nativeHandle);

    const char *textStr = env->GetStringUTFChars(text, nullptr);
    std::string input(textStr);
    env->ReleaseStringUTFChars(text, textStr);

    llama_memory_seq_rm(llama_get_memory(session->ctx), -1, -1, -1);

    const llama_vocab *vocab = llama_model_get_vocab(session->model);
    std::vector<llama_token> tokens = tokenizeHelper(vocab, input, true);

    encodeInBatches(session->ctx, tokens.data(), static_cast<int>(tokens.size()), session->nBatch);

    const float *embeddings = llama_get_embeddings(session->ctx);
    if (!embeddings) return env->NewFloatArray(0);

    int nEmbd = llama_model_n_embd(session->model);
    jfloatArray result = env->NewFloatArray(nEmbd);
    env->SetFloatArrayRegion(result, 0, nEmbd, embeddings);
    return result;
}

/* ──────────────────────────────────────────────────────────────
 *  多模态（Vision-Language Model）支持
 *  ────────────────────────────────────────────────────────────── */

JNIEXPORT void JNICALL
Java_com_llama4j_native_1_LlamaContext_initMultimodal(
    JNIEnv *env, jclass clazz,
    jlong nativeHandle, jstring mmprojPath)
{
    if (nativeHandle == 0) {
        throwJavaException(env, "java/lang/IllegalArgumentException", "nativeHandle 为 0");
        return;
    }
    auto *session = reinterpret_cast<LlamaSession *>(nativeHandle);

    if (session->mtmdCtx) {
        mtmd_free(session->mtmdCtx);
        session->mtmdCtx = nullptr;
    }

    const char *pathStr = env->GetStringUTFChars(mmprojPath, nullptr);
    if (!pathStr) {
        throwJavaException(env, "java/lang/IllegalArgumentException", "mmproj 路径不能为空");
        return;
    }
    std::string path(pathStr);
    env->ReleaseStringUTFChars(mmprojPath, pathStr);

    auto mparams = mtmd_context_params_default();
    mparams.use_gpu = true;
    mparams.n_threads = 4;

    mtmd_context *mctx = mtmd_init_from_file(path.c_str(), session->model, mparams);
    if (!mctx) {
        throwJavaException(env, "java/io/IOException",
            ("无法初始化多模态上下文: " + path).c_str());
        return;
    }
    session->mtmdCtx = mctx;
}

JNIEXPORT jboolean JNICALL
Java_com_llama4j_native_1_LlamaContext_isMultimodalEnabled(
    JNIEnv *env, jclass clazz, jlong nativeHandle)
{
    if (nativeHandle == 0) return JNI_FALSE;
    auto *session = reinterpret_cast<LlamaSession *>(nativeHandle);
    return session->mtmdCtx != nullptr ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT void JNICALL
Java_com_llama4j_native_1_LlamaContext_freeMultimodal(
    JNIEnv *env, jclass clazz, jlong nativeHandle)
{
    if (nativeHandle == 0) return;
    auto *session = reinterpret_cast<LlamaSession *>(nativeHandle);
    if (session->mtmdCtx) {
        mtmd_free(session->mtmdCtx);
        session->mtmdCtx = nullptr;
    }
}

/**
 * 多模态同步生成。
 *
 * 流程：
 *   1. 从 byte[] 创建 mtmd_bitmap
 *   2. 使用 mtmd_tokenize 将 prompt（含 <__media__> 标记）与图片交错编码
 *   3. mtmd_helper_eval_chunks 将所有 chunks（文本 + 图片）送入 KV cache
 *   4. 标准 autoregressive sampling loop 生成文本
 */
JNIEXPORT jstring JNICALL
Java_com_llama4j_native_1_LlamaContext_generateMultimodal(
    JNIEnv *env, jclass clazz,
    jlong nativeHandle, jstring prompt,
    jobjectArray imageDataArray,
    jint maxTokens, jfloat temperature,
    jint topK, jfloat topP,
    jfloat repeatPenalty, jlong seed,
    jstring stopToken)
{
    if (nativeHandle == 0) return env->NewStringUTF("");
    auto *session = reinterpret_cast<LlamaSession *>(nativeHandle);

    std::lock_guard<std::mutex> lock(session->mutex);

    if (!session->mtmdCtx) {
        throwJavaException(env, "java/lang/IllegalStateException", "多模态未初始化，请先调用 initMultimodal");
        return env->NewStringUTF("");
    }

    const char *promptStr = env->GetStringUTFChars(prompt, nullptr);
    std::string promptText(promptStr);
    env->ReleaseStringUTFChars(prompt, promptStr);

    std::string stopTokenStr;
    if (stopToken != nullptr) {
        const char *stopStr = env->GetStringUTFChars(stopToken, nullptr);
        stopTokenStr = stopStr;
        env->ReleaseStringUTFChars(stopToken, stopStr);
    }

    // 加载所有图片为 mtmd_bitmap
    jsize nImages = imageDataArray ? env->GetArrayLength(imageDataArray) : 0;
    std::vector<mtmd_bitmap *> bitmaps(nImages, nullptr);
    for (jsize i = 0; i < nImages; i++) {
        jbyteArray imgBytes = (jbyteArray) env->GetObjectArrayElement(imageDataArray, i);
        jsize dataLen = env->GetArrayLength(imgBytes);
        jbyte *data = env->GetByteArrayElements(imgBytes, nullptr);
        bitmaps[i] = mtmd_helper_bitmap_init_from_buf(session->mtmdCtx,
            reinterpret_cast<const unsigned char *>(data), dataLen);
        env->ReleaseByteArrayElements(imgBytes, data, JNI_ABORT);
        env->DeleteLocalRef(imgBytes);
        if (!bitmaps[i]) {
            for (auto *b : bitmaps) { if (b) mtmd_bitmap_free(b); }
            throwJavaException(env, "java/io/IOException",
                ("图片解码失败，第 " + std::to_string(i + 1) + " 张图片数据无效").c_str());
            return env->NewStringUTF("");
        }
    }

    // tokenize: 将 prompt 中的 <__media__> 标记与图片交错
    std::vector<const mtmd_bitmap *> bitmapPtrs(nImages);
    for (jsize i = 0; i < nImages; i++) bitmapPtrs[i] = bitmaps[i];

    mtmd_input_text inputText;
    inputText.text = promptText.c_str();
    inputText.add_special = true;
    inputText.parse_special = true;

    mtmd_input_chunks *chunks = mtmd_input_chunks_init();
    int32_t tokenizeRes = mtmd_tokenize(session->mtmdCtx, chunks, &inputText,
        bitmapPtrs.data(), nImages);

    if (tokenizeRes != 0) {
        mtmd_input_chunks_free(chunks);
        for (auto *b : bitmaps) { if (b) mtmd_bitmap_free(b); }
        throwJavaException(env, "java/io/IOException",
            ("多模态 tokenize 失败，错误码: " + std::to_string(tokenizeRes)).c_str());
        return env->NewStringUTF("");
    }

    // 清空 KV cache 并评估所有 chunks
    llama_memory_seq_rm(llama_get_memory(session->ctx), -1, -1, -1);

    llama_pos nPast = 0;
    int32_t evalRes = mtmd_helper_eval_chunks(session->mtmdCtx, session->ctx,
        chunks, nPast, 0, session->nBatch, true, &nPast);

    // 释放 chunks 和 bitmaps
    mtmd_input_chunks_free(chunks);
    for (auto *b : bitmaps) { if (b) mtmd_bitmap_free(b); }

    if (evalRes != 0) {
        throwJavaException(env, "java/io/IOException",
            ("多模态 eval_chunks 失败，错误码: " + std::to_string(evalRes)).c_str());
        return env->NewStringUTF("");
    }

    // autoregressive sampling loop（与 generate 相同）
    const llama_vocab *vocab = llama_model_get_vocab(session->model);
    llama_batch batch;

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
    int promptTokenCount = static_cast<int>(nPast);
    session->lastTokens.clear();

    for (int i = 0; i < maxTokens; i++) {
        llama_token newToken = llama_sampler_sample(smpl, session->ctx, -1);

        if (llama_vocab_is_eog(vocab, newToken)) break;

        std::string piece = tokenToPiece(vocab, newToken);
        result += piece;

        if (!stopTokenStr.empty() && result.find(stopTokenStr) != std::string::npos) {
            size_t pos = result.find(stopTokenStr);
            result = result.substr(0, pos);
            break;
        }

        // 手动构建 batch，设置正确的 position（从 nPast 继续递增）
        llama_pos curPos = nPast + static_cast<llama_pos>(i);
        batch.n_tokens = 1;
        batch.token = &newToken;
        batch.embd = nullptr;
        batch.pos = &curPos;
        batch.n_seq_id = nullptr;
        batch.seq_id = nullptr;
        batch.logits = nullptr;
        if (llama_decode(session->ctx, batch) != 0) break;
        session->lastTokens.push_back(newToken);
    }

    session->lastPromptTokens = promptTokenCount;
    session->lastCompletionTokens = static_cast<int>(session->lastTokens.size());

    llama_sampler_free(smpl);
    return env->NewStringUTF(result.c_str());
}

/**
 * 多模态流式生成。
 */
JNIEXPORT void JNICALL
Java_com_llama4j_native_1_LlamaContext_generateMultimodalStream(
    JNIEnv *env, jclass clazz,
    jlong nativeHandle, jstring prompt,
    jobjectArray imageDataArray,
    jint maxTokens, jfloat temperature,
    jint topK, jfloat topP,
    jfloat repeatPenalty, jlong seed,
    jstring stopToken, jobject callback)
{
    if (nativeHandle == 0 || !callback) return;
    auto *session = reinterpret_cast<LlamaSession *>(nativeHandle);

    std::lock_guard<std::mutex> lock(session->mutex);

    if (!session->mtmdCtx) {
        throwJavaException(env, "java/lang/IllegalStateException", "多模态未初始化，请先调用 initMultimodal");
        return;
    }

    jclass callbackClass = env->GetObjectClass(callback);
    jmethodID onTokenMethod = env->GetMethodID(callbackClass, "onToken", "(Ljava/lang/String;)V");

    const char *promptStr = env->GetStringUTFChars(prompt, nullptr);
    std::string promptText(promptStr);
    env->ReleaseStringUTFChars(prompt, promptStr);

    std::string stopTokenStr;
    if (stopToken != nullptr) {
        const char *stopStr = env->GetStringUTFChars(stopToken, nullptr);
        stopTokenStr = stopStr;
        env->ReleaseStringUTFChars(stopToken, stopStr);
    }

    // 加载所有图片为 mtmd_bitmap
    jsize nImages = imageDataArray ? env->GetArrayLength(imageDataArray) : 0;
    std::vector<mtmd_bitmap *> bitmaps(nImages, nullptr);
    for (jsize i = 0; i < nImages; i++) {
        jbyteArray imgBytes = (jbyteArray) env->GetObjectArrayElement(imageDataArray, i);
        jsize dataLen = env->GetArrayLength(imgBytes);
        jbyte *data = env->GetByteArrayElements(imgBytes, nullptr);
        bitmaps[i] = mtmd_helper_bitmap_init_from_buf(session->mtmdCtx,
            reinterpret_cast<const unsigned char *>(data), dataLen);
        env->ReleaseByteArrayElements(imgBytes, data, JNI_ABORT);
        env->DeleteLocalRef(imgBytes);
        if (!bitmaps[i]) {
            for (auto *b : bitmaps) { if (b) mtmd_bitmap_free(b); }
            throwJavaException(env, "java/io/IOException",
                ("图片解码失败，第 " + std::to_string(i + 1) + " 张图片数据无效").c_str());
            return;
        }
    }

    // tokenize
    std::vector<const mtmd_bitmap *> bitmapPtrs(nImages);
    for (jsize i = 0; i < nImages; i++) bitmapPtrs[i] = bitmaps[i];

    mtmd_input_text inputText;
    inputText.text = promptText.c_str();
    inputText.add_special = true;
    inputText.parse_special = true;

    mtmd_input_chunks *chunks = mtmd_input_chunks_init();
    int32_t tokenizeRes = mtmd_tokenize(session->mtmdCtx, chunks, &inputText,
        bitmapPtrs.data(), nImages);

    if (tokenizeRes != 0) {
        mtmd_input_chunks_free(chunks);
        for (auto *b : bitmaps) { if (b) mtmd_bitmap_free(b); }
        throwJavaException(env, "java/io/IOException",
            ("多模态 tokenize 失败，错误码: " + std::to_string(tokenizeRes)).c_str());
        return;
    }

    // 清空 KV cache 并评估所有 chunks
    llama_memory_seq_rm(llama_get_memory(session->ctx), -1, -1, -1);

    llama_pos nPast = 0;
    int32_t evalRes = mtmd_helper_eval_chunks(session->mtmdCtx, session->ctx,
        chunks, nPast, 0, session->nBatch, true, &nPast);

    mtmd_input_chunks_free(chunks);
    for (auto *b : bitmaps) { if (b) mtmd_bitmap_free(b); }

    if (evalRes != 0) {
        throwJavaException(env, "java/io/IOException",
            ("多模态 eval_chunks 失败，错误码: " + std::to_string(evalRes)).c_str());
        return;
    }

    // autoregressive sampling loop（流式版本）
    const llama_vocab *vocab = llama_model_get_vocab(session->model);
    llama_batch batch;

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
    int promptTokenCount = static_cast<int>(nPast);
    session->lastTokens.clear();

    for (int i = 0; i < maxTokens; i++) {
        llama_token newToken = llama_sampler_sample(smpl, session->ctx, -1);
        if (llama_vocab_is_eog(vocab, newToken)) break;

        std::string piece = tokenToPiece(vocab, newToken);
        result += piece;

        if (!stopTokenStr.empty() && result.find(stopTokenStr) != std::string::npos) {
            break;
        }

        jstring jPiece = env->NewStringUTF(piece.c_str());
        env->CallVoidMethod(callback, onTokenMethod, jPiece);
        env->DeleteLocalRef(jPiece);

        // 手动构建 batch，设置正确的 position（从 nPast 继续递增）
        llama_pos curPos = nPast + static_cast<llama_pos>(i);
        batch.n_tokens = 1;
        batch.token = &newToken;
        batch.embd = nullptr;
        batch.pos = &curPos;
        batch.n_seq_id = nullptr;
        batch.seq_id = nullptr;
        batch.logits = nullptr;
        if (llama_decode(session->ctx, batch) != 0) break;
        session->lastTokens.push_back(newToken);
    }

    session->lastPromptTokens = promptTokenCount;
    session->lastCompletionTokens = static_cast<int>(session->lastTokens.size());

    llama_sampler_free(smpl);
}

/* ──────────────────────────────────────────────────────────────
 *  模型元数据查询
 *  ────────────────────────────────────────────────────────────── */

JNIEXPORT jstring JNICALL
Java_com_llama4j_native_1_LlamaContext_getModelDesc(
    JNIEnv *env, jclass clazz, jlong nativeHandle)
{
    if (nativeHandle == 0) return env->NewStringUTF("");
    auto *session = reinterpret_cast<LlamaSession *>(nativeHandle);
    char buf[256];
    llama_model_desc(session->model, buf, sizeof(buf));
    return env->NewStringUTF(buf);
}

JNIEXPORT jlong JNICALL
Java_com_llama4j_native_1_LlamaContext_getModelSize(
    JNIEnv *env, jclass clazz, jlong nativeHandle)
{
    if (nativeHandle == 0) return 0;
    auto *session = reinterpret_cast<LlamaSession *>(nativeHandle);
    return static_cast<jlong>(llama_model_size(session->model));
}

JNIEXPORT jlong JNICALL
Java_com_llama4j_native_1_LlamaContext_getModelNParams(
    JNIEnv *env, jclass clazz, jlong nativeHandle)
{
    if (nativeHandle == 0) return 0;
    auto *session = reinterpret_cast<LlamaSession *>(nativeHandle);
    return static_cast<jlong>(llama_model_n_params(session->model));
}
