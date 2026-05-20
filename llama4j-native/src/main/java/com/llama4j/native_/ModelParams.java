package com.llama4j.native_;

/**
 * 模型加载参数 — 不可变配置对象
 *
 * <p>控制 GGUF 模型如何被加载到内存中，包括上下文窗口大小、
 * GPU 卸载策略和 CPU 线程分配。使用 Builder 模式构建实例。</p>
 *
 * <h2>参数说明</h2>
 * <ul>
 *   <li>{@code nCtx} — 上下文窗口大小（token 数量），决定模型一次能处理的最大序列长度。
 *       常见值：2048（短文本）、4096（标准）、8192+（长文本）。值越大，内存占用越高。</li>
 *   <li>{@code nGpuLayers} — 卸载到 GPU 的模型层数。-1 表示全部卸载（需要足够的显存），
 *       0 表示纯 CPU 推理。通常从 -1 开始，如果显存不足再逐步减少。</li>
 *   <li>{@code nThreads} — CPU 推理线程数。建议设为物理核心数，超线程核心数效果通常更差。</li>
 * </ul>
 *
 * <h2>使用示例</h2>
 * <pre>{@code
 * // 使用默认参数
 * ModelParams params = ModelParams.DEFAULT;
 *
 * // 自定义参数
 * ModelParams params = ModelParams.builder()
 *     .nCtx(8192)           // 8K 上下文
 *     .nGpuLayers(-1)       // 全部 GPU 卸载
 *     .nThreads(8)          // 8 线程
 *     .build();
 * }</pre>
 *
 * @param nCtx       上下文窗口大小（token 数），默认 4096
 * @param nGpuLayers GPU 卸载层数，-1 表示全部，默认 -1
 * @param nThreads   CPU 推理线程数，默认为可用处理器数
 */
public record ModelParams(
    int nCtx,
    int nGpuLayers,
    int nThreads
) {

    /** 默认模型参数：4K 上下文、全 GPU 卸载、自动线程数 */
    public static final ModelParams DEFAULT = builder().build();

    /**
     * 紧凑构造器 — 参数校验
     *
     * <p>在 record 对象创建时自动执行，确保所有参数都在合法范围内。
     * 这是一种"快速失败"策略，避免将无效参数传递到原生层导致崩溃。</p>
     *
     * @param nCtx       上下文窗口大小
     * @param nGpuLayers GPU 卸载层数
     * @param nThreads   CPU 推理线程数
     */
    public ModelParams {
        if (nCtx <= 0) {
            throw new IllegalArgumentException("上下文大小 nCtx 必须为正数，当前值: " + nCtx);
        }
        if (nGpuLayers < -1) {
            throw new IllegalArgumentException("GPU 层数 nGpuLayers 必须 >= -1，当前值: " + nGpuLayers);
        }
        if (nThreads <= 0) {
            throw new IllegalArgumentException("线程数 nThreads 必须为正数，当前值: " + nThreads);
        }
    }

    /** @return 带有默认值的 Builder 实例 */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * ModelParams 的流畅构建器
     *
     * <p>采用 Builder 模式的原因：record 类不可变，无法在创建后修改字段。
     * Builder 提供了一种优雅的方式来逐步设置参数，同时保持不可变性。</p>
     */
    public static final class Builder {
        private int nCtx = 4096;
        private int nGpuLayers = -1;
        private int nThreads = Runtime.getRuntime().availableProcessors();

        private Builder() {}

        /** 设置上下文窗口大小（token 数） */
        public Builder nCtx(int nCtx)               { this.nCtx = nCtx; return this; }

        /** 设置 GPU 卸载层数（-1 = 全部） */
        public Builder nGpuLayers(int nGpuLayers)   { this.nGpuLayers = nGpuLayers; return this; }

        /** 设置 CPU 推理线程数 */
        public Builder nThreads(int nThreads)        { this.nThreads = nThreads; return this; }

        /** 构建不可变的 ModelParams 实例 */
        public ModelParams build() {
            return new ModelParams(nCtx, nGpuLayers, nThreads);
        }
    }
}
