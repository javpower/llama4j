package com.llama4j.exception;

/**
 * 工具未找到异常
 *
 * <p>当 LLM 请求调用的工具名称在注册表中不存在时抛出。</p>
 */
public class ToolNotFoundException extends Llama4jException {

    /** 错误码常量 */
    public static final String CODE = "TOOL_NOT_FOUND";

    /** 工具名称 */
    private final String toolName;

    /** @param toolName 未找到的工具名称 */
    public ToolNotFoundException(String toolName) {
        super(CODE, "工具未找到: " + toolName);
        this.toolName = toolName;
    }

    /** @return 工具名称 */
    public String getToolName() {
        return toolName;
    }
}
