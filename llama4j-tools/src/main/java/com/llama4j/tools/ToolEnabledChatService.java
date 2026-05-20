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
import java.util.stream.Collectors;

/**
 * 支持函数调用的扩展聊天服务 — ReAct 循环实现
 *
 * <p>本服务在标准 {@link ChatService} 基础上增加了 ReAct（Reasoning + Acting）
 * 风格的工具调用循环。当模型输出中包含工具调用请求时，自动执行工具
 * 并将结果反馈给模型，直到模型产出最终的文本响应。</p>
 *
 * <h2>ReAct 循环流程</h2>
 * <pre>
 * 用户消息 → 注入工具描述 → 模型推理 → 检测输出
 *                                       ├── 普通文本 → 直接返回
 *                                       └── 工具调用 → 执行工具 → 结果反馈 → 再次推理 → ...
 * </pre>
 *
 * <h2>小模型优化</h2>
 * <p>自动将注册工具的名称、描述、参数和调用示例注入到对话的系统提示中，
 * 使小参数模型（1.5B~3B）也能可靠地进行工具调用。注入内容包括：</p>
 * <ul>
 *   <li>所有可用工具的名称和描述</li>
 *   <li>每个工具的参数列表、类型和说明</li>
 *   <li>标准调用格式说明</li>
 *   <li>针对每个工具的具体 Few-shot 示例</li>
 * </ul>
 *
 * <h2>安全限制</h2>
 * <p>通过 maxToolRounds 参数限制最大工具调用轮次，防止模型陷入
 * 无限工具调用循环。</p>
 */
public class ToolEnabledChatService {

    private static final Logger LOG = LoggerFactory.getLogger(ToolEnabledChatService.class);
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    /** 工具调用正则模式 — 匹配 tool_call(name, ...) 的开头部分 */
    private static final Pattern TOOL_CALL_PREFIX =
        Pattern.compile("tool_call\\s*\\(\\s*\"([^\"]+)\"\\s*,\\s*");

    private final ChatService chatService;
    private final ToolRegistry toolRegistry;
    private final int maxToolRounds;

    /**
     * 创建支持工具调用的聊天服务。
     */
    public ToolEnabledChatService(ChatService chatService, ToolRegistry toolRegistry) {
        this(chatService, toolRegistry, 5);
    }

    /**
     * 创建支持工具调用的聊天服务，指定最大工具调用轮次。
     */
    public ToolEnabledChatService(ChatService chatService, ToolRegistry toolRegistry, int maxToolRounds) {
        this.chatService = Objects.requireNonNull(chatService, "ChatService 不能为 null");
        this.toolRegistry = Objects.requireNonNull(toolRegistry, "ToolRegistry 不能为 null");
        if (maxToolRounds <= 0) throw new IllegalArgumentException("maxToolRounds 必须为正数");
        this.maxToolRounds = maxToolRounds;
    }

    /**
     * 带工具调用的聊天补全。
     *
     * <p>执行 ReAct 循环：注入工具描述 → 推理 → 检测工具调用 → 执行 → 反馈 → 再推理，
     * 直到模型产出纯文本响应或达到最大轮次。</p>
     */
    public ChatResponse chatWithTools(ChatRequest request) {
        List<Message> conversationHistory = new ArrayList<>(request.messages());

        // 如果有注册工具，注入工具描述到系统提示
        String toolPrompt = buildToolSystemPrompt();
        if (!toolPrompt.isEmpty()) {
            injectToolPrompt(conversationHistory, toolPrompt);
        }

        ChatResponse response = null;

        for (int round = 0; round < maxToolRounds; round++) {
            ChatRequest currentRequest = ChatRequest.builder()
                .messages(conversationHistory)
                .temperature(request.temperature())
                .maxTokens(request.maxTokens())
                .topK(request.topK())
                .topP(request.topP())
                .repeatPenalty(request.repeatPenalty())
                .seed(request.seed())
                .stopTokens(request.stopTokens())
                .build();

            response = chatService.chat(currentRequest);

            Optional<ToolCall> toolCallOpt = parseToolCall(response.content());
            if (toolCallOpt.isEmpty()) {
                return response;
            }

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

            conversationHistory.add(Message.assistant(response.content()));
            conversationHistory.add(Message.tool(result.content()));
        }

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
     *  工具提示注入 — 小模型优化
     *  ────────────────────────────────────────── */

    /**
     * 构建工具描述系统提示。
     *
     * <p>将所有注册工具的名称、描述、参数和 Few-shot 示例
     * 组织成清晰的系统提示文本，帮助小模型理解如何使用工具。</p>
     */
    private String buildToolSystemPrompt() {
        Collection<ToolDefinition> tools = toolRegistry.getDefinitions();
        if (tools.isEmpty()) return "";

        StringBuilder sb = new StringBuilder();
        sb.append("\n\n# 可用工具\n\n");
        sb.append("你可以使用以下工具来辅助回答问题。当你需要使用工具时，");
        sb.append("请严格按指定JSON格式输出工具调用，不要输出其他内容。\n\n");

        // 工具列表 + 参数说明
        int index = 1;
        for (ToolDefinition tool : tools) {
            sb.append("## ").append(index++).append(". ").append(tool.name()).append("\n");
            sb.append("功能：").append(tool.description()).append("\n");
            if (!tool.parameters().isEmpty()) {
                sb.append("参数：\n");
                for (ToolParameter param : tool.parameters()) {
                    sb.append("  - ").append(param.name());
                    sb.append(" (").append(param.type());
                    if (param.required()) sb.append(", 必需");
                    else sb.append(", 可选");
                    sb.append("): ").append(param.description());
                    if (!param.enumValues().isEmpty()) {
                        sb.append("。可选值: ").append(String.join(", ", param.enumValues()));
                    }
                    sb.append("\n");
                }
            }
            sb.append("\n");
        }

        // 调用格式说明
        sb.append("# 调用格式\n\n");
        sb.append("需要使用工具时，只输出以下JSON，不要包含任何其他文字：\n");
        sb.append("```json\n");
        sb.append("{\"name\": \"工具名称\", \"arguments\": {\"参数名\": \"参数值\"}}\n");
        sb.append("```\n\n");

        // Few-shot 示例 — 为每个工具生成一个具体示例
        sb.append("# 调用示例\n\n");
        for (ToolDefinition tool : tools) {
            sb.append("使用 ").append(tool.name()).append(" 时，输出：\n");
            sb.append("```json\n");
            sb.append(buildExampleCall(tool));
            sb.append("\n```\n\n");
        }

        sb.append("# 注意\n");
        sb.append("- 只有在需要使用工具时才输出JSON格式\n");
        sb.append("- 不需要工具时，直接用自然语言回答\n");

        return sb.toString();
    }

    /**
     * 为单个工具生成一个示例调用 JSON。
     */
    private String buildExampleCall(ToolDefinition tool) {
        StringBuilder sb = new StringBuilder();
        sb.append("{\"name\": \"").append(tool.name()).append("\", \"arguments\": {");
        if (!tool.parameters().isEmpty()) {
            String args = tool.parameters().stream()
                .map(p -> {
                    String exampleValue = getExampleValue(p);
                    return "\"" + p.name() + "\": " + exampleValue;
                })
                .collect(Collectors.joining(", "));
            sb.append(args);
        }
        sb.append("}}");
        return sb.toString();
    }

    private String getExampleValue(ToolParameter param) {
        if (!param.enumValues().isEmpty()) {
            return "\"" + param.enumValues().get(0) + "\"";
        }
        return switch (param.type()) {
            case "integer", "number" -> "42";
            case "boolean" -> "true";
            default -> "\"" + param.description().replaceAll("[，。、；：].*$", "").trim() + "\"";
        };
    }

    /**
     * 将工具提示注入到对话历史中。
     *
     * <p>策略：如果已有系统消息，追加到末尾；否则在列表头部插入新的系统消息。</p>
     */
    private void injectToolPrompt(List<Message> conversationHistory, String toolPrompt) {
        // 查找最后一个系统消息
        int lastSystemIndex = -1;
        for (int i = conversationHistory.size() - 1; i >= 0; i--) {
            if (conversationHistory.get(i).role() == Role.SYSTEM) {
                lastSystemIndex = i;
                break;
            }
        }

        if (lastSystemIndex >= 0) {
            // 追加到已有系统消息末尾
            Message existing = conversationHistory.get(lastSystemIndex);
            conversationHistory.set(lastSystemIndex,
                new Message(Role.SYSTEM, existing.content() + toolPrompt));
        } else {
            // 在头部插入新的系统消息
            conversationHistory.add(0, Message.system(toolPrompt.trim()));
        }

        LOG.debug("已注入工具描述到系统提示 ({} 个工具)", toolRegistry.size());
    }

    /* ──────────────────────────────────────────
     *  工具调用解析
     *  ────────────────────────────────────────── */

    /**
     * 从模型输出中解析工具调用。
     */
    private Optional<ToolCall> parseToolCall(String content) {
        if (content == null || content.isBlank()) {
            return Optional.empty();
        }

        // 先清理 markdown 代码块标记（小模型可能输出 ```json ... ```）
        String cleaned = content.trim();
        if (cleaned.startsWith("```")) {
            cleaned = cleanMarkdownBlock(cleaned);
        }

        // 策略1：JSON 解析
        try {
            JsonNode node = OBJECT_MAPPER.readTree(cleaned);
            if (node.has("name") && node.has("arguments")) {
                String name = node.get("name").asText();
                String args = node.get("arguments").toString();
                return Optional.of(ToolCall.of(name, args));
            }
            if (node.has("function") && node.get("function").has("name")) {
                JsonNode func = node.get("function");
                String name = func.get("name").asText();
                String args = func.has("arguments") ? func.get("arguments").asText() : "{}";
                return Optional.of(ToolCall.of(name, args));
            }
        } catch (JsonProcessingException ignored) {
        }

        // 策略2：匹配 tool_call("name", {...}) 格式
        Matcher matcher = TOOL_CALL_PREFIX.matcher(content);
        if (matcher.find()) {
            String name = matcher.group(1);
            int jsonStart = matcher.end();
            String json = extractBalancedJson(content, jsonStart);
            if (json != null) {
                return Optional.of(ToolCall.of(name, json));
            }
        }

        // 策略3：在文本中搜索嵌入的 JSON 工具调用（小模型可能在前后加文字）
        int jsonStart = findJsonToolCall(cleaned);
        if (jsonStart >= 0) {
            String json = extractBalancedJson(cleaned, jsonStart);
            if (json != null) {
                try {
                    JsonNode node = OBJECT_MAPPER.readTree(json);
                    if (node.has("name") && node.has("arguments")) {
                        return Optional.of(ToolCall.of(node.get("name").asText(), node.get("arguments").toString()));
                    }
                } catch (JsonProcessingException ignored) {
                }
            }
        }

        return Optional.empty();
    }

    /**
     * 清理 markdown 代码块标记。
     */
    private String cleanMarkdownBlock(String text) {
        // 去掉 ```json 开头和 ``` 结尾
        String cleaned = text.replaceAll("^```(?:json)?\\s*", "")
                             .replaceAll("\\s*```$", "")
                             .trim();
        return cleaned;
    }

    /**
     * 在文本中搜索 JSON 工具调用的起始位置。
     * 寻找 {"name": "...", "arguments": 模式。
     */
    private int findJsonToolCall(String text) {
        // 搜索 "name" 字段后紧跟已知工具名的模式
        for (ToolDefinition tool : toolRegistry.getDefinitions()) {
            String pattern = "\"" + tool.name() + "\"";
            int idx = text.indexOf(pattern);
            if (idx > 0) {
                // 向前找 '{'
                for (int i = idx - 1; i >= 0; i--) {
                    if (text.charAt(i) == '{') return i;
                    if (!Character.isWhitespace(text.charAt(i)) && text.charAt(i) != '"'
                        && text.charAt(i) != ':' && text.charAt(i) != 'n'
                        && text.charAt(i) != 'a' && text.charAt(i) != 'm'
                        && text.charAt(i) != 'e') break;
                }
            }
        }
        return -1;
    }

    private static String extractBalancedJson(String text, int start) {
        if (start >= text.length() || text.charAt(start) != '{') return null;
        int depth = 0;
        boolean inString = false;
        boolean escape = false;
        for (int i = start; i < text.length(); i++) {
            char c = text.charAt(i);
            if (escape) { escape = false; continue; }
            if (c == '\\' && inString) { escape = true; continue; }
            if (c == '"') { inString = !inString; continue; }
            if (inString) continue;
            if (c == '{') depth++;
            else if (c == '}') { depth--; if (depth == 0) return text.substring(start, i + 1); }
        }
        return null;
    }
}
