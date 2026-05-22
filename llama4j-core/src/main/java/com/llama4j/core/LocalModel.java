package com.llama4j.core;

import com.llama4j.chat.ChatTemplateEngine;
import com.llama4j.native_.LlamaContext;
import com.llama4j.native_.ModelParams;

import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;

/**
 * 本地推理模型 — llama.cpp JNI 推理的 Model 接口实现
 *
 * <p>封装完整的本地推理管线：LlamaContext + ChatTemplateEngine + ChatService。</p>
 *
 * <h2>使用示例</h2>
 * <pre>{@code
 * Model local = LocalModel.fromFile("/models/qwen2.5-7b-q4_k_m.gguf");
 * ChatResponse response = local.chat(ChatRequest.builder()
 *     .addMessage(Role.USER, "你好")
 *     .build());
 * }</pre>
 */
public final class LocalModel implements Model {

    private final ChatService chatService;
    private final String modelDescription;

    private LocalModel(ChatService chatService, String modelDescription) {
        this.chatService = chatService;
        this.modelDescription = modelDescription;
    }

    @Override
    public ChatResponse chat(ChatRequest request) {
        return chatService.chat(request);
    }

    @Override
    public CompletableFuture<ChatResponse> chatStream(ChatRequest request, ChatStreamListener listener) {
        return chatService.chatStream(request, listener);
    }

    @Override
    public String getModelName() {
        return modelDescription;
    }

    /** 关闭模型，释放原生资源 */
    public void close() {
        chatService.shutdown();
    }

    /* ──────────────────────────────────────────
     *  工厂方法
     *  ────────────────────────────────────────── */

    /**
     * 从模型文件创建本地模型（使用默认参数）。
     */
    public static LocalModel fromFile(String modelPath) {
        return fromFile(modelPath, ModelParams.DEFAULT);
    }

    /**
     * 从模型文件创建本地模型（自定义参数）。
     */
    public static LocalModel fromFile(String modelPath, ModelParams params) {
        LlamaContext context = new LlamaContext(modelPath, params);
        ChatTemplateEngine engine = new ChatTemplateEngine();
        ChatService chatService = new ChatService(context, engine);
        String desc = context.getModelDescription();
        return new LocalModel(chatService, desc);
    }

    /**
     * 从已有的 LlamaContext 创建。
     */
    public static LocalModel fromContext(LlamaContext context) {
        Objects.requireNonNull(context, "LlamaContext 不能为 null");
        ChatTemplateEngine engine = new ChatTemplateEngine();
        ChatService chatService = new ChatService(context, engine);
        String desc = context.getModelDescription();
        return new LocalModel(chatService, desc);
    }
}
