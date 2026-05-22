package com.llama4j.providers;

import com.llama4j.core.ChatService;
import com.llama4j.core.LocalModel;
import com.llama4j.core.Model;
import com.llama4j.providers.openai.OpenAIConfig;
import com.llama4j.providers.openai.OpenAIModel;

import java.util.Map;

/**
 * 模型供应商工厂 — 根据 type 创建对应的 Model 实现
 *
 * <h2>使用示例</h2>
 * <pre>{@code
 * Model model = ProviderFactory.create("openai", Map.of(
 *     "apiKey", "sk-xxx",
 *     "baseUrl", "https://api.deepseek.com",
 *     "modelName", "deepseek-chat"
 * ));
 * }</pre>
 */
public class ProviderFactory {

    /**
     * 创建模型实例。
     *
     * @param type   供应商类型: "local", "openai"
     * @param config 配置参数
     * @return Model 实例
     */
    public static Model create(String type, Map<String, String> config) {
        return switch (type) {
            case "local" -> createLocalModel(config);
            case "openai" -> createOpenAIModel(config);
            default -> throw new IllegalArgumentException("未知的供应商类型: " + type);
        };
    }

    private static Model createLocalModel(Map<String, String> config) {
        throw new UnsupportedOperationException(
            "LocalModel 需要 ChatService 实例，请直接使用 new LocalModel(chatService)");
    }

    private static Model createOpenAIModel(Map<String, String> config) {
        OpenAIConfig.Builder builder = OpenAIConfig.builder();
        if (config.containsKey("apiKey")) builder.apiKey(config.get("apiKey"));
        if (config.containsKey("baseUrl")) builder.baseUrl(config.get("baseUrl"));
        if (config.containsKey("modelName")) builder.modelName(config.get("modelName"));
        return new OpenAIModel(builder.build());
    }
}
