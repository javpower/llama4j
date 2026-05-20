package com.llama4j.chat;

import java.util.Objects;

/**
 * 单条聊天消息 — 不可变对象
 *
 * <p>每条消息由 {@link Role}（角色）和文本内容组成。消息是对话模板系统
 * 的基本单元——它们被组装成列表，然后通过 {@link com.llama4j.chat.ChatFormat}
 * 渲染为模型可理解的最终提示词字符串。</p>
 *
 * <h2>消息在渲染管线中的位置</h2>
 * <pre>
 * List&lt;Message&gt; → ChatFormat.render() → 提示词字符串 → 分词 → 推理
 * </pre>
 *
 * <h2>使用示例</h2>
 * <pre>{@code
 * Message sys = Message.system("你是一个有帮助的AI助手。");
 * Message usr = Message.user("什么是量子计算？");
 * Message ast = Message.assistant("量子计算是利用量子力学原理...");
 * }</pre>
 *
 * @param role    消息发送者的角色
 * @param content 消息的文本内容
 */
public record Message(Role role, String content) {

    public Message {
        Objects.requireNonNull(role, "角色不能为 null");
        Objects.requireNonNull(content, "内容不能为 null");
    }

    /** 便捷工厂方法：创建系统消息 */
    public static Message system(String content) {
        return new Message(Role.SYSTEM, content);
    }

    /** 便捷工厂方法：创建用户消息 */
    public static Message user(String content) {
        return new Message(Role.USER, content);
    }

    /** 便捷工厂方法：创建助手消息 */
    public static Message assistant(String content) {
        return new Message(Role.ASSISTANT, content);
    }

    /** 便捷工厂方法：创建工具结果消息 */
    public static Message tool(String content) {
        return new Message(Role.TOOL, content);
    }
}
