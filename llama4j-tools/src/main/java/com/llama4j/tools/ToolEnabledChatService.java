package com.llama4j.tools;

import com.llama4j.chat.ChatTemplateEngine;
import com.llama4j.chat.Message;
import com.llama4j.chat.Role;
import com.llama4j.core.ChatRequest;
import com.llama4j.core.ChatResponse;
import com.llama4j.core.ChatStreamListener;
import com.llama4j.core.ChatService;
import com.llama4j.exception.InferenceException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 支持函数调用的扩展聊天服务 — ReAct 循环实现
 *
 * <p>本服务在标准 {@link ChatService} 基础上增加了 ReAct（Reasoning + Acting）
 * 风格的工具调用循环。当模型输出中包含工具调用请求时，自动执行工具
 * 并将结果反馈给模型，直到模型产出最终的文本响应。</p>
 *
 * <h2>ReAct 循环流程</h2>
 * <pre>
 * 用户消息 → 模型推理 → 检测输出
 *                          ├── 普通文本 → 直接返回
 *                          └── 工具调用 → 执行工具 → 结果反馈 → 再次推理 → ...
 * </pre>
 *
 * <h2>工具调用检测策略</h2>
 * <ol>
 *   <li><strong>结构化输出</strong> — 专为函数调用训练的模型会生成
 *       JSON 格式的工具调用（如 ChatML function calls）</li>
 *   <li><strong>正则匹配</strong> — 兜底方案，匹配常见工具调用格式</li>
 * </ol>
 *
 * <h2>安全限制</h2>
 * <p>通过 maxToolRounds 参数限制最大工具调用轮次，防止模型陷入
 * 无限工具调用循环（例如工具 A 调用工具 B，工具 B 又调用工具 A）。</p>
 */
public class ToolEnabledChatService {

    private static final Logger LOG = LoggerFactory.getLogger(ToolEnabledChatService.class);
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    /** 工具调用正则模式 — 匹配 tool_call(name, {...}) 格式 */
    private static final Pattern TOOL_CALL_PATTERN =
        Pattern.compile("tool_call\\s*\\(\\s*\"([^\"]+)\"\\s*,\\s*(\\{.*?})\\s*\\)", Pattern.DOTALL);

    private final ChatService chatService;
    private final ToolRegistry toolRegistry;
    private final int maxToolRounds;

    /**
     * 创建支持工具调用的聊天服务。
     *
     * @param chatService   底层聊天服务
     * @param toolRegistry  工具注册中心
     */
    public ToolEnabledChatService(ChatService chatService, ToolRegistry toolRegistry) {
        this(chatService, toolRegistry, 5); // 默认最多 5 轮工具调用
    }

    /**
     * 创建支持工具调用的聊天服务，指定最大工具调用轮次。
     *
     * @param chatService    底层聊天服务
     * @param toolRegistry   工具注册中心
     * @param maxToolRounds  最大工具调用轮次
     */
    public ToolEnabledChatService(ChatService chatService, ToolRegistry toolRegistry, int maxToolRounds) {
        this.chatService = Objects.requireNonNull(chatService, "ChatService 不能为 null");
        this.toolRegistry = Objects.requireNonNull(toolRegistry, "ToolRegistry 不能为 null");
        this.maxToolRounds = maxToolRounds;
    }

    /**
     * 带工具调用的聊天补全。
     *
     * <p>执行 ReAct 循环：推理 → 检测工具调用 → 执行 → 反馈 → 再推理，
     * 直到模型产出纯文本响应或达到最大轮次。</p>
     *
     * @param request 聊天请求
     * @return 最终的聊天响应
     */
    public ChatResponse chatWithTools(ChatRequest request) {
        List<Message> conversationHistory = new ArrayList<>(request.messages());
        ChatResponse response = null;

        for (int round = 0; round < maxToolRounds; round++) {
            // 构建当前轮次的请求
            ChatRequest currentRequest = ChatRequest.builder()
                .messages(conversationHistory)
                .temperature(request.temperature())
                .maxTokens(request.maxTokens())
                .topK(request.topK())
                .topP(request.topP())
                .repeatPenalty(request.repeatPenalty())
                .seed(request.seed())
                .build();

            // 执行推理
            response = chatService.chat(currentRequest);

            // 检测是否包含工具调用
            Optional<ToolCall> toolCallOpt = parseToolCall(response.content());
            if (toolCallOpt.isEmpty()) {
                // 没有工具调用 → 返回最终响应
                return response;
            }

            // 执行工具调用
            ToolCall toolCall = toolCallOpt.get();
            LOG.info("检测到工具调用: {} (第 {}/{} 轮)", toolCall.toolName(), round + 1, maxToolRounds);

            ToolResult result;
            try {
                result = toolRegistry.execute(toolCall);
                LOG.debug("工具执行结果: {}", result.content());
            } catch (Exception e) {
                result = ToolResult.failure(toolCall.id(), "执行错误: " + e.getMessage());
                LOG.error("工具执行失败: {}", e.getMessage());
            }

            // 将工具调用和结果追加到对话历史
            conversationHistory.add(Message.assistant(response.content()));
            conversationHistory.add(Message.tool(result.content()));
        }

        // 达到最大轮次，返回最后一次响应
        LOG.warn("已达到最大工具调用轮次 ({})，返回当前响应", maxToolRounds);
        return response;
    }

    /** 获取工具注册中心 */
    public ToolRegistry getToolRegistry() {
        return toolRegistry;
    }

    /** 获取底层聊天服务 */
    public ChatService getChatService() {
        return chatService;
    }

    /* ──────────────────────────────────────────
     *  内部辅助方法
     *  ────────────────────────────────────────── */

    /**
     * 从模型输出中解析工具调用。
     *
     * <p>两种策略：</p>
     * <ol>
     *   <li>尝试 JSON 解析 — 适用于结构化工具调用输出</li>
     *   <li>正则匹配 — 兜底方案</li>
     * </ol>
     */
    private Optional<ToolCall> parseToolCall(String content) {
        if (content == null || content.isBlank()) {
            return Optional.empty();
        }

        // 策略1：JSON 解析
        try {
            JsonNode node = OBJECT_MAPPER.readTree(content);
            if (node.has("name") && node.has("arguments")) {
                String name = node.get("name").asText();
                String args = node.get("arguments").toString();
                return Optional.of(ToolCall.of(name, args));
            }
            // OpenAI 风格的 function_call
            if (node.has("function") && node.get("function").has("name")) {
                JsonNode func = node.get("function");
                String name = func.get("name").asText();
                String args = func.has("arguments") ? func.get("arguments").asText() : "{}";
                return Optional.of(ToolCall.of(name, args));
            }
        } catch (JsonProcessingException ignored) {
            // 不是 JSON，尝试正则匹配
        }

        // 策略2：正则匹配
        Matcher matcher = TOOL_CALL_PATTERN.matcher(content);
        if (matcher.find()) {
            String name = matcher.group(1);
            String args = matcher.group(2);
            return Optional.of(ToolCall.of(name, args));
        }

        return Optional.empty();
    }
}
