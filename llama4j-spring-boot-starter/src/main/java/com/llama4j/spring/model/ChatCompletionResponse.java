package com.llama4j.spring.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * OpenAI 兼容的聊天补全响应 — 响应体 DTO
 *
 * <p>镜像 OpenAI Chat Completion API 的响应格式，支持同步和流式两种模式。</p>
 *
 * <h2>同步响应示例</h2>
 * <pre>
 * {
 *   "id": "chatcmpl-abc123",
 *   "object": "chat.completion",
 *   "created": 1700000000,
 *   "model": "qwen2.5-7b",
 *   "choices": [{"index": 0, "message": {"role": "assistant", "content": "你好！"}, "finish_reason": "stop"}],
 *   "usage": {"prompt_tokens": 10, "completion_tokens": 5, "total_tokens": 15}
 * }
 * </pre>
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ChatCompletionResponse(
    String id,
    String object,
    long created,
    String model,
    List<Choice> choices,
    Usage usage
) {

    /** 创建标准响应 */
    public static ChatCompletionResponse of(String id, String model, List<Choice> choices, Usage usage) {
        return new ChatCompletionResponse(id, "chat.completion", System.currentTimeMillis() / 1000, model, choices, usage);
    }

    /** 单个补全选择 */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Choice(
        int index,
        ChatCompletionRequest.ChatMessage message,
        @JsonProperty("finish_reason") String finishReason
    ) {
        public static Choice of(int index, String role, String content, String finishReason) {
            return new Choice(index, new ChatCompletionRequest.ChatMessage(role, content), finishReason);
        }
    }

    /** Token 使用统计 */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Usage(
        @JsonProperty("prompt_tokens") int promptTokens,
        @JsonProperty("completion_tokens") int completionTokens,
        @JsonProperty("total_tokens") int totalTokens
    ) {}

    /** 流式响应块 */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Chunk(
        String id,
        String object,
        long created,
        String model,
        List<ChunkChoice> choices
    ) {
        /** 创建内容增量块 */
        public static Chunk delta(String id, String model, String content, String finishReason) {
            var delta = new ChatCompletionRequest.ChatMessage("assistant", content);
            var choice = new ChunkChoice(0, delta, finishReason);
            return new Chunk(id, "chat.completion.chunk", System.currentTimeMillis() / 1000, model, List.of(choice));
        }

        /** 创建工具调用增量块 — 包含 name 和 arguments */
        public static Chunk toolCallDelta(String id, String model,
                                          int toolCallIndex, String toolCallId,
                                          String functionName, String arguments) {
            var callInfo = new ChatCompletionRequest.ToolCallInfo(
                toolCallId, "function",
                new ChatCompletionRequest.FunctionCall(functionName, arguments));
            var delta = new ChatCompletionRequest.ChatMessage("assistant", null, List.of(callInfo), null);
            var choice = new ChunkChoice(0, delta, null);
            return new Chunk(id, "chat.completion.chunk", System.currentTimeMillis() / 1000, model, List.of(choice));
        }

        /** 创建 finish 块 — 空 delta + finish_reason */
        public static Chunk finish(String id, String model, String finishReason) {
            var delta = new ChatCompletionRequest.ChatMessage(null, null, null, null);
            var choice = new ChunkChoice(0, delta, finishReason);
            return new Chunk(id, "chat.completion.chunk", System.currentTimeMillis() / 1000, model, List.of(choice));
        }
    }

    /** 流式块选择 */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record ChunkChoice(
        int index,
        ChatCompletionRequest.ChatMessage delta,
        @JsonProperty("finish_reason") String finishReason
    ) {}
}
