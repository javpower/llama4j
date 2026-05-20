package com.llama4j.tools;

import java.util.Objects;

/**
 * LLM 发起的工具调用请求
 *
 * <p>当 LLM 决定调用工具时，它会生成一个结构化输出，包含工具名称和参数。
 * 本记录捕获该请求，用于 {@link ToolRegistry} 执行。</p>
 *
 * <h2>工具调用流程</h2>
 * <pre>
 * LLM 输出 → 解析为 ToolCall → ToolRegistry.execute() → ToolResult
 * </pre>
 *
 * @param id        工具调用的唯一标识符（用于匹配结果）
 * @param toolName  要调用的工具名称
 * @param arguments JSON 格式的参数字符串
 */
public record ToolCall(
    String id,
    String toolName,
    String arguments
) {

    public ToolCall {
        Objects.requireNonNull(id, "调用 ID 不能为 null");
        Objects.requireNonNull(toolName, "工具名称不能为 null");
        Objects.requireNonNull(arguments, "参数不能为 null（无参数时使用空字符串）");
    }

    /** 创建一个自动生成 ID 的工具调用 */
    public static ToolCall of(String toolName, String arguments) {
        return new ToolCall(java.util.UUID.randomUUID().toString(), toolName, arguments);
    }
}
