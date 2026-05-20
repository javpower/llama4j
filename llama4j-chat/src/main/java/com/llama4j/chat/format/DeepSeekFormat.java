package com.llama4j.chat.format;

import com.llama4j.chat.ChatFormat;
import com.llama4j.chat.Message;
import com.llama4j.chat.Role;

import java.util.List;

/**
 * DeepSeek 对话格式
 *
 * <p>DeepSeek 系列模型使用的对话格式，采用 Unicode 下划线特殊标记
 * 和 User/Assistant 角色标签。</p>
 *
 * <h2>格式结构</h2>
 * <pre>
 * &lt;｜begin▁of▁sentence｜&gt;User: {用户消息}
 * Assistant: {助手回复}&lt;｜end▁of▁sentence｜&gt;
 * User: {下一轮消息}
 * Assistant:
 * </pre>
 *
 * <h2>适用模型</h2>
 * <ul>
 *   <li>DeepSeek Coder</li>
 *   <li>DeepSeek V2 / V2.5</li>
 *   <li>DeepSeek-MoE</li>
 * </ul>
 *
 * <p>注意：DeepSeek 格式使用 Unicode 特殊字符（▁ = 下划线变体），
 * 这些字符在 GGUF 元数据中以 UTF-8 编码存储。</p>
 */
public final class DeepSeekFormat implements ChatFormat {

    private static final String BOS = "<｜begin▁of▁sentence｜>";
    private static final String EOS = "<｜end▁of▁sentence｜>";

    @Override
    public String name() {
        return "deepseek";
    }

    @Override
    public String render(List<Message> messages) {
        StringBuilder sb = new StringBuilder();
        sb.append(BOS);

        for (Message msg : messages) {
            switch (msg.role()) {
                case SYSTEM    -> sb.append(msg.content()).append("\n\n");
                case USER      -> sb.append("User: ").append(msg.content()).append("\n");
                case ASSISTANT -> sb.append("Assistant: ").append(msg.content()).append(EOS);
                case TOOL      -> sb.append("Tool: ").append(msg.content()).append("\n");
            }
        }

        sb.append("Assistant: ");
        return sb.toString();
    }

    @Override
    public boolean matches(String template) {
        return template.contains("begin▁of▁sentence")
            || (template.contains("User:") && template.contains("Assistant:"));
    }
}
