package com.llama4j.providers.openai;

import com.llama4j.chat.Message;
import com.llama4j.chat.Role;
import com.llama4j.core.*;
import com.llama4j.core.ToolSchema;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.CompletableFuture;

/**
 * OpenAI Chat Completions API 客户端 — 实现 {@link Model} 接口
 *
 * <p>使用 Java 11+ HttpClient 调用 OpenAI API。兼容所有 OpenAI 兼容端点
 * （DeepSeek、Moonshot、通义千问等），只需修改 {@link OpenAIConfig#baseUrl()}。</p>
 *
 * <h2>使用示例</h2>
 * <pre>{@code
 * Model openai = new OpenAIModel(OpenAIConfig.builder()
 *     .apiKey("sk-xxx")
 *     .modelName("gpt-4o")
 *     .build());
 *
 * ChatResponse response = openai.chat(ChatRequest.builder()
 *     .addMessage(Role.USER, "Hello!")
 *     .build());
 * }</pre>
 */
public class OpenAIModel implements Model {

    private static final Logger LOG = LoggerFactory.getLogger(OpenAIModel.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final OpenAIConfig config;
    private final HttpClient httpClient;

    public OpenAIModel(OpenAIConfig config) {
        this.config = Objects.requireNonNull(config, "OpenAIConfig 不能为 null");
        this.httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(30))
            .build();
        LOG.info("OpenAI Model 初始化: baseUrl={}, model={}", config.baseUrl(), config.modelName());
    }

    @Override
    public ChatResponse chat(ChatRequest request) {
        try {
            ObjectNode body = buildRequestBody(request, false);
            HttpRequest httpRequest = buildHttpRequest(body);

            long startTime = System.currentTimeMillis();
            HttpResponse<String> httpResponse = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString());
            long latencyMs = System.currentTimeMillis() - startTime;

            if (httpResponse.statusCode() != 200) {
                throw new RuntimeException("OpenAI API 错误 (HTTP " + httpResponse.statusCode() + "): " + httpResponse.body());
            }

            return parseResponse(httpResponse.body(), latencyMs);
        } catch (IOException | InterruptedException e) {
            throw new RuntimeException("OpenAI API 调用失败", e);
        }
    }

    @Override
    public CompletableFuture<ChatResponse> chatStream(ChatRequest request, ChatStreamListener listener) {
        try {
            ObjectNode body = buildRequestBody(request, true);
            HttpRequest httpRequest = buildHttpRequest(body);

            long startTime = System.currentTimeMillis();
            CompletableFuture<ChatResponse> future = new CompletableFuture<>();

            httpClient.sendAsync(httpRequest, HttpResponse.BodyHandlers.ofLines())
                .thenAccept(response -> {
                    if (response.statusCode() != 200) {
                        try {
                            String errorBody = response.body().collect(java.util.stream.Collectors.joining());
                            listener.onError(new RuntimeException("OpenAI API 错误: " + errorBody));
                        } catch (Exception e) {
                            listener.onError(e);
                        }
                        return;
                    }

                    try {
                        StringBuilder contentBuilder = new StringBuilder();
                        int promptTokens = 0;
                        int completionTokens = 0;

                        response.body().forEach(line -> {
                            try {
                                if ("[DONE]".equals(line)) return;
                                if (line.startsWith("data: ")) {
                                    String json = line.substring(6).trim();
                                    if (json.isEmpty() || "[DONE]".equals(json)) return;

                                    JsonNode chunk = MAPPER.readTree(json);
                                    JsonNode choices = chunk.get("choices");
                                    if (choices != null && choices.size() > 0) {
                                        JsonNode delta = choices.get(0).get("delta");
                                        if (delta != null && delta.has("content") && !delta.get("content").isNull()) {
                                            String token = delta.get("content").asText();
                                            contentBuilder.append(token);
                                            listener.onToken(token);
                                        }
                                    }
                                    // 解析 usage（如果有）
                                    if (chunk.has("usage") && !chunk.get("usage").isNull()) {
                                        JsonNode usage = chunk.get("usage");
                                        // 这些会在最后的 chunk 中出现
                                    }
                                }
                            } catch (Exception e) {
                                LOG.warn("解析 SSE chunk 失败: {}", e.getMessage());
                            }
                        });

                        long latencyMs = System.currentTimeMillis() - startTime;
                        String content = contentBuilder.toString();
                        int compTokens = estimateTokens(content);
                        double tps = latencyMs > 0 ? (double) compTokens / (latencyMs / 1000.0) : 0.0;

                        ChatResponse chatResponse = ChatResponse.of(content, 0, compTokens, tps, latencyMs);
                        listener.onComplete(chatResponse);
                        future.complete(chatResponse);
                    } catch (Exception e) {
                        listener.onError(e);
                        future.completeExceptionally(e);
                    }
                })
                .exceptionally(ex -> {
                    listener.onError(ex);
                    future.completeExceptionally(ex);
                    return null;
                });

            return future;
        } catch (Exception e) {
            listener.onError(e);
            return CompletableFuture.failedFuture(e);
        }
    }

    @Override
    public String getModelName() {
        return config.modelName();
    }

    /* ──────────────────────────────────────────
     *  内部辅助方法
     *  ────────────────────────────────────────── */

    private HttpRequest buildHttpRequest(ObjectNode body) throws IOException {
        String jsonBody = MAPPER.writeValueAsString(body);
        String endpoint = config.baseUrl().replaceAll("/+$", "") + "/v1/chat/completions";

        return HttpRequest.newBuilder()
            .uri(URI.create(endpoint))
            .header("Content-Type", "application/json")
            .header("Authorization", "Bearer " + config.apiKey())
            .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
            .timeout(Duration.ofSeconds(120))
            .build();
    }

    private ObjectNode buildRequestBody(ChatRequest request, boolean stream) {
        ObjectNode body = MAPPER.createObjectNode();
        body.put("model", config.modelName());
        body.put("stream", stream);

        // 消息
        ArrayNode messages = body.putArray("messages");
        for (Message msg : request.messages()) {
            ObjectNode msgNode = messages.addObject();
            msgNode.put("role", msg.role().value());
            msgNode.put("content", msg.content());
        }

        // 参数
        if (request.temperature() != 0.7f) body.put("temperature", request.temperature());
        if (request.maxTokens() != 2048) body.put("max_tokens", request.maxTokens());
        if (request.topP() != 0.9f) body.put("top_p", request.topP());
        if (request.seed() != -1) body.put("seed", request.seed());

        return body;
    }

    private ChatResponse parseResponse(String responseBody, long latencyMs) throws IOException {
        JsonNode root = MAPPER.readTree(responseBody);

        String content = "";
        JsonNode choices = root.get("choices");
        if (choices != null && choices.size() > 0) {
            JsonNode message = choices.get(0).get("message");
            if (message != null && message.has("content")) {
                content = message.get("content").asText("");
            }
        }

        int promptTokens = 0;
        int completionTokens = 0;
        if (root.has("usage")) {
            JsonNode usage = root.get("usage");
            promptTokens = usage.has("prompt_tokens") ? usage.get("prompt_tokens").asInt() : 0;
            completionTokens = usage.has("completion_tokens") ? usage.get("completion_tokens").asInt() : 0;
        }

        if (completionTokens == 0) {
            completionTokens = estimateTokens(content);
        }

        double tps = latencyMs > 0 ? (double) completionTokens / (latencyMs / 1000.0) : 0.0;
        return ChatResponse.of(content, promptTokens, completionTokens, tps, latencyMs);
    }

    private int estimateTokens(String text) {
        if (text == null) return 0;
        return text.length() / 4;
    }
}
