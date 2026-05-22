package com.llama4j.providers.openai;

import java.util.Objects;

/**
 * OpenAI API 配置
 *
 * <p>同时兼容所有 OpenAI 兼容端点（DeepSeek、Moonshot、通义千问等），
 * 只需修改 baseUrl。</p>
 */
public class OpenAIConfig {

    private final String apiKey;
    private final String baseUrl;
    private final String modelName;

    private OpenAIConfig(Builder builder) {
        this.apiKey = Objects.requireNonNull(builder.apiKey, "apiKey 不能为 null");
        this.baseUrl = builder.baseUrl != null ? builder.baseUrl : "https://api.openai.com";
        this.modelName = builder.modelName != null ? builder.modelName : "gpt-4o";
    }

    public String apiKey() { return apiKey; }
    public String baseUrl() { return baseUrl; }
    public String modelName() { return modelName; }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private String apiKey;
        private String baseUrl;
        private String modelName;

        public Builder apiKey(String apiKey) { this.apiKey = apiKey; return this; }
        public Builder baseUrl(String baseUrl) { this.baseUrl = baseUrl; return this; }
        public Builder modelName(String modelName) { this.modelName = modelName; return this; }

        public OpenAIConfig build() {
            return new OpenAIConfig(this);
        }
    }
}
