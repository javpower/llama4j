package com.llama4j.chat.format;

import com.llama4j.chat.ChatFormat;
import com.llama4j.chat.Message;
import com.llama4j.chat.Role;

import java.util.List;

/**
 * Vicuna 对话格式
 *
 * <p>Vicuna 模型使用的对话格式，采用纯文本角色标签，
 * 没有特殊的 XML 风格标记。助手回复以 &lt;/s&gt; 结束。</p>
 *
 * <h2>格式结构</h2>
 * <pre>
 * {系统消息}
 *
 * USER: {用户消息}
 * ASSISTANT: {助手回复}&lt;/s&gt;
 * ASSISTANT:
 * </pre>
 *
 * <h2>适用模型</h2>
 * <ul>
 *   <li>Vicuna 7B / 13B / 33B</li>
 *   <li>LongChat</li>
 *   <li>基于 Vicuna 格式的微调模型</li>
 * </ul>
 */
public final class VicunaFormat implements ChatFormat {

    @Override
    public String name() {
        return "vicuna";
    }

    @Override
    public String render(List<Message> messages) {
        StringBuilder sb = new StringBuilder();

        for (Message msg : messages) {
            switch (msg.role()) {
                case SYSTEM    -> sb.append(msg.content()).append("\n\n");
                case USER      -> sb.append("USER: ").append(msg.content()).append("\n");
                case ASSISTANT -> sb.append("ASSISTANT: ").append(msg.content()).append("</s>\n");
                case TOOL      -> sb.append("TOOL: ").append(msg.content()).append("\n");
            }
        }

        sb.append("ASSISTANT: ");
        return sb.toString();
    }

    @Override
    public boolean matches(String template) {
        return template.contains("USER:") && template.contains("ASSISTANT:");
    }
}
