package com.llama4j.core;

import com.llama4j.chat.Message;
import com.llama4j.chat.Role;

import java.util.Objects;

/**
 * 聊天补全响应 — 不可变对象
 *
 * <p>包含模型生成的文本内容、token 使用统计和推理性能指标。
 * 此对象由 {@link ChatService#chat} 方法返回，提供了完整的
 * 生成结果和性能数据。</p>
 *
 * <h2>字段说明</h2>
 * <ul>
 *   <li>{@code content} — 模型生成的纯文本内容</li>
 *   <li>{@code message} — 包含角色（ASSISTANT）和内容的完整消息对象，
 *       可直接追加到对话历史中</li>
 *   <li>{@code promptTokens} — 输入提示词消耗的 token 数量</li>
 *   <li>{@code completionTokens} — 模型生成的 token 数量</li>
 *   <li>{@code totalTokens} — 总 token 数（提示 + 补全）</li>
 *   <li>{@code tokensPerSecond} — 生成速度（token/秒），衡量推理性能</li>
 *   <li>{@code latencyMs} — 总推理延迟（毫秒），从开始到结束的耗时</li>
 * </ul>
 *
 * @param content          生成的文本内容
 * @param message          完整的助手消息（包含角色）
 * @param promptTokens     提示词 token 数
 * @param completionTokens 补全 token 数
 * @param totalTokens      总 token 数
 * @param tokensPerSecond  生成速度（token/秒）
 * @param latencyMs        推理延迟（毫秒）
 */
public record ChatResponse(
    String content,
    Message message,
    int promptTokens,
    int completionTokens,
    int totalTokens,
    double tokensPerSecond,
    long latencyMs
) {

    public ChatResponse {
        Objects.requireNonNull(content, "内容不能为 null");
        Objects.requireNonNull(message, "消息不能为 null");
    }

    /**
     * 创建简单响应（仅内容和 token 计数，无性能指标）。
     *
     * <p>适用于不需要性能监控的简单场景。</p>
     *
     * @param content          生成的文本
     * @param promptTokens     提示词 token 数
     * @param completionTokens 补全 token 数
     */
    public static ChatResponse of(String content, int promptTokens, int completionTokens) {
        Message msg = new Message(Role.ASSISTANT, content);
        int total = promptTokens + completionTokens;
        return new ChatResponse(content, msg, promptTokens, completionTokens, total, 0.0, 0);
    }

    /**
     * 创建带性能指标的完整响应。
     *
     * @param content          生成的文本
     * @param promptTokens     提示词 token 数
     * @param completionTokens 补全 token 数
     * @param tokensPerSecond  生成速度（token/秒）
     * @param latencyMs        推理延迟（毫秒）
     */
    public static ChatResponse of(String content, int promptTokens, int completionTokens,
                                  double tokensPerSecond, long latencyMs) {
        Message msg = new Message(Role.ASSISTANT, content);
        int total = promptTokens + completionTokens;
        return new ChatResponse(content, msg, promptTokens, completionTokens, total, tokensPerSecond, latencyMs);
    }
}
