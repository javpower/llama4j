package com.llama4j.spring;

import com.llama4j.chat.Role;
import com.llama4j.core.ChatRequest;
import com.llama4j.core.ChatResponse;
import com.llama4j.core.ChatStreamListener;
import com.llama4j.spring.model.ChatCompletionRequest;
import com.llama4j.spring.model.ChatCompletionResponse;
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

    private final ExecutorService streamExecutor = Executors.newCachedThreadPool(r -> {
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
        boolean hasTools = request.tools() != null && !request.tools().isEmpty();
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

                chatService.getChatService().chatStream(chatRequest, new ChatStreamListener() {
                    @Override
                    public void onToken(String token) {
                        try {
                            ChatCompletionResponse.Chunk chunk =
                                ChatCompletionResponse.Chunk.delta(responseId, request.model(), token, null);
                            emitter.send(SseEmitter.event()
                                .data(objectMapper.writeValueAsString(chunk)));
                        } catch (Exception e) {
                            emitter.completeWithError(e);
                        }
                    }

                    @Override
                    public void onComplete(ChatResponse response) {
                        try {
                            ChatCompletionResponse.Chunk done =
                                ChatCompletionResponse.Chunk.delta(responseId, request.model(), "", "stop");
                            emitter.send(SseEmitter.event()
                                .data(objectMapper.writeValueAsString(done)));
                            emitter.send(SseEmitter.event().data("[DONE]"));
                            emitter.complete();
                        } catch (Exception e) {
                            emitter.completeWithError(e);
                        }
                    }

                    @Override
                    public void onError(Throwable error) {
                        emitter.completeWithError(error);
                    }
                });
            } catch (Exception e) {
                emitter.completeWithError(e);
            }
        });

        return emitter;
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
