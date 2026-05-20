package com.llama4j.native_;

/**
 * 文本生成参数 — 不可变配置对象
 *
 * <p>控制模型推理时的采样行为，包括温度、Top-K/Top-P 采样、
 * 重复惩罚和 token 限制。这些参数直接影响生成文本的质量和多样性。</p>
 *
 * <h2>采样参数详解</h2>
 * <ul>
 *   <li>{@code temperature} — 控制随机性。0 = 贪心解码（每次选概率最高的 token），
 *       0.7 = 适度随机（推荐用于对话），1.5+ = 高度随机（创意写作）。</li>
 *   <li>{@code topK} — 只从概率最高的 K 个 token 中采样。40 是常用值，
 *       设为 0 则不限制。值越小生成越确定，值越大越多样。</li>
 *   <li>{@code topP} — 核采样（Nucleus Sampling）：只从累积概率达到 P 的 token 中采样。
 *       0.9 表示只考虑覆盖 90% 概率质量的 token。比 topK 更灵活。</li>
 *   <li>{@code repeatPenalty} — 重复惩罚系数。1.0 = 无惩罚，1.1 = 轻度惩罚（推荐），
 *       1.5+ = 强力惩罚。用于防止模型陷入重复循环。</li>
 *   <li>{@code seed} — 随机种子。-1 表示每次生成结果不同，
 *       固定种子可实现可复现的生成结果。</li>
 * </ul>
 *
 * <h2>使用示例</h2>
 * <pre>{@code
 * GenerateParams params = GenerateParams.builder("讲一个故事")
 *     .maxTokens(1024)
 *     .temperature(0.8f)
 *     .topP(0.95f)
 *     .repeatPenalty(1.15f)
 *     .seed(42L)             // 固定种子，结果可复现
 *     .build();
 * }</pre>
 *
 * @param prompt        输入提示词文本
 * @param maxTokens     最大生成 token 数，默认 2048
 * @param temperature   采样温度，0 = 贪心，默认 0.7
 * @param topK          Top-K 采样参数，默认 40
 * @param topP          Top-P 核采样阈值，默认 0.9
 * @param repeatPenalty 重复惩罚系数，默认 1.1
 * @param seed          随机种子，-1 = 不确定，默认 -1
 */
public record GenerateParams(
    String prompt,
    int maxTokens,
    float temperature,
    int topK,
    float topP,
    float repeatPenalty,
    long seed
) {

    /**
     * 紧凑构造器 — 参数校验
     *
     * <p>确保所有采样参数在合理范围内，防止无效参数导致原生层崩溃
     * 或产生无意义的生成结果。</p>
     */
    public GenerateParams {
        if (prompt == null || prompt.isBlank()) {
            throw new IllegalArgumentException("提示词 prompt 不能为空或空白");
        }
        if (maxTokens <= 0) {
            throw new IllegalArgumentException("最大 token 数 maxTokens 必须为正数，当前值: " + maxTokens);
        }
        if (temperature < 0) {
            throw new IllegalArgumentException("温度 temperature 不能为负数，当前值: " + temperature);
        }
        if (topK < 0) {
            throw new IllegalArgumentException("Top-K 必须为非负数，当前值: " + topK);
        }
        if (topP < 0 || topP > 1) {
            throw new IllegalArgumentException("Top-P 必须在 [0, 1] 范围内，当前值: " + topP);
        }
        if (repeatPenalty < 1.0f) {
            throw new IllegalArgumentException("重复惩罚必须 >= 1.0，当前值: " + repeatPenalty);
        }
    }

    /** 创建一个带有指定提示词的 Builder */
    public static Builder builder(String prompt) {
        return new Builder(prompt);
    }

    /**
     * GenerateParams 的流畅构建器
     */
    public static final class Builder {
        private final String prompt;
        private int maxTokens = 2048;
        private float temperature = 0.7f;
        private int topK = 40;
        private float topP = 0.9f;
        private float repeatPenalty = 1.1f;
        private long seed = -1L;

        private Builder(String prompt) {
            this.prompt = prompt;
        }

        /** 设置最大生成 token 数 */
        public Builder maxTokens(int maxTokens)         { this.maxTokens = maxTokens; return this; }

        /** 设置采样温度（0 = 贪心，越高越随机） */
        public Builder temperature(float temperature)    { this.temperature = temperature; return this; }

        /** 设置 Top-K 采样参数 */
        public Builder topK(int topK)                    { this.topK = topK; return this; }

        /** 设置 Top-P 核采样阈值 */
        public Builder topP(float topP)                  { this.topP = topP; return this; }

        /** 设置重复惩罚系数（>= 1.0） */
        public Builder repeatPenalty(float repeatPenalty){ this.repeatPenalty = repeatPenalty; return this; }

        /** 设置随机种子（-1 = 不确定） */
        public Builder seed(long seed)                   { this.seed = seed; return this; }

        /** 构建不可变的 GenerateParams 实例 */
        public GenerateParams build() {
            return new GenerateParams(prompt, maxTokens, temperature, topK, topP, repeatPenalty, seed);
        }
    }
}
