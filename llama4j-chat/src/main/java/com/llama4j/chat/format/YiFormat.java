package com.llama4j.chat.format;

import com.llama4j.chat.ChatFormat;
import com.llama4j.chat.Message;

import java.util.List;

/**
 * Yi 对话格式
 *
 * <p>零一万物 Yi 系列模型使用的对话格式。结构与 ChatML 相同，
 * 但注册为独立格式以支持未来可能的差异。</p>
 *
 * <h2>格式结构</h2>
 * <pre>
 * &lt;|im_start|&gt;system
 * {系统消息}&lt;|im_end|&gt;
 * &lt;|im_start|&gt;user
 * {用户消息}&lt;|im_end|&gt;
 * &lt;|im_start|&gt;assistant
 * </pre>
 *
 * <h2>适用模型</h2>
 * <ul>
 *   <li>Yi-34B</li>
 *   <li>Yi-1.5 系列</li>
 *   <li>Yi-Coder</li>
 * </ul>
 */
public final class YiFormat implements ChatFormat {

    private static final String IM_START = "<|im_start|>";
    private static final String IM_END   = "<|im_end|>";

    @Override
    public String name() {
        return "yi";
    }

    @Override
    public String render(List<Message> messages) {
        StringBuilder sb = new StringBuilder();

        for (Message msg : messages) {
            sb.append(IM_START).append(msg.role().value()).append("\n");
            sb.append(msg.content()).append(IM_END).append("\n");
        }

        sb.append(IM_START).append("assistant\n");
        return sb.toString();
    }

    @Override
    public boolean matches(String template) {
        // Yi 模型的模板通常包含 "Yi" 标识
        return template.contains("Yi") && template.contains("im_start");
    }
}
