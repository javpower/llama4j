package com.llama4j.spring;

import com.llama4j.chat.Role;
import com.llama4j.core.ChatRequest;
import com.llama4j.core.ChatResponse;
import com.llama4j.core.ChatStreamListener;
import com.llama4j.spring.model.ChatCompletionRequest;
import com.llama4j.spring.model.ChatCompletionResponse;
import com.llama4j.tools.StreamingToolListener;
import com.llama4j.tools.ToolEnabledChatService;
import com.llama4j.tools.ToolRegistry;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import jakarta.annotation.PreDestroy;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@RestController
@RequestMapping("/v1")
public class LlamaEndpoint {

    private static final Logger LOG = LoggerFactory.getLogger(LlamaEndpoint.class);

    private final ToolEnabledChatService chatService;
    private final ToolRegistry toolRegistry;
    private final LlamaProperties properties;
    private final ObjectMapper objectMapper;

    private final ExecutorService streamExecutor = Executors.newFixedThreadPool(
        Runtime.getRuntime().availableProcessors() * 2, r -> {
        Thread t = new Thread(r, "llama4j-stream");
        t.setDaemon(true);
        return t;
    });

    public LlamaEndpoint(ToolEnabledChatService chatService,
                         ToolRegistry toolRegistry,
                         LlamaProperties properties,
                         ObjectMapper objectMapper) {
        this.chatService = chatService;
        this.toolRegistry = toolRegistry;
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    /**
     * 聊天补全 — 根据 stream 字段自动选择同步或流式响应。
     *
     * <p>遵循 OpenAI API 规范：请求体中的 stream 字段决定响应模式，
     * 不依赖 Accept 头进行路由。</p>
     */
    @PostMapping(value = "/chat/completions")
    public Object chatCompletion(@RequestBody ChatCompletionRequest request) {
        if (Boolean.TRUE.equals(request.stream())) {
            return handleStream(request);
        }
        return handleSync(request);
    }

    @PreDestroy
    void shutdown() {
        streamExecutor.shutdownNow();
    }

    /** 同步响应处理 */
    private ResponseEntity<ChatCompletionResponse> handleSync(ChatCompletionRequest request) {
        LOG.debug("收到同步聊天补全请求: {} 条消息", request.messages() != null ? request.messages().size() : 0);

        ChatRequest chatRequest = convertRequest(request);
        boolean hasTools = toolRegistry.size() > 0;
        ChatResponse chatResponse = hasTools
            ? chatService.chatWithTools(chatRequest)
            : chatService.getChatService().chat(chatRequest);

        String responseId = "chatcmpl-" + UUID.randomUUID().toString().substring(0, 8);

        ChatCompletionResponse.Choice choice = ChatCompletionResponse.Choice.of(
            0, "assistant", chatResponse.content(), "stop");

        ChatCompletionResponse.Usage usage = new ChatCompletionResponse.Usage(
            chatResponse.promptTokens(),
            chatResponse.completionTokens(),
            chatResponse.totalTokens());

        ChatCompletionResponse response = ChatCompletionResponse.of(
            responseId, request.model() != null ? request.model() : "llama4j",
            List.of(choice), usage);

        return ResponseEntity.ok(response);
    }

    /** 流式 SSE 响应处理 */
    private SseEmitter handleStream(ChatCompletionRequest request) {
        LOG.debug("收到流式聊天补全请求: {} 条消息", request.messages() != null ? request.messages().size() : 0);

        SseEmitter emitter = new SseEmitter(300_000L);
        String responseId = "chatcmpl-" + UUID.randomUUID().toString().substring(0, 8);

        emitter.onTimeout(() -> LOG.warn("SSE emitter 超时: {}", responseId));
        emitter.onError(e -> LOG.warn("SSE emitter 错误: {}", e.getMessage()));

        streamExecutor.execute(() -> {
            try {
                ChatRequest chatRequest = convertRequest(request);
                boolean hasTools = toolRegistry.size() > 0;

                LOG.info("[SSE {}] 流式请求开始: hasTools={}, registeredTools={}, messages={}, model={}",
                    responseId, hasTools, toolRegistry.size(),
                    request.messages() != null ? request.messages().size() : 0,
                    request.model());

                if (hasTools) {
                    LOG.info("[SSE {}] 服务端已注册 {} 个工具, 走流式工具调用路径",
                        responseId, toolRegistry.size());
                    handleStreamWithTools(emitter, responseId, request, chatRequest);
                } else {
                    LOG.debug("[SSE {}] 无 tools, 走普通流式路径", responseId);
                    handleStreamPlain(emitter, responseId, request, chatRequest);
                }
            } catch (Exception e) {
                LOG.error("[SSE {}] 流式处理异常: {}", responseId, e.getMessage(), e);
                emitter.completeWithError(e);
            }
        });

        return emitter;
    }

    /** 无工具的普通流式响应 */
    private void handleStreamPlain(SseEmitter emitter, String responseId,
                                   ChatCompletionRequest request, ChatRequest chatRequest) throws Exception {
        chatService.getChatService().chatStream(chatRequest, new ChatStreamListener() {
            @Override
            public void onToken(String token) {
                try {
                    sendContentChunk(emitter, responseId, request.model(), token, null);
                } catch (Exception e) {
                    emitter.completeWithError(e);
                }
            }

            @Override
            public void onComplete(ChatResponse response) {
                try {
                    var stopChunk = ChatCompletionResponse.Chunk.finish(responseId, request.model(), "stop");
                    emitter.send(SseEmitter.event()
                        .data(objectMapper.writeValueAsString(stopChunk)));
                    emitter.send(SseEmitter.event().data("[DONE]"));
                    LOG.info("[SSE {}] 普通流式完成: tokens={}, latency={}ms",
                        responseId, response.completionTokens(), response.latencyMs());
                    emitter.complete();
                } catch (Exception e) {
                    emitter.completeWithError(e);
                }
            }

            @Override
            public void onError(Throwable error) {
                LOG.error("[SSE {}] 普通流式错误: {}", responseId, error.getMessage());
                emitter.completeWithError(error);
            }
        });
    }

    /** 带工具调用的流式响应 */
    private void handleStreamWithTools(SseEmitter emitter, String responseId,
                                       ChatCompletionRequest request, ChatRequest chatRequest) throws Exception {
        int[] toolCallIndex = {0};

        chatService.chatStreamWithTools(chatRequest, new StreamingToolListener() {
            @Override
            public void onContentToken(String token) {
                try {
                    sendContentChunk(emitter, responseId, request.model(), token, null);
                } catch (Exception e) {
                    emitter.completeWithError(e);
                }
            }

            @Override
            public void onToolCall(com.llama4j.tools.ToolCall toolCall) {
                try {
                    int idx = toolCallIndex[0];
                    LOG.info("[SSE {}] 发送 tool_calls delta: index={}, name={}, arguments={}",
                        responseId, idx, toolCall.toolName(), toolCall.arguments());
                    // 工具调用 chunk: 带 name + arguments
                    var callChunk = ChatCompletionResponse.Chunk.toolCallDelta(
                        responseId, request.model(),
                        idx, toolCall.id(), toolCall.toolName(), toolCall.arguments());
                    emitter.send(SseEmitter.event()
                        .data(objectMapper.writeValueAsString(callChunk)));
                } catch (Exception e) {
                    emitter.completeWithError(e);
                }
            }

            @Override
            public void onToolResult(com.llama4j.tools.ToolResult result) {
                try {
                    LOG.info("[SSE {}] 发送 tool_calls finish: success={}, resultLen={}",
                        responseId, result.success(), result.content().length());
                    // finish chunk: 空 delta + finish_reason="tool_calls"
                    var finishChunk = ChatCompletionResponse.Chunk.finish(
                        responseId, request.model(), "tool_calls");
                    emitter.send(SseEmitter.event()
                        .data(objectMapper.writeValueAsString(finishChunk)));
                    toolCallIndex[0]++;
                } catch (Exception e) {
                    emitter.completeWithError(e);
                }
            }

            @Override
            public void onComplete(ChatResponse response) {
                try {
                    var stopChunk = ChatCompletionResponse.Chunk.finish(
                        responseId, request.model(), "stop");
                    emitter.send(SseEmitter.event()
                        .data(objectMapper.writeValueAsString(stopChunk)));
                    emitter.send(SseEmitter.event().data("[DONE]"));
                    LOG.info("[SSE {}] 流式工具调用完成: tokens={}, latency={}ms",
                        responseId,
                        response != null ? response.completionTokens() : -1,
                        response != null ? response.latencyMs() : -1);
                    emitter.complete();
                } catch (Exception e) {
                    emitter.completeWithError(e);
                }
            }

            @Override
            public void onError(Throwable error) {
                LOG.error("[SSE {}] 流式工具调用错误: {}", responseId, error.getMessage(), error);
                emitter.completeWithError(error);
            }
        });
    }

    private void sendContentChunk(SseEmitter emitter, String responseId,
                                  String model, String content, String finishReason) throws Exception {
        var chunk = ChatCompletionResponse.Chunk.delta(responseId, model, content, finishReason);
        emitter.send(SseEmitter.event().data(objectMapper.writeValueAsString(chunk)));
    }

    private ChatRequest convertRequest(ChatCompletionRequest request) {
        ChatRequest.Builder builder = ChatRequest.builder();

        if (request.messages() != null) {
            for (ChatCompletionRequest.ChatMessage msg : request.messages()) {
                Role role = Role.fromValue(msg.role());
                builder.addMessage(role, msg.content());
            }
        }

        LlamaProperties.InferenceConfig defaults = properties.getInference();
        builder.temperature(request.temperature() != null ? request.temperature().floatValue() : defaults.getTemperature());
        builder.maxTokens(request.maxTokens() != null ? request.maxTokens() : defaults.getMaxTokens());
        builder.topP(request.topP() != null ? request.topP().floatValue() : defaults.getTopP());
        builder.topK(defaults.getTopK());
        builder.repeatPenalty(defaults.getRepeatPenalty());

        return builder.build();
    }
}
