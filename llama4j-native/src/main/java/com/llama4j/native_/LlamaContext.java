package com.llama4j.native_;

import com.llama4j.exception.ModelNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * JNI 核心桥接类 — llama.cpp 的主要 Java 入口
 *
 * <p>本类提供了对 llama.cpp C API 的安全、惯用的 Java 封装。
 * 它通过 {@link AutoCloseable} 管理原生模型生命周期，确保资源正确释放。
 * 所有原生操作都通过 nativeHandle（不透明指针）进行，并在调用前
 * 检查上下文是否已关闭，防止 use-after-free 错误。</p>
 *
 * <h2>线程安全</h2>
 * <p>单个 {@code LlamaContext} 实例对于并发生成调用是<strong>非线程安全</strong>的。
 * 多线程场景下，应为每个线程创建独立的上下文，或使用外部同步。
 * 分词和元数据查询操作则可以安全地从不同线程并发调用。</p>
 *
 * <h2>资源管理</h2>
 * <p>模型加载时会分配大量 GPU/CPU 内存（7B Q4 模型约需 4-5GB）。
 * 必须在使用完毕后调用 {@link #close()} 释放资源。推荐使用
 * try-with-resources 语句确保资源释放：</p>
 *
 * <h2>使用示例</h2>
 * <pre>{@code
 * try (LlamaContext ctx = new LlamaContext("/models/qwen2.5-7b-q4_k_m.gguf",
 *         ModelParams.DEFAULT)) {
 *
 *     // 简单生成
 *     String response = ctx.generate(GenerateParams.builder("你好，世界！")
 *         .maxTokens(256)
 *         .temperature(0.7f)
 *         .build());
 *
 *     // 流式生成
 *     ctx.generateStream("讲一个故事", token -> {
 *         System.out.print(token);
 *     });
 * }
 * }</pre>
 */
public final class LlamaContext implements AutoCloseable {

    private static final Logger LOG = LoggerFactory.getLogger(LlamaContext.class);

    // 静态初始化块：在类加载时自动加载原生库
    static {
        NativeLoader.load("llama4j");
    }

    /** 原生层 LlamaSession 的不透明指针，0 表示无效 */
    private final long nativeHandle;

    /** 关闭标志，防止重复释放和 use-after-free */
    private volatile boolean closed = false;

    /**
     * 加载模型并创建推理上下文。
     *
     * <p>此构造函数会调用原生层的 loadModel 函数，分配 GPU/CPU 内存。
     * 加载时间取决于模型大小和磁盘速度，通常为几秒到几十秒。</p>
     *
     * @param modelPath GGUF 模型文件的绝对路径
     * @param params    模型加载参数（上下文大小、GPU 层数等）
     * @throws UnsatisfiedLinkError 如果原生库加载失败
     * @throws ModelNotFoundException 如果模型文件不存在或格式错误
     */
    public LlamaContext(String modelPath, ModelParams params) {
        LOG.info("正在加载模型: {} (nCtx={}, nGpuLayers={}, nThreads={})",
            modelPath, params.nCtx(), params.nGpuLayers(), params.nThreads());
        this.nativeHandle = loadModel(modelPath, params.nCtx(), params.nGpuLayers(), params.nThreads());
        if (nativeHandle == 0) {
            throw new ModelNotFoundException(modelPath);
        }
        LOG.info("模型加载成功 (nativeHandle={})", nativeHandle);
    }

    /* ──────────────────────────────────────────
     *  原生方法声明
     *  ──────────────────────────────────────────
     *  这些方法在 llama4j.cpp 中实现，通过 JNI 调用 llama.cpp C API。
     *  方法名遵循 JNI 命名规范：Java_包名_类名_方法名
     */

    /** 加载模型，返回原生会话指针 */
    private static native long loadModel(String modelPath, int nCtx, int nGpuLayers, int nThreads);

    /** 释放原生模型和上下文资源 */
    private static native void freeModel(long nativeHandle);

    /** 将文本分词为 token ID 数组 */
    private static native int[] tokenize(long nativeHandle, String text, boolean addBos);

    /** 将 token ID 转换回文本 */
    private static native String detokenize(long nativeHandle, int[] tokens);

    /** 同步生成文本，返回完整结果 */
    private static native String generate(long nativeHandle, String prompt,
        int maxTokens, float temperature, int topK, float topP,
        float repeatPenalty, long seed, String stopToken);

    /** 流式生成文本，每个 token 通过回调传递 */
    private static native void generateStream(long nativeHandle, String prompt,
        int maxTokens, float temperature, int topK, float topP,
        float repeatPenalty, long seed, String stopToken, TokenCallback callback);

    /** 带 Grammar 约束的同步生成 */
    private static native String generateWithGrammar(long nativeHandle, String prompt,
        int maxTokens, float temperature, int topK, float topP,
        float repeatPenalty, long seed, String stopToken, long grammarSamplerHandle);

    /** 带 Grammar 约束的流式生成 */
    private static native void generateStreamWithGrammar(long nativeHandle, String prompt,
        int maxTokens, float temperature, int topK, float topP,
        float repeatPenalty, long seed, String stopToken,
        long grammarSamplerHandle, TokenCallback callback);

    /** 保存 KV 缓存会话状态 */
    private static native byte[] saveSession(long nativeHandle);

    /** 恢复 KV 缓存会话状态 */
    private static native void loadSession(long nativeHandle, byte[] data);

    /** 获取模型内嵌的 chat template */
    private static native String getChatTemplate(long nativeHandle);

    /** 获取模型词汇表大小 */
    private static native int getVocabSize(long nativeHandle);

    /** 获取上下文窗口大小 */
    private static native int getContextSize(long nativeHandle);

    /** 获取 KV 缓存中的 token 数量 */
    private static native int getKvCacheTokenCount(long nativeHandle);

    /** 使用 llama.cpp 内置引擎渲染对话模板 */
    private static native String applyChatTemplate(long nativeHandle, String[] roles, String[] contents, boolean addAssistant);

    /** 创建 grammar 约束采样器 */
    private static native long createGrammarSampler(long nativeHandle, String grammarStr, String grammarRoot);

    /** 释放 grammar 采样器 */
    private static native void freeGrammarSampler(long samplerHandle);

    /** 生成文本嵌入向量 */
    private static native float[] embed(long nativeHandle, String text);

    /** 获取模型描述 */
    private static native String getModelDesc(long nativeHandle);

    /** 获取模型文件大小 */
    private static native long getModelSize(long nativeHandle);

    /** 获取模型参数数量 */
    private static native long getModelNParams(long nativeHandle);

    /* ──────────────────────────────────────────
     *  公开 API — 分词
     *  ────────────────────────────────────────── */

    /**
     * 将文本分词为 token ID 数组。
     *
     * @param text    要分词的文本
     * @param addBos  是否在开头添加 BOS（Beginning Of Sequence）token
     * @return token ID 数组
     */
    public int[] tokenize(String text, boolean addBos) {
        ensureOpen();
        return tokenize(nativeHandle, text, addBos);
    }

    /**
     * 将 token ID 数组转换回文本。
     *
     * @param tokens token ID 数组
     * @return 解码后的文本
     */
    public String detokenize(int[] tokens) {
        ensureOpen();
        return detokenize(nativeHandle, tokens);
    }

    /* ──────────────────────────────────────────
     *  公开 API — 推理
     *  ────────────────────────────────────────── */

    /**
     * 同步生成文本 — 阻塞直到生成完毕。
     *
     * <p>此方法会阻塞调用线程，直到模型生成完毕或达到 maxTokens 限制。
     * 对于需要实时输出的场景，请使用 {@link #generateStream}。</p>
     *
     * @param params 生成参数（提示词、温度、token 限制等）
     * @return 生成的文本内容
     */
    public String generate(GenerateParams params) {
        ensureOpen();
        LOG.debug("开始同步生成 (maxTokens={}, temperature={})", params.maxTokens(), params.temperature());
        String stopToken = params.stopTokens() != null && !params.stopTokens().isEmpty()
            ? params.stopTokens().get(0) : null;

        GrammarConstraint grammar = resolveGrammar(params);
        try {
            if (grammar != null) {
                return generateWithGrammar(nativeHandle, params.prompt(), params.maxTokens(),
                    params.temperature(), params.topK(), params.topP(),
                    params.repeatPenalty(), params.seed(), stopToken, grammar.handle());
            }
            return generate(nativeHandle, params.prompt(), params.maxTokens(),
                params.temperature(), params.topK(), params.topP(),
                params.repeatPenalty(), params.seed(), stopToken);
        } finally {
            // 如果是 jsonMode 自动创建的临时 grammar，用完关闭
            if (grammar != null && params.grammar() == null) {
                grammar.close();
            }
        }
    }

    /**
     * 流式生成文本 — 每个 token 实时回调。
     *
     * <p>此方法同样会阻塞调用线程，但每生成一个 token 就通过
     * {@link TokenCallback} 回调传递给调用方，实现逐字输出效果。</p>
     *
     * @param prompt   输入提示词
     * @param callback token 回调接口
     */
    public void generateStream(String prompt, TokenCallback callback) {
        ensureOpen();
        generateStream(nativeHandle, prompt, 2048, 0.7f, 40, 0.9f, 1.1f, -1L, null, callback);
    }

    /**
     * 流式生成文本（完整参数版本）。
     *
     * @param params    生成参数
     * @param callback  token 回调接口
     */
    public void generateStream(GenerateParams params, TokenCallback callback) {
        ensureOpen();
        String stopToken = params.stopTokens() != null && !params.stopTokens().isEmpty()
            ? params.stopTokens().get(0) : null;

        GrammarConstraint grammar = resolveGrammar(params);
        try {
            if (grammar != null) {
                generateStreamWithGrammar(nativeHandle, params.prompt(), params.maxTokens(),
                    params.temperature(), params.topK(), params.topP(),
                    params.repeatPenalty(), params.seed(), stopToken,
                    grammar.handle(), callback);
            } else {
                generateStream(nativeHandle, params.prompt(), params.maxTokens(),
                    params.temperature(), params.topK(), params.topP(),
                    params.repeatPenalty(), params.seed(), stopToken, callback);
            }
        } finally {
            if (grammar != null && params.grammar() == null) {
                grammar.close();
            }
        }
    }

    /* ──────────────────────────────────────────
     *  公开 API — 会话状态
     *  ────────────────────────────────────────── */

    /**
     * 保存当前 KV 缓存会话状态。
     *
     * @return 序列化的会话状态
     */
    public SessionState saveSession() {
        ensureOpen();
        byte[] data = saveSession(nativeHandle);
        return new SessionState(data);
    }

    /**
     * 恢复之前保存的会话状态。
     *
     * @param state 要恢复的会话状态
     */
    public void loadSession(SessionState state) {
        ensureOpen();
        loadSession(nativeHandle, state.data());
    }

    /* ──────────────────────────────────────────
     *  公开 API — 元数据查询
     *  ────────────────────────────────────────── */

    /** @return 模型内嵌的 chat template 字符串 */
    public String getChatTemplate() {
        ensureOpen();
        return getChatTemplate(nativeHandle);
    }

    /** @return 模型词汇表大小 */
    public int getVocabSize() {
        ensureOpen();
        return getVocabSize(nativeHandle);
    }

    /** @return 上下文窗口大小（n_ctx） */
    public int getContextSize() {
        ensureOpen();
        return getContextSize(nativeHandle);
    }

    /** @return KV 缓存中当前存储的 token 数量 */
    public int getKvCacheTokenCount() {
        ensureOpen();
        return getKvCacheTokenCount(nativeHandle);
    }

    /* ──────────────────────────────────────────
     *  公开 API — 聊天模板渲染
     *  ────────────────────────────────────────── */

    /**
     * 使用 llama.cpp 内置引擎渲染对话模板。
     *
     * <p>此方法直接调用 llama_chat_apply_template，支持所有主流模型格式，
     * 无需自定义 Jinja2 解析器。</p>
     *
     * @param roles     消息角色数组（如 "system", "user", "assistant"）
     * @param contents  消息内容数组
     * @param addAssistant 是否在末尾添加助手前缀
     * @return 渲染后的提示词字符串
     */
    public String applyChatTemplate(String[] roles, String[] contents, boolean addAssistant) {
        ensureOpen();
        return applyChatTemplate(nativeHandle, roles, contents, addAssistant);
    }

    /* ──────────────────────────────────────────
     *  公开 API — Grammar 约束生成
     *  ────────────────────────────────────────── */

    /**
     * 创建 grammar 约束采样器，用于 JSON 模式等结构化输出。
     *
     * @param grammarStr  GBNF 语法字符串
     * @param grammarRoot 根规则名称
     * @return 采样器的不透明指针（需调用 freeGrammar 释放）
     * @deprecated 使用 {@link GrammarConstraint#create(LlamaContext, String, String)} 代替
     */
    @Deprecated
    public long createGrammar(String grammarStr, String grammarRoot) {
        ensureOpen();
        return createGrammarSampler(nativeHandle, grammarStr, grammarRoot);
    }

    /**
     * 释放 grammar 采样器
     * @deprecated 使用 {@link GrammarConstraint#close()} 代替
     */
    @Deprecated
    public void freeGrammar(long grammarHandle) {
        if (grammarHandle != 0) freeGrammarSampler(grammarHandle);
    }

    /* ──────────────────────────────────────────
     *  公开 API — Embeddings 嵌入向量
     *  ────────────────────────────────────────── */

    /**
     * 生成文本的嵌入向量。
     *
     * @param text 输入文本
     * @return 嵌入向量浮点数组
     */
    public float[] embed(String text) {
        ensureOpen();
        return embed(nativeHandle, text);
    }

    /* ──────────────────────────────────────────
     *  公开 API — 扩展元数据
     *  ────────────────────────────────────────── */

    /** @return 模型描述（如 "Qwen2 1.5B Q4_K_M"） */
    public String getModelDescription() {
        ensureOpen();
        return getModelDesc(nativeHandle);
    }

    /** @return 模型文件大小（字节） */
    public long getModelSize() {
        ensureOpen();
        return getModelSize(nativeHandle);
    }

    /** @return 模型参数数量 */
    public long getModelParameterCount() {
        ensureOpen();
        return getModelNParams(nativeHandle);
    }

    /* ──────────────────────────────────────────
     *  生命周期管理
     *  ────────────────────────────────────────── */

    /**
     * 释放原生模型和上下文资源。
     *
     * <p>此方法是幂等的——多次调用不会有副作用。调用 close 后，
     * 所有其他方法将抛出 {@link IllegalStateException}。</p>
     */
    @Override
    public void close() {
        if (!closed) {
            LOG.info("正在关闭 LlamaContext (nativeHandle={})", nativeHandle);
            freeModel(nativeHandle);
            closed = true;
        }
    }

    /** @return 上下文是否已关闭 */
    public boolean isClosed() {
        return closed;
    }

    /**
     * 安全检查 — 确保上下文未关闭。
     *
     * <p>所有公开方法在调用原生函数前都会先执行此检查，
     * 防止在原生层访问已释放的内存（use-after-free）。</p>
     */
    /**
     * 解析生成参数中的 grammar 约束。
     *
     * <p>如果显式设置了 grammar，直接使用；如果开启了 jsonMode 但没有 grammar，
     * 则自动创建一个 JSON grammar。后者会在生成完成后由调用方负责关闭。</p>
     */
    private GrammarConstraint resolveGrammar(GenerateParams params) {
        if (params.grammar() != null) {
            return params.grammar();
        }
        if (params.jsonMode()) {
            return GrammarConstraint.json(this);
        }
        return null;
    }

    private void ensureOpen() {
        if (closed) {
            throw new IllegalStateException("LlamaContext 已关闭，不能再使用");
        }
    }

    /**
     * 终结器 — 安全网，防止忘记调用 close() 导致内存泄漏。
     *
     * <p>注意：终结器的执行时间不确定，不应依赖它来释放资源。
     * 始终使用 try-with-resources 或显式调用 close()。</p>
     */
    @Override
    protected void finalize() {
        if (!closed) {
            LOG.warn("LlamaContext 未显式关闭 — 在终结器中释放原生资源（请使用 try-with-resources）");
            close();
        }
    }
}
