package com.llama4j.tools;

import java.util.Objects;

/**
 * 工具执行结果 — 不可变对象
 *
 * <p>封装工具调用的执行结果，包括对应的调用 ID、结果内容和成功/失败状态。
 * 结果会被反馈给 LLM，让它基于工具返回的信息继续生成。</p>
 *
 * @param toolCallId 对应的 {@link ToolCall} 的 ID
 * @param content    结果内容字符串
 * @param success    是否执行成功
 */
public record ToolResult(
    String toolCallId,
    String content,
    boolean success
) {

    public ToolResult {
        Objects.requireNonNull(toolCallId, "调用 ID 不能为 null");
        Objects.requireNonNull(content, "内容不能为 null");
    }

    /** 创建成功结果 */
    public static ToolResult success(String toolCallId, String content) {
        return new ToolResult(toolCallId, content, true);
    }

    /** 创建失败结果 */
    public static ToolResult failure(String toolCallId, String errorMessage) {
        return new ToolResult(toolCallId, errorMessage, false);
    }
}
