package com.llama4j.chat;

/**
 * 对话参与者角色枚举
 *
 * <p>定义了标准 LLM 对话中的四种角色，对应主流 API（OpenAI、Anthropic 等）
 * 的消息角色分类。这些角色在对话模板中被用于格式化消息——不同角色
 * 的消息会被不同的特殊标记包围。</p>
 *
 * <h2>角色说明</h2>
 * <ul>
 *   <li>{@code SYSTEM} — 系统指令，定义模型的行为准则和约束。
 *       通常放在对话最前面，对模型的所有回复生效。</li>
 *   <li>{@code USER} — 人类用户的消息，即用户的提问或指令。</li>
 *   <li>{@code ASSISTANT} — AI 助手的回复，模型生成的响应。</li>
 *   <li>{@code TOOL} — 工具/函数调用的返回结果，反馈给模型继续推理。</li>
 * </ul>
 *
 * <h2>在模板中的使用</h2>
 * <pre>
 * Llama 3:  &lt;|start_header_id|&gt;user&lt;|end_header_id|&gt;  ← role.value()
 * ChatML:   &lt;|im_start|&gt;user                            ← role.value()
 * Gemma:    &lt;start_of_turn&gt;user                          ← role.value()
 * </pre>
 */
public enum Role {

    /** 系统级指令，引导模型行为 */
    SYSTEM("system"),

    /** 人类用户的消息 */
    USER("user"),

    /** AI 助手的回复 */
    ASSISTANT("assistant"),

    /** 工具/函数调用结果 */
    TOOL("tool");

    /** 小写字符串表示，用于对话模板中的角色标记 */
    private final String value;

    Role(String value) {
        this.value = value;
    }

    /** 获取角色的小写字符串表示（如 "system"、"user"） */
    public String value() {
        return value;
    }

    /**
     * 从字符串解析角色。
     *
     * @param value 角色字符串（如 "system"、"user"）
     * @return 对应的 Role 枚举值
     * @throws IllegalArgumentException 如果字符串不匹配任何角色
     */
    public static Role fromValue(String value) {
        for (Role role : values()) {
            if (role.value.equalsIgnoreCase(value)) {
                return role;
            }
        }
        throw new IllegalArgumentException("未知角色: " + value);
    }
}
