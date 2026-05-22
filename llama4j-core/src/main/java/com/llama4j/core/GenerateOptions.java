package com.llama4j.core;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * 统一生成参数 — 跨供应商的推理配置
 *
 * <p>本地推理和云端 API 共用同一套参数对象。本地专用参数（topK、repeatPenalty）
 * 和云端专用参数（apiKey、baseUrl、frequencyPenalty）共存，各实现按需读取。</p>
 */
public record GenerateOptions(
    Float temperature,
    Integer maxTokens,
    Integer topK,
    Float topP,
    Float repeatPenalty,
    Long seed,
    List<String> stopTokens,
    String apiKey,
    String baseUrl,
    String modelName,
    Float frequencyPenalty,
    Float presencePenalty
) {
    public GenerateOptions {
        stopTokens = stopTokens != null ? List.copyOf(stopTokens) : List.of();
    }

    public static Builder builder() {
        return new Builder();
    }

    /**
     * 合并两个选项 — primary 中非 null 的字段覆盖 fallback。
     */
    public static GenerateOptions merge(GenerateOptions primary, GenerateOptions fallback) {
        if (primary == null) return fallback;
        if (fallback == null) return primary;
        return new GenerateOptions(
            primary.temperature != null ? primary.temperature : fallback.temperature,
            primary.maxTokens != null ? primary.maxTokens : fallback.maxTokens,
            primary.topK != null ? primary.topK : fallback.topK,
            primary.topP != null ? primary.topP : fallback.topP,
            primary.repeatPenalty != null ? primary.repeatPenalty : fallback.repeatPenalty,
            primary.seed != null ? primary.seed : fallback.seed,
            !primary.stopTokens.isEmpty() ? primary.stopTokens : fallback.stopTokens,
            primary.apiKey != null ? primary.apiKey : fallback.apiKey,
            primary.baseUrl != null ? primary.baseUrl : fallback.baseUrl,
            primary.modelName != null ? primary.modelName : fallback.modelName,
            primary.frequencyPenalty != null ? primary.frequencyPenalty : fallback.frequencyPenalty,
            primary.presencePenalty != null ? primary.presencePenalty : fallback.presencePenalty
        );
    }

    public static class Builder {
        private Float temperature;
        private Integer maxTokens;
        private Integer topK;
        private Float topP;
        private Float repeatPenalty;
        private Long seed;
        private List<String> stopTokens;
        private String apiKey;
        private String baseUrl;
        private String modelName;
        private Float frequencyPenalty;
        private Float presencePenalty;

        public Builder temperature(Float v) { this.temperature = v; return this; }
        public Builder maxTokens(Integer v) { this.maxTokens = v; return this; }
        public Builder topK(Integer v) { this.topK = v; return this; }
        public Builder topP(Float v) { this.topP = v; return this; }
        public Builder repeatPenalty(Float v) { this.repeatPenalty = v; return this; }
        public Builder seed(Long v) { this.seed = v; return this; }
        public Builder stopTokens(List<String> v) { this.stopTokens = v; return this; }
        public Builder apiKey(String v) { this.apiKey = v; return this; }
        public Builder baseUrl(String v) { this.baseUrl = v; return this; }
        public Builder modelName(String v) { this.modelName = v; return this; }
        public Builder frequencyPenalty(Float v) { this.frequencyPenalty = v; return this; }
        public Builder presencePenalty(Float v) { this.presencePenalty = v; return this; }

        public GenerateOptions build() {
            return new GenerateOptions(temperature, maxTokens, topK, topP, repeatPenalty,
                seed, stopTokens, apiKey, baseUrl, modelName, frequencyPenalty, presencePenalty);
        }
    }
}
