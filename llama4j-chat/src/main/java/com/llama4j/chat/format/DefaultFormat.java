package com.llama4j.chat.format;

import com.llama4j.chat.ChatFormat;
import com.llama4j.chat.Message;
import com.llama4j.chat.Role;

import java.util.List;

/**
 * 默认兜底格式
 *
 * <p>当模型的 chat template 无法识别或完全缺失时使用的兜底格式。
 * 采用简单的方括号角色标签，兼容性最广。</p>
 *
 * <h2>格式结构</h2>
 * <pre>
 * [System] {系统消息}
 * [User] {用户消息}
 * [Assistant] {助手消息}
 * [Assistant]:
 * </pre>
 *
 * <p>此格式永远不会在自动检测中匹配（matches 始终返回 false），
 * 它只在所有其他格式都无法识别时作为最后的兜底方案。</p>
 */
public final class DefaultFormat implements ChatFormat {

    @Override
    public String name() {
        return "default";
    }

    @Override
    public String render(List<Message> messages) {
        StringBuilder sb = new StringBuilder();

        for (Message msg : messages) {
            String label = switch (msg.role()) {
                case SYSTEM    -> "System";
                case USER      -> "User";
                case ASSISTANT -> "Assistant";
                case TOOL      -> "Tool";
            };
            sb.append("[").append(label).append("] ").append(msg.content()).append("\n");
        }

        sb.append("[Assistant]: ");
        return sb.toString();
    }

    @Override
    public boolean matches(String template) {
        // 兜底格式永远不主动匹配
        return false;
    }
}
