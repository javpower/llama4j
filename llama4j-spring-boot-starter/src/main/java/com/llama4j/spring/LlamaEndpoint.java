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
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * OpenAI 兼容的聊天补全 API 控制器
 *
 * <p>暴露 {@code POST /v1/chat/completions} 端点，与 OpenAI Chat Completion API
 * 线级兼容。任何 OpenAI 客户端库或工具都可以直接对接 llama4j 服务。</p>
 *
 * <h2>支持的功能</h2>
 * <ul>
 *   <li>同步聊天补全（stream=false）</li>
 *   <li>SSE 流式输出（stream=true）</li>
 *   <li>函数调用（通过 tools 参数）</li>
 *   <li>可配置的生成参数</li>
 * </ul>
 *
 * <h2>测试命令</h2>
 * <pre>
 * curl -X POST http://localhost:8080/v1/chat/completions \
 *   -H "Content-Type: application/json" \
 *   -d '{
 *     "messages": [{"role": "user", "content": "你好！"}],
 *     "temperature": 0.7
 *   }'
 * </pre>
 */
@RestController
@RequestMapping("/v1")
public class LlamaEndpoint {

    private static final Logger LOG = LoggerFactory.getLogger(LlamaEndpoint.class);

    private final ToolEnabledChatService chatService;
    private final ToolRegistry toolRegistry;
    private final LlamaProperties properties;
    private final ObjectMapper objectMapper;

    /** 流式推理的异步执行器 */
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
     * 聊天补全端点 — OpenAI 兼容
     *
     * <p>根据请求中的 stream 参数决定返回同步响应还是 SSE 流。</p>
     */
    @PostMapping("/chat/completions")
    public ResponseEntity<?> chatCompletion(@RequestBody ChatCompletionRequest request) {
        LOG.debug("收到聊天补全请求: {} 条消息", request.messages() != null ? request.messages().size() : 0);

        // 流式请求
        if (Boolean.TRUE.equals(request.stream())) {
            return handleStreamRequest(request);
        }

        // 同步请求
        return handleSyncRequest(request);
    }

    /** 处理同步请求 */
    private ResponseEntity<ChatCompletionResponse> handleSyncRequest(ChatCompletionRequest request) {
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

    /** 处理流式请求 — 使用 SSE (Server-Sent Events) */
    private ResponseEntity<SseEmitter> handleStreamRequest(ChatCompletionRequest request) {
        SseEmitter emitter = new SseEmitter(300_000L); // 5 分钟超时
        String responseId = "chatcmpl-" + UUID.randomUUID().toString().substring(0, 8);

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
                            // 发送结束标记
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

        return ResponseEntity.ok()
            .contentType(MediaType.TEXT_EVENT_STREAM)
            .body(emitter);
    }

    /** 将 OpenAI 格式请求转换为内部 ChatRequest */
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
