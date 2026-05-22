package com.llama4j.core;

import com.llama4j.chat.Message;
import com.llama4j.chat.Role;
import com.llama4j.native_.GrammarConstraint;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * 聊天补全请求 — 不可变对象
 *
 * <p>封装了对话消息列表和生成参数，是 {@link ChatService#chat} 方法的输入。
 * 使用 Builder 模式构建，支持流式 API 风格的消息添加。</p>
 *
 * <h2>设计说明</h2>
 * <p>请求对象采用不可变设计（record），一旦创建就不能修改。
 * 这确保了在异步或并发场景下，请求对象的状态不会被意外改变。
 * Builder 内部使用可变列表收集消息，在 build() 时才创建不可变副本。</p>
 *
 * <h2>使用示例</h2>
 * <pre>{@code
 * ChatRequest request = ChatRequest.builder()
 *     .system("你是一个有帮助的AI助手。")     // 添加系统提示
 *     .addMessage(Role.USER, "你好！")         // 添加用户消息
 *     .temperature(0.7f)                       // 设置温度
 *     .maxTokens(1024)                         // 限制生成长度
 *     .build();
 * }</pre>
 *
 * @param messages      对话消息列表（至少包含一条消息）
 * @param temperature   采样温度（默认 0.7）
 * @param maxTokens     最大生成 token 数（默认 2048）
 * @param topK          Top-K 采样参数（默认 40）
 * @param topP          Top-P 核采样参数（默认 0.9）
 * @param repeatPenalty 重复惩罚系数（默认 1.1）
 * @param seed          随机种子，-1 为不确定（默认 -1）
 * @param stopTokens    停止 token 列表，生成这些 token 时立即停止
 */
public record ChatRequest(
    List<Message> messages,
    float temperature,
    int maxTokens,
    int topK,
    float topP,
    float repeatPenalty,
    long seed,
    List<String> stopTokens,
    GrammarConstraint grammar,
    boolean jsonMode
) {

    /**
     * 紧凑构造器 — 参数校验与防御性拷贝
     *
     * <p>关键设计点：</p>
     * <ul>
     *   <li>消息列表通过 {@code List.copyOf} 创建不可变副本，
     *       防止外部代码在创建后修改列表</li>
     *   <li>强制要求至少一条消息，避免空请求</li>
     * </ul>
     */
    public ChatRequest {
        Objects.requireNonNull(messages, "消息列表不能为 null");
        if (messages.isEmpty()) {
            throw new IllegalArgumentException("消息列表不能为空");
        }
        messages = List.copyOf(messages);
        if (stopTokens == null) {
            stopTokens = List.of();
        }
        if (grammar != null && grammar.isClosed()) {
            throw new IllegalArgumentException("GrammarConstraint 已关闭，不能使用");
        }
    }

    /** 创建新的 Builder */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * ChatRequest 的流畅构建器
     *
     * <p>提供多种消息添加方式，适配不同的使用习惯：</p>
     * <ul>
     *   <li>{@code messages(List)} — 批量设置消息列表</li>
     *   <li>{@code addMessage(Message)} — 添加单个 Message 对象</li>
     *   <li>{@code addMessage(Role, String)} — 通过角色和内容添加</li>
     *   <li>{@code system(String)} — 便捷方法，在列表头部插入系统消息</li>
     * </ul>
     */
    public static final class Builder {
        private final List<Message> messages = new ArrayList<>();
        private float temperature = 0.7f;
        private int maxTokens = 2048;
        private int topK = 40;
        private float topP = 0.9f;
        private float repeatPenalty = 1.1f;
        private long seed = -1L;
        private List<String> stopTokens = List.of();
        private GrammarConstraint grammar = null;
        private boolean jsonMode = false;

        private Builder() {}

        /** 批量设置消息列表（替换已有消息） */
        public Builder messages(List<Message> messages) {
            this.messages.clear();
            this.messages.addAll(messages);
            return this;
        }

        /** 添加单个 Message 对象 */
        public Builder addMessage(Message message) {
            this.messages.add(message);
            return this;
        }

        /** 通过角色和内容添加消息 */
        public Builder addMessage(Role role, String content) {
            this.messages.add(new Message(role, content));
            return this;
        }

        /**
         * 在列表头部插入系统提示消息。
         *
         * <p>系统消息定义了模型的行为准则，通常放在对话的最前面。
         * 此方法会将系统消息插入到列表头部，即使之前已有消息。</p>
         */
        public Builder system(String content) {
            this.messages.removeIf(m -> m.role() == Role.SYSTEM);
            this.messages.add(0, new Message(Role.SYSTEM, content));
            return this;
        }

        public Builder temperature(float temperature)    { this.temperature = temperature; return this; }
        public Builder maxTokens(int maxTokens)          { this.maxTokens = maxTokens; return this; }
        public Builder topK(int topK)                    { this.topK = topK; return this; }
        public Builder topP(float topP)                  { this.topP = topP; return this; }
        public Builder repeatPenalty(float repeatPenalty){ this.repeatPenalty = repeatPenalty; return this; }
        public Builder seed(long seed)                   { this.seed = seed; return this; }
        public Builder stopTokens(List<String> stopTokens) { this.stopTokens = stopTokens != null ? stopTokens : List.of(); return this; }
        public Builder grammar(GrammarConstraint grammar)  { this.grammar = grammar; return this; }
        public Builder jsonMode(boolean jsonMode)          { this.jsonMode = jsonMode; return this; }

        /** 构建不可变的 ChatRequest 实例 */
        public ChatRequest build() {
            return new ChatRequest(
                Collections.unmodifiableList(new ArrayList<>(messages)),
                temperature, maxTokens, topK, topP, repeatPenalty, seed, stopTokens,
                grammar, jsonMode);
        }
    }
}
