package com.llama4j.chat.format;

import com.llama4j.chat.ChatFormat;
import com.llama4j.chat.Message;

import java.util.List;

/**
 * Phi-3 对话格式
 *
 * <p>Microsoft Phi-3 系列模型使用的对话格式，采用尖括号标记系统。</p>
 *
 * <h2>格式结构</h2>
 * <pre>
 * &lt;|system|&gt;
 * {系统消息}&lt;|end|&gt;
 * &lt;|user|&gt;
 * {用户消息}&lt;|end|&gt;
 * &lt;|assistant|&gt;
 * {助手回复}&lt;|end|&gt;
 * </pre>
 *
 * <h2>适用模型</h2>
 * <ul>
 *   <li>Microsoft Phi-3 mini / small / medium</li>
 *   <li>Phi-3.5 系列</li>
 * </ul>
 */
public final class Phi3Format implements ChatFormat {

    @Override
    public String name() {
        return "phi3";
    }

    @Override
    public String render(List<Message> messages) {
        StringBuilder sb = new StringBuilder();

        for (Message msg : messages) {
            sb.append("<|").append(msg.role().value()).append("|>\n");
            sb.append(msg.content()).append("<|end|>\n");
        }

        // 助手回复起始标记
        sb.append("<|assistant|").append(">\n");
        return sb.toString();
    }

    @Override
    public boolean matches(String template) {
        return template.contains("<|end|>") && template.contains("user");
    }
}
