/**
 * llama4j — Java bindings for llama.cpp
 *
 * Copyright (c) 2024 llama4j contributors
 * SPDX-License-Identifier: MIT
 *
 * JNI 头文件 — llama.cpp 原生桥接层
 * ====================================
 * 本文件声明了 Java 代码通过 JNI 调用 llama.cpp C 库的所有原生方法。
 * Java 端的 LlamaContext 类通过这些方法与底层 C 引擎交互，
 * 实现模型加载、推理、分词、KV 缓存管理等核心功能。
 *
 * 架构说明：
 *   Java 层 (LlamaContext.java)
 *       ↕ JNI 调用
 *   本文件声明的 C 函数
 *       ↕ 内部调用
 *   llama.cpp C API (llama.h / ggml.h)
 *
 * 线程安全：
 *   每个 LlamaSession 实例内部包含一个 std::mutex，
 *   确保同一上下文的并发生成请求被正确序列化。
 */
#ifndef LLAMA4J_H
#define LLAMA4J_H

#include <jni.h>
#include <string>
#include <vector>

#ifdef __cplusplus
extern "C" {
#endif

/* ──────────────────────────────────────────────────────────────
 *  模型生命周期管理
 *  ──────────────────────────────────────────────────────────────
 *  这组函数负责模型的加载与释放。模型加载时会分配 GPU/CPU 内存，
 *  因此必须在使用完毕后调用 freeModel 释放资源，避免内存泄漏。
 */

/**
 * 加载 GGUF 模型并创建推理上下文。
 *
 * 工作流程：
 *   1. 将 Java String 转换为 C 字符串路径
 *   2. 调用 llama_model_load_from_file 加载模型权重
 *   3. 调用 llama_init_from_model 创建推理上下文
 *   4. 创建 LlamaSession 结构体封装所有状态
 *   5. 从模型元数据中提取 chat template
 *
 * @param modelPath   GGUF 模型文件的绝对路径
 * @param nCtx        上下文窗口大小（token 数量），决定模型能处理的最大序列长度
 * @param nGpuLayers  卸载到 GPU 的层数，-1 表示全部卸载
 * @param nThreads    CPU 推理线程数，通常设为物理核心数
 * @return            LlamaSession 的不透明指针（0 表示加载失败）
 */
JNIEXPORT jlong JNICALL
Java_com_llama4j_native_1_LlamaContext_loadModel(
    JNIEnv *env, jclass clazz,
    jstring modelPath,
    jint nCtx,
    jint nGpuLayers,
    jint nThreads);

/**
 * 释放已加载的模型及其推理上下文。
 *
 * 必须在模型不再使用时调用，否则会导致 GPU/CPU 内存泄漏。
 * 此函数是幂等的——对已释放的 handle 调用不会产生副作用。
 *
 * @param nativeHandle  loadModel 返回的不透明指针
 */
JNIEXPORT void JNICALL
Java_com_llama4j_native_1_LlamaContext_freeModel(
    JNIEnv *env, jclass clazz, jlong nativeHandle);

/* ──────────────────────────────────────────────────────────────
 *  分词（Tokenization）
 *  ──────────────────────────────────────────────────────────────
 *  将文本字符串转换为模型词汇表中的 token ID 序列，
 *  或将 token ID 序列还原为文本字符串。
 */

/**
 * 将文本字符串分词为 token ID 数组。
 *
 * 分词是推理的前置步骤——模型只能处理数字 ID，不能直接处理文本。
 * llama.cpp 使用 SentencePiece/BPE 分词器，具体算法取决于模型。
 *
 * @param nativeHandle  模型会话指针
 * @param text          待分词的文本
 * @param addBos        是否在开头添加 BOS（Beginning of Sequence）token
 * @return              token ID 数组
 */
JNIEXPORT jintArray JNICALL
Java_com_llama4j_native_1_LlamaContext_tokenize(
    JNIEnv *env, jclass clazz,
    jlong nativeHandle, jstring text, jboolean addBos);

/**
 * 将单个 token ID 转换为对应的文本片段。
 *
 * 注意：单个 token 可能只对应一个汉字的一部分或半个单词，
 * 因此在流式输出时需要正确拼接连续的 token 片段。
 *
 * @param nativeHandle  模型会话指针
 * @param tokenId       token ID
 * @return              token 对应的文本片段
 */
JNIEXPORT jstring JNICALL
Java_com_llama4j_native_1_LlamaContext_tokenToStr(
    JNIEnv *env, jclass clazz,
    jlong nativeHandle, jint tokenId);

/* ──────────────────────────────────────────────────────────────
 *  推理生成
 *  ──────────────────────────────────────────────────────────────
 *  核心推理函数。generate 完成完整生成后返回结果，
 *  generateStream 通过回调逐 token 流式输出。
 */

/**
 * 执行完整的文本生成（非流式）。
 *
 * 内部流程：
 *   1. 对 prompt 进行分词
 *   2. 将 token 序列送入模型处理（llama_decode）
 *   3. 循环采样生成新 token（llama_sample_* + llama_decode）
 *   4. 遇到 EOS token 或达到 maxTokens 时停止
 *   5. 返回完整的生成文本
 *
 * @param nativeHandle  模型会话指针
 * @param prompt        输入提示词（已经过 chat template 渲染）
 * @param maxTokens     最大生成 token 数
 * @param temperature   采样温度（0 = 贪心解码，越高越随机）
 * @param topK          Top-K 采样：只考虑概率最高的 K 个 token
 * @param topP          Top-P（核采样）：只考虑累积概率达到 P 的 token
 * @param repeatPenalty 重复惩罚系数（>1.0 抑制重复）
 * @param seed          随机种子（-1 = 不确定）
 * @return              生成的完整文本
 */
JNIEXPORT jstring JNICALL
Java_com_llama4j_native_1_LlamaContext_generate(
    JNIEnv *env, jclass clazz,
    jlong nativeHandle, jstring prompt,
    jint maxTokens, jfloat temperature,
    jint topK, jfloat topP,
    jfloat repeatPenalty, jlong seed,
    jstring stopToken);

/**
 * 执行流式文本生成，每生成一个 token 就通过回调通知 Java 层。
 *
 * 与 generate 的区别在于：generateStream 不会等待全部生成完毕，
 * 而是每产生一个 token 就立即调用 Java 端的 TokenCallback.onToken()，
 * 实现逐字输出的实时流式效果，适合聊天界面等交互场景。
 *
 * @param nativeHandle  模型会话指针
 * @param prompt        输入提示词
 * @param maxTokens     最大生成 token 数
 * @param temperature   采样温度
 * @param topK          Top-K 采样参数
 * @param topP          Top-P 采样参数
 * @param repeatPenalty 重复惩罚系数
 * @param seed          随机种子
 * @param callback      Java 端的 TokenCallback 回调对象
 */
JNIEXPORT void JNICALL
Java_com_llama4j_native_1_LlamaContext_generateStream(
    JNIEnv *env, jclass clazz,
    jlong nativeHandle, jstring prompt,
    jint maxTokens, jfloat temperature,
    jint topK, jfloat topP,
    jfloat repeatPenalty, jlong seed,
    jstring stopToken,
    jobject callback);

/* ──────────────────────────────────────────────────────────────
 *  KV 缓存管理
 *  ──────────────────────────────────────────────────────────────
 *  KV 缓存（Key-Value Cache）是 Transformer 推理的核心优化：
 *  已处理的 token 的 Key/Value 向量会被缓存，避免重复计算。
 *  保存/恢复 KV 缓存可以实现会话的断点续聊。
 */

/**
 * 保存当前 KV 缓存状态为字节数组。
 *
 * 保存的数据包含所有已处理 token 的 Key/Value 向量，
 * 可用于后续恢复会话，避免重新处理历史 prompt。
 *
 * @param nativeHandle  模型会话指针
 * @return              序列化的 KV 缓存字节数组
 */
JNIEXPORT jbyteArray JNICALL
Java_com_llama4j_native_1_LlamaContext_saveSession(
    JNIEnv *env, jclass clazz, jlong nativeHandle);

/**
 * 从字节数组恢复 KV 缓存状态。
 *
 * 恢复后，模型可以从上次的断点继续生成，
 * 而不需要重新处理整个对话历史，大幅降低延迟。
 *
 * @param nativeHandle  模型会话指针
 * @param sessionData   saveSession 返回的序列化数据
 */
JNIEXPORT void JNICALL
Java_com_llama4j_native_1_LlamaContext_loadSession(
    JNIEnv *env, jclass clazz,
    jlong nativeHandle, jbyteArray sessionData);

/* ──────────────────────────────────────────────────────────────
 *  模型元数据查询
 *  ──────────────────────────────────────────────────────────────
 *  这些函数用于查询已加载模型的元信息，如词汇表大小、
 *  上下文长度、内嵌的 chat template 等。
 */

/**
 * 获取模型元数据中内嵌的 chat template 字符串。
 *
 * 许多 GGUF 模型在元数据中包含了 Jinja2 格式的对话模板，
 * 定义了如何将多轮对话格式化为模型可理解的输入。
 * 例如 Llama 3 的模板包含 <|start_header_id|> 等特殊标记。
 *
 * @param nativeHandle  模型会话指针
 * @return              chat template 字符串（可能为空）
 */
JNIEXPORT jstring JNICALL
Java_com_llama4j_native_1_LlamaContext_getChatTemplate(
    JNIEnv *env, jclass clazz, jlong nativeHandle);

/**
 * 获取模型词汇表大小。
 *
 * 词汇表大小决定了模型能识别的 token 数量，
 * 例如 Qwen2.5 的词汇表大小为 151,936。
 *
 * @param nativeHandle  模型会话指针
 * @return              词汇表大小
 */
JNIEXPORT jint JNICALL
Java_com_llama4j_native_1_LlamaContext_getVocabSize(
    JNIEnv *env, jclass clazz, jlong nativeHandle);

/**
 * 获取已加载模型的上下文窗口大小（n_ctx）。
 *
 * 上下文窗口大小决定了模型一次能处理的最大 token 数量，
 * 超出此长度的输入会被截断或触发滑动窗口。
 *
 * @param nativeHandle  模型会话指针
 * @return              上下文大小（token 数）
 */
JNIEXPORT jint JNICALL
Java_com_llama4j_native_1_LlamaContext_getContextSize(
    JNIEnv *env, jclass clazz, jlong nativeHandle);

/**
 * 获取 KV 缓存中当前存储的 token 数量。
 *
 * 此值可用于监控上下文使用率，当接近 n_ctx 时
 * 应考虑清理历史或开启新的会话。
 *
 * @param nativeHandle  模型会话指针
 * @return              缓存的 token 数量
 */
JNIEXPORT jint JNICALL
Java_com_llama4j_native_1_LlamaContext_getKvCacheTokenCount(
    JNIEnv *env, jclass clazz, jlong nativeHandle);

/* ──────────────────────────────────────────────────────────────
 *  聊天模板渲染
 *  ────────────────────────────────────────────────────────────── */

JNIEXPORT jstring JNICALL
Java_com_llama4j_native_1_LlamaContext_applyChatTemplate(
    JNIEnv *env, jclass clazz,
    jlong nativeHandle, jobjectArray roles, jobjectArray contents, jboolean addAssistant);

/* ──────────────────────────────────────────────────────────────
 *  Grammar 约束生成
 *  ────────────────────────────────────────────────────────────── */

JNIEXPORT jlong JNICALL
Java_com_llama4j_native_1_LlamaContext_createGrammarSampler(
    JNIEnv *env, jclass clazz, jlong nativeHandle, jstring grammarStr, jstring grammarRoot);

JNIEXPORT void JNICALL
Java_com_llama4j_native_1_LlamaContext_freeGrammarSampler(
    JNIEnv *env, jclass clazz, jlong samplerHandle);

/**
 * 带 Grammar 约束的同步生成。
 *
 * 与 generate 相同，但在采样链中插入 grammar sampler，
 * 确保输出符合指定语法。
 *
 * @param grammarSamplerHandle  grammar sampler 的不透明指针（0 = 不使用约束）
 */
JNIEXPORT jstring JNICALL
Java_com_llama4j_native_1_LlamaContext_generateWithGrammar(
    JNIEnv *env, jclass clazz,
    jlong nativeHandle, jstring prompt,
    jint maxTokens, jfloat temperature,
    jint topK, jfloat topP,
    jfloat repeatPenalty, jlong seed,
    jstring stopToken, jlong grammarSamplerHandle);

/**
 * 带 Grammar 约束的流式生成。
 *
 * @param grammarSamplerHandle  grammar sampler 的不透明指针（0 = 不使用约束）
 */
JNIEXPORT void JNICALL
Java_com_llama4j_native_1_LlamaContext_generateStreamWithGrammar(
    JNIEnv *env, jclass clazz,
    jlong nativeHandle, jstring prompt,
    jint maxTokens, jfloat temperature,
    jint topK, jfloat topP,
    jfloat repeatPenalty, jlong seed,
    jstring stopToken, jlong grammarSamplerHandle,
    jobject callback);

/* ──────────────────────────────────────────────────────────────
 *  Embeddings 嵌入向量
 *  ────────────────────────────────────────────────────────────── */

JNIEXPORT jfloatArray JNICALL
Java_com_llama4j_native_1_LlamaContext_embed(
    JNIEnv *env, jclass clazz, jlong nativeHandle, jstring text);

/* ──────────────────────────────────────────────────────────────
 *  模型元数据扩展查询
 *  ────────────────────────────────────────────────────────────── */

JNIEXPORT jstring JNICALL
Java_com_llama4j_native_1_LlamaContext_getModelDesc(
    JNIEnv *env, jclass clazz, jlong nativeHandle);

JNIEXPORT jlong JNICALL
Java_com_llama4j_native_1_LlamaContext_getModelSize(
    JNIEnv *env, jclass clazz, jlong nativeHandle);

JNIEXPORT jlong JNICALL
Java_com_llama4j_native_1_LlamaContext_getModelNParams(
    JNIEnv *env, jclass clazz, jlong nativeHandle);

#ifdef __cplusplus
}
#endif

#endif /* LLAMA4J_H */
