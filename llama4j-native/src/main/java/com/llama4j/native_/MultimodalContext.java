package com.llama4j.native_;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 多模态推理上下文 — 封装 VLM（Vision-Language Model）的原生调用
 *
 * <p>本类管理与 LlamaContext 关联的 mtmd 多模态上下文，
 * 提供多模态（文本 + 图片）的同步和流式推理能力。</p>
 *
 * <h2>使用示例</h2>
 * <pre>{@code
 * try (LlamaContext ctx = new LlamaContext("/models/qwen2-vl.gguf", ModelParams.DEFAULT);
 *      MultimodalContext mmCtx = ctx.enableMultimodal("/models/qwen2-vl.gguf")) {
 *
 *     byte[] imageBytes = Files.readAllBytes(Path.of("/tmp/photo.jpg"));
 *     String result = mmCtx.generate(
 *         GenerateParams.builder("<__media__>\n描述这张图片").maxTokens(512).build(),
 *         new byte[][]{imageBytes});
 *     System.out.println(result);
 * }
 * }</pre>
 */
public final class MultimodalContext implements AutoCloseable {

    private static final Logger LOG = LoggerFactory.getLogger(MultimodalContext.class);

    private final LlamaContext context;
    private final AtomicBoolean closed = new AtomicBoolean(false);

    MultimodalContext(LlamaContext context) {
        this.context = context;
    }

    /**
     * 多模态同步生成。
     *
     * @param params  生成参数（prompt 中需包含 &lt;__media__&gt; 标记）
     * @param images  图片数据数组（byte[][]），支持多张图片
     * @return 生成的文本
     * @throws IllegalArgumentException 如果 images 为 null 或为空
     */
    public String generate(GenerateParams params, byte[][] images) {
        ensureOpen();
        if (images == null || images.length == 0) {
            throw new IllegalArgumentException("images 不能为空");
        }
        LOG.debug("多模态生成: prompt长度={}, 图片数={}", params.prompt().length(), images.length);

        String stopToken = params.stopTokens() != null && !params.stopTokens().isEmpty()
            ? params.stopTokens().get(0) : null;

        return LlamaContext.doGenerateMultimodal(context.handle(), params.prompt(), images,
            params.maxTokens(), params.temperature(), params.topK(), params.topP(),
            params.repeatPenalty(), params.seed(), stopToken);
    }

    /**
     * 多模态流式生成。
     *
     * @param params    生成参数
     * @param images    图片数据数组
     * @param callback  token 回调
     * @throws IllegalArgumentException 如果 images 为 null 或为空
     */
    public void generateStream(GenerateParams params, byte[][] images, TokenCallback callback) {
        ensureOpen();
        if (images == null || images.length == 0) {
            throw new IllegalArgumentException("images 不能为空");
        }
        String stopToken = params.stopTokens() != null && !params.stopTokens().isEmpty()
            ? params.stopTokens().get(0) : null;

        LlamaContext.doGenerateMultimodalStream(context.handle(), params.prompt(), images,
            params.maxTokens(), params.temperature(), params.topK(), params.topP(),
            params.repeatPenalty(), params.seed(), stopToken, callback);
    }

    @Override
    public void close() {
        if (closed.compareAndSet(false, true)) {
            LlamaContext.doFreeMultimodal(context.handle());
            LOG.debug("MultimodalContext 已关闭");
        }
    }

    public boolean isClosed() {
        return closed.get();
    }

    private void ensureOpen() {
        if (closed.get()) {
            throw new IllegalStateException("MultimodalContext 已关闭");
        }
        if (context.isClosed()) {
            throw new IllegalStateException("底层 LlamaContext 已关闭");
        }
    }
}
