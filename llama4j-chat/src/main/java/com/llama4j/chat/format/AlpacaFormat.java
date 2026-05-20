package com.llama4j.chat.format;

import com.llama4j.chat.ChatFormat;
import com.llama4j.chat.Message;
import com.llama4j.chat.Role;

import java.util.List;

/**
 * Alpaca 对话格式
 *
 * <p>Alpaca 模型使用的指令-响应格式，采用 ### 标记分隔不同部分。
 * 这是最早的开源指令微调格式之一，至今仍被广泛使用。</p>
 *
 * <h2>格式结构</h2>
 * <pre>
 * Below is an instruction that describes a task. Write a response.
 *
 * ### Instruction:
 * {用户指令}
 *
 * ### Response:
 * {助手回复}
 * </pre>
 *
 * <h2>适用模型</h2>
 * <ul>
 *   <li>Alpaca 7B / 13B</li>
 *   <li>OpenAssistant</li>
 *   <li>基于 Alpaca 格式的微调模型</li>
 * </ul>
 */
public final class AlpacaFormat implements ChatFormat {

    /** 默认系统提示词 */
    private static final String DEFAULT_SYSTEM =
        "Below is an instruction that describes a task. Write a response that appropriately completes the request.";

    @Override
    public String name() {
        return "alpaca";
    }

    @Override
    public String render(List<Message> messages) {
        StringBuilder sb = new StringBuilder();

        // 提取系统提示词（如果有）
        String systemPrompt = DEFAULT_SYSTEM;
        for (Message msg : messages) {
            if (msg.role() == Role.SYSTEM) {
                systemPrompt = msg.content();
                break;
            }
        }

        sb.append(systemPrompt).append("\n\n");

        for (Message msg : messages) {
            if (msg.role() == Role.USER) {
                sb.append("### Instruction:\n").append(msg.content()).append("\n\n");
            } else if (msg.role() == Role.ASSISTANT) {
                sb.append("### Response:\n").append(msg.content()).append("\n\n");
            }
        }

        sb.append("### Response:\n");
        return sb.toString();
    }

    @Override
    public boolean matches(String template) {
        return template.contains("### Instruction:") || template.contains("### Response:");
    }
}
