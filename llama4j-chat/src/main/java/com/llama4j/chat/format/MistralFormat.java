package com.llama4j.chat.format;

import com.llama4j.chat.ChatFormat;
import com.llama4j.chat.Message;
import com.llama4j.chat.Role;

import java.util.List;

/**
 * Mistral / Mixtral 对话格式
 *
 * <p>Mistral AI 系列模型使用的对话格式，采用 [INST]...[/INST] 标记对
 * 包裹用户指令，助手回复直接跟在 [/INST] 后面。</p>
 *
 * <h2>格式结构</h2>
 * <pre>
 * [INST] {系统消息}
 * {用户消息} [/INST] {助手回复}&lt;/s&gt;
 * [INST] {下一轮用户消息} [/INST]
 * </pre>
 *
 * <h2>适用模型</h2>
 * <ul>
 *   <li>Mistral 7B v0.1 / v0.2 / v0.3</li>
 *   <li>Mixtral 8x7B / 8x22B</li>
 *   <li>Mistral Nemo</li>
 *   <li>Codestral</li>
 * </ul>
 *
 * <p>注意：Mistral 格式中，系统消息放在第一个 [INST] 块内，
 * 而不是作为独立的标记块。这与 Llama 3 的 header 格式不同。</p>
 */
public final class MistralFormat implements ChatFormat {

    @Override
    public String name() {
        return "mistral";
    }

    @Override
    public String render(List<Message> messages) {
        StringBuilder sb = new StringBuilder();
        boolean inInst = false; // 跟踪当前是否在 [INST] 块内

        for (int i = 0; i < messages.size(); i++) {
            Message msg = messages.get(i);

            switch (msg.role()) {
                case SYSTEM -> {
                    // 系统消息放在 [INST] 块内
                    if (!inInst) {
                        sb.append("[INST] ");
                        inInst = true;
                    }
                    sb.append(msg.content()).append("\n\n");
                }
                case USER -> {
                    // 用户消息开启 [INST] 块
                    if (!inInst) {
                        sb.append("[INST] ");
                        inInst = true;
                    }
                    sb.append(msg.content());
                    sb.append(" [/INST]");
                    inInst = false;
                }
                case ASSISTANT -> {
                    // 助手回复直接跟在 [/INST] 后面
                    sb.append(" ").append(msg.content()).append("</s>");
                }
                case TOOL -> {
                    sb.append(msg.content()).append("\n");
                }
            }
        }

        // 如果还在 [INST] 块内，关闭它
        if (inInst) {
            sb.append(" [/INST]");
        }

        return sb.toString();
    }

    @Override
    public boolean matches(String template) {
        return template.contains("[INST]") && template.contains("[/INST]");
    }
}
