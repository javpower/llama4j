package com.llama4j.chat.format;

import com.llama4j.chat.ChatFormat;
import com.llama4j.chat.Message;

import java.util.List;

/**
 * ChatML 对话格式
 *
 * <p>ChatML 是一种广泛使用的对话格式标准，最初由 OpenAI 提出。
 * 采用 im_start / im_end 标记对来界定每条消息的边界。</p>
 *
 * <h2>格式结构</h2>
 * <pre>
 * &lt;|im_start|&gt;system
 * {系统消息}&lt;|im_end|&gt;
 * &lt;|im_start|&gt;user
 * {用户消息}&lt;|im_end|&gt;
 * &lt;|im_start|&gt;assistant
 * {助手回复}&lt;|im_end|&gt;
 * </pre>
 *
 * <h2>适用模型</h2>
 * <ul>
 *   <li>Qwen 2 / 2.5 系列</li>
 *   <li>Yi 系列</li>
 *   <li>DeepSeek V2</li>
 *   <li>其他使用 ChatML 格式的模型</li>
 * </ul>
 */
public final class ChatMLFormat implements ChatFormat {

    private static final String IM_START = "<|im_start|>";
    private static final String IM_END   = "<|im_end|>";

    @Override
    public String name() {
        return "chatml";
    }

    @Override
    public String render(List<Message> messages) {
        StringBuilder sb = new StringBuilder();

        for (Message msg : messages) {
            sb.append(IM_START).append(msg.role().value()).append("\n");
            sb.append(msg.content()).append(IM_END).append("\n");
        }

        // 助手回复起始标记（不加 im_end，等待模型生成）
        sb.append(IM_START).append("assistant\n");
        return sb.toString();
    }

    @Override
    public boolean matches(String template) {
        return template.contains("<|im_start|>") || template.contains("im_start");
    }
}
