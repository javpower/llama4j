package com.llama4j.spring;

import com.llama4j.chat.Role;
import com.llama4j.core.*;
import com.llama4j.spring.model.ChatCompletionRequest;
import com.llama4j.spring.model.ChatCompletionResponse;
import com.llama4j.tools.ReActAgent;
import com.llama4j.tools.StreamingToolListener;
import com.llama4j.tools.ToolCall;
import com.llama4j.tools.ToolRegistry;
import com.llama4j.tools.ToolResult;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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

    private final ModelRegistry modelRegistry;
    private final ToolRegistry toolRegistry;
    private final LlamaProperties properties;
    private final ObjectMapper objectMapper;

    private final ExecutorService streamExecutor = Executors.newFixedThreadPool(
        Runtime.getRuntime().availableProcessors() * 2, r -> {
            Thread t = new Thread(r, "llama4j-stream");
            t.setDaemon(true);
            return t;
        });

    public LlamaEndpoint(ModelRegistry modelRegistry,
                         ToolRegistry toolRegistry,
                         LlamaProperties properties,
                         ObjectMapper objectMapper) {
        this.modelRegistry = modelRegistry;
        this.toolRegistry = toolRegistry;
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    @PostMapping(value = "/chat/completions")
    public Object chatCompletion(@RequestBody ChatCompletionRequest request) {
        if (Boolean.TRUE.equals(request.stream())) {
            return handleStream(request);
        }
        return handleSync(request);
    }

    /** 获取可用模型列表 */
    @GetMapping(value = "/models")
    public Object listModels() {
        List<Map<String, Object>> modelList = new ArrayList<>();
        for (String name : modelRegistry.modelNames()) {
            Model model = modelRegistry.get(name);
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", name);
            m.put("name", model.getModelName());
            m.put("default", name.equals(modelRegistry.defaultModelName()));
            modelList.add(m);
        }
        return Map.of("models", modelList);
    }

    @PreDestroy
    void shutdown() {
        streamExecutor.shutdownNow();
    }

    /** 解析请求中指定的模型，或使用默认模型 */
    private Model resolveModel(ChatCompletionRequest request) {
        String modelHint = request.model();
        if (modelHint != null && !modelHint.isBlank() && modelRegistry.contains(modelHint)) {
            return modelRegistry.get(modelHint);
        }
        return modelRegistry.getDefault();
    }

    /** 同步响应 */
    private ResponseEntity<ChatCompletionResponse> handleSync(ChatCompletionRequest request) {
        Model model = resolveModel(request);
        ChatRequest chatRequest = convertRequest(request);

        ChatResponse chatResponse;
        if (toolRegistry.size() > 0) {
            ReActAgent agent = ReActAgent.builder()
                .model(model)
                .toolRegistry(toolRegistry)
                .build();
            chatResponse = agent.call(chatRequest);
        } else {
            chatResponse = model.chat(chatRequest);
        }

        String responseId = "chatcmpl-" + UUID.randomUUID().toString().substring(0, 8);
        ChatCompletionResponse.Choice choice = ChatCompletionResponse.Choice.of(
            0, "assistant", chatResponse.content(), "stop");
        ChatCompletionResponse.Usage usage = new ChatCompletionResponse.Usage(
            chatResponse.promptTokens(),
            chatResponse.completionTokens(),
            chatResponse.totalTokens());

        return ResponseEntity.ok(ChatCompletionResponse.of(
            responseId,
            request.model() != null ? request.model() : "llama4j",
            List.of(choice), usage));
    }

    /** 流式 SSE 响应 */
    private SseEmitter handleStream(ChatCompletionRequest request) {
        Model model = resolveModel(request);
        SseEmitter emitter = new SseEmitter(300_000L);
        String responseId = "chatcmpl-" + UUID.randomUUID().toString().substring(0, 8);

        emitter.onTimeout(() -> LOG.warn("SSE 超时: {}", responseId));
        emitter.onError(e -> LOG.warn("SSE 错误: {}", e.getMessage()));

        streamExecutor.execute(() -> {
            try {
                ChatRequest chatRequest = convertRequest(request);
                boolean hasTools = toolRegistry.size() > 0;
                String modelName = request.model() != null ? request.model() : "llama4j";

                if (hasTools) {
                    ReActAgent agent = ReActAgent.builder()
                        .model(model)
                        .toolRegistry(toolRegistry)
                        .build();
                    handleStreamWithTools(emitter, responseId, modelName, agent, chatRequest);
                } else {
                    handleStreamPlain(emitter, responseId, modelName, model, chatRequest);
                }
            } catch (Exception e) {
                LOG.error("[SSE {}] 异常: {}", responseId, e.getMessage(), e);
                emitter.completeWithError(e);
            }
        });

        return emitter;
    }

    private void handleStreamPlain(SseEmitter emitter, String responseId,
                                   String modelName, Model model, ChatRequest chatRequest) {
        model.chatStream(chatRequest, new ChatStreamListener() {
            @Override
            public void onToken(String token) {
                try {
                    sendContentChunk(emitter, responseId, modelName, token);
                } catch (Exception e) {
                    emitter.completeWithError(e);
                }
            }

            @Override
            public void onComplete(ChatResponse response) {
                try {
                    emitter.send(SseEmitter.event().data(
                        objectMapper.writeValueAsString(
                            ChatCompletionResponse.Chunk.finish(responseId, modelName, "stop"))));
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
    }

    private void handleStreamWithTools(SseEmitter emitter, String responseId,
                                       String modelName, ReActAgent agent, ChatRequest chatRequest) {
        int[] toolCallIndex = {0};

        agent.callStream(chatRequest, new StreamingToolListener() {
            @Override
            public void onContentToken(String token) {
                try {
                    sendContentChunk(emitter, responseId, modelName, token);
                } catch (Exception e) {
                    emitter.completeWithError(e);
                }
            }

            @Override
            public void onToolCall(ToolCall toolCall) {
                try {
                    int idx = toolCallIndex[0];
                    var callChunk = ChatCompletionResponse.Chunk.toolCallDelta(
                        responseId, modelName, idx, toolCall.id(), toolCall.toolName(), toolCall.arguments());
                    emitter.send(SseEmitter.event()
                        .data(objectMapper.writeValueAsString(callChunk)));
                } catch (Exception e) {
                    emitter.completeWithError(e);
                }
            }

            @Override
            public void onToolResult(ToolResult result) {
                try {
                    var finishChunk = ChatCompletionResponse.Chunk.finish(responseId, modelName, "tool_calls");
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
                    emitter.send(SseEmitter.event().data(
                        objectMapper.writeValueAsString(
                            ChatCompletionResponse.Chunk.finish(responseId, modelName, "stop"))));
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
    }

    private void sendContentChunk(SseEmitter emitter, String responseId,
                                  String model, String content) throws Exception {
        var chunk = ChatCompletionResponse.Chunk.delta(responseId, model, content, null);
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
