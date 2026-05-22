package com.llama4j.core;

import java.util.concurrent.CompletableFuture;

/**
 * 供应商无关的模型接口 — 本地推理与云端 API 的统一抽象
 *
 * <p>所有模型实现（本地 LlamaContext、OpenAI、DashScope 等）都实现此接口，
 * 使上层的 ReAct Agent、Spring Boot 端点等代码与具体供应商解耦。</p>
 */
public interface Model {

    /**
     * 同步聊天补全。
     *
     * @param request 聊天请求
     * @return 聊天响应
     */
    ChatResponse chat(ChatRequest request);

    /**
     * 流式聊天补全。
     *
     * @param request  聊天请求
     * @param listener 流式监听器
     * @return CompletableFuture，推理完成时完成
     */
    CompletableFuture<ChatResponse> chatStream(ChatRequest request, ChatStreamListener listener);

    /**
     * 获取模型名称（用于日志和调试）。
     *
     * @return 模型名称
     */
    String getModelName();
}
