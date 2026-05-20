package com.llama4j.sample;

import com.llama4j.chat.Role;
import com.llama4j.core.ChatRequest;
import com.llama4j.core.ChatResponse;
import com.llama4j.core.ChatService;
import com.llama4j.tools.ToolEnabledChatService;
import com.llama4j.tools.annotation.Tool;
import com.llama4j.tools.annotation.ToolParam;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.*;

/**
 * 示例 REST 控制器 — 演示直接使用 ChatService
 *
 * <p>展示如何在 Spring MVC 控制器中直接使用 llama4j ChatService，
 * 绕过 OpenAI 兼容端点。提供更简单的 API，适合不需要
 * OpenAI 兼容性的应用。</p>
 *
 * <h2>端点列表</h2>
 * <ul>
 *   <li>{@code GET /api/chat?message=你好} — 简单聊天</li>
 *   <li>{@code POST /api/chat} — 带参数的聊天</li>
 * </ul>
 *
 * <h2>工具注册</h2>
 * <p>本控制器中的 {@code getCurrentTime} 和 {@code calculate} 方法
 * 使用 {@code @Tool} 注解标记，在构造时自动注册到 ToolRegistry。
 * LLM 可以在对话中自动调用这些工具。</p>
 */
@RestController
@RequestMapping("/api/chat")
public class ChatController {

    private static final Logger LOG = LoggerFactory.getLogger(ChatController.class);

    private final ToolEnabledChatService chatService;

    public ChatController(ToolEnabledChatService chatService) {
        this.chatService = chatService;
        // 扫描本对象中的 @Tool 注解方法并注册
        chatService.getToolRegistry().scanAndRegister(this);
    }

    /**
     * 简单聊天端点。
     *
     * <p>示例：{@code GET /api/chat?message=你好}</p>
     */
    @GetMapping
    public String chat(@RequestParam String message) {
        LOG.info("收到聊天消息: {}", message);

        ChatRequest request = ChatRequest.builder()
            .addMessage(Role.USER, message)
            .build();

        ChatResponse response = chatService.chatWithTools(request);
        return response.content();
    }

    /**
     * 带参数的聊天端点。
     *
     * <p>示例：{@code POST /api/chat}</p>
     * <pre>{@code
     * {
     *   "systemPrompt": "你是一个数学老师",
     *   "userMessage": "1+1等于几？",
     *   "temperature": 0.3
     * }
     * }</pre>
     */
    @PostMapping
    public String chatWithParams(@RequestBody ChatBody body) {
        LOG.info("收到参数化聊天请求: {}", body.userMessage());

        ChatRequest.Builder builder = ChatRequest.builder()
            .addMessage(Role.USER, body.userMessage());

        if (body.systemPrompt() != null && !body.systemPrompt().isBlank()) {
            builder.system(body.systemPrompt());
        }

        if (body.temperature() != null) {
            builder.temperature(body.temperature());
        }

        ChatResponse response = chatService.chatWithTools(builder.build());
        return response.content();
    }

    /* ──────────────────────────────────────────
     *  工具方法 — 使用 @Tool 注解自动注册
     *  ────────────────────────────────────────── */

    /**
     * 获取当前时间工具。
     *
     * <p>LLM 可以在对话中调用此工具获取实时时间信息，
     * 弥补模型知识截止日期的局限。</p>
     */
    @Tool(name = "get_current_time", description = "获取指定时区的当前时间")
    public String getCurrentTime(
        @ToolParam(description = "时区 ID，如 'Asia/Shanghai'", type = "string") String timezone
    ) {
        return java.time.ZonedDateTime.now(java.time.ZoneId.of(timezone)).toString();
    }

    /**
     * 简易计算器工具。
     *
     * <p>LLM 可以调用此工具执行算术运算，弥补模型在精确计算上的不足。</p>
     */
    @Tool(name = "calculate", description = "执行简单的算术运算")
    public String calculate(
        @ToolParam(description = "数学表达式，如 '3 + 5'", type = "string") String expression
    ) {
        try {
            return "计算结果: " + evaluateSimple(expression);
        } catch (Exception e) {
            return "计算错误: " + e.getMessage();
        }
    }

    /** 简易表达式求值器（仅用于演示，生产环境应使用专业数学解析器） */
    private double evaluateSimple(String expr) {
        String[] parts = expr.trim().split("\\s+");
        if (parts.length != 3) throw new IllegalArgumentException("格式: 'a 运算符 b'");
        double a = Double.parseDouble(parts[0]);
        double b = Double.parseDouble(parts[2]);
        return switch (parts[1]) {
            case "+" -> a + b;
            case "-" -> a - b;
            case "*" -> a * b;
            case "/" -> a / b;
            default -> throw new IllegalArgumentException("未知运算符: " + parts[1]);
        };
    }

    /** 请求体 */
    record ChatBody(
        String systemPrompt,
        String userMessage,
        Float temperature
    ) {}
}
