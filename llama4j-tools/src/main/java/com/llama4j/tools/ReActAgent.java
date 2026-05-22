package com.llama4j.tools;

import com.llama4j.chat.Message;
import com.llama4j.chat.Role;
import com.llama4j.core.*;
import com.llama4j.core.hook.*;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * 增强 ReAct Agent — 多工具调用 + Grammar 约束 + Hook 系统
 *
 * <p>基于 {@link Model} 接口，支持本地推理和云端 API。核心改进：</p>
 * <ul>
 *   <li><strong>多工具调用</strong>：每轮可解析和执行多个 ToolCall</li>
 *   <li><strong>Grammar 约束</strong>：工具调用轮次可启用 JSON 模式</li>
 *   <li><strong>Hook 系统</strong>：PreReasoning → PostReasoning → PreActing → PostActing</li>
 *   <li><strong>可配置提示策略</strong>：通过 {@link PromptStrategy} 自定义工具描述格式</li>
 * </ul>
 *
 * <h2>ReAct 循环</h2>
 * <pre>
 * 用户消息 → 注入工具描述 → [推理 → 检测工具调用 → 执行工具 → 反馈] × N → 最终回答
 * </pre>
 *
 * <h2>使用示例</h2>
 * <pre>{@code
 * ReActAgent agent = ReActAgent.builder()
 *     .model(localModel)
 *     .toolRegistry(registry)
 *     .maxIterations(10)
 *     .build();
 *
 * ChatResponse response = agent.call(ChatRequest.builder()
 *     .addMessage(Role.USER, "北京今天天气如何？")
 *     .build());
 * }</pre>
 */
public class ReActAgent {

    private static final Logger LOG = LoggerFactory.getLogger(ReActAgent.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    /** 匹配 JSON 数组或单个 JSON 对象中的工具调用 */
    private static final Pattern TOOL_CALL_PREFIX =
        Pattern.compile("tool_call\\s*\\(\\s*\"([^\"]+)\"\\s*,\\s*");

    private final Model model;
    private final ToolRegistry toolRegistry;
    private final int maxIterations;
    private final boolean jsonModeForTools;
    private final PromptStrategy promptStrategy;
    private final List<Hook> hooks;

    private ReActAgent(Builder builder) {
        this.model = Objects.requireNonNull(builder.model, "Model 不能为 null");
        this.toolRegistry = Objects.requireNonNull(builder.toolRegistry, "ToolRegistry 不能为 null");
        this.maxIterations = builder.maxIterations;
        this.jsonModeForTools = builder.jsonModeForTools;
        this.promptStrategy = builder.promptStrategy;
        this.hooks = builder.hooks.stream()
            .sorted(Comparator.comparingInt(Hook::priority))
            .toList();
    }

    public static Builder builder() {
        return new Builder();
    }

    /* ──────────────────────────────────────────
     *  同步 ReAct 循环
     *  ────────────────────────────────────────── */

    /**
     * 执行同步 ReAct 循环。
     */
    public ChatResponse call(ChatRequest request) {
        List<Message> history = new ArrayList<>(request.messages());
        String toolPrompt = promptStrategy.buildToolPrompt(toolRegistry.getDefinitions());
        if (!toolPrompt.isEmpty()) {
            injectToolPrompt(history, toolPrompt);
        }

        Set<String> executedKeys = new HashSet<>();
        ChatResponse response = null;

        for (int round = 0; round < maxIterations; round++) {
            // PreReasoning hook
            notifyHooks(new PreReasoningEvent(round, history));

            boolean expectToolCall = round == 0 || !executedKeys.isEmpty();
            ChatRequest currentRequest = buildCurrentRequest(request, history, expectToolCall);
            response = model.chat(currentRequest);

            // PostReasoning hook
            notifyHooks(new PostReasoningEvent(round, response));

            // 解析工具调用 — 支持多个
            List<ToolCall> toolCalls = parseToolCalls(response.content());
            if (toolCalls.isEmpty()) {
                LOG.debug("第 {} 轮无工具调用，返回最终响应", round + 1);
                return response;
            }

            // 去重
            toolCalls = toolCalls.stream()
                .filter(tc -> {
                    String key = tc.toolName() + ":" + tc.arguments();
                    return executedKeys.add(key);
                })
                .toList();

            if (toolCalls.isEmpty()) {
                LOG.warn("第 {} 轮所有工具调用均为重复，返回当前响应", round + 1);
                return response;
            }

            LOG.info("第 {}/{} 轮检测到 {} 个工具调用", round + 1, maxIterations, toolCalls.size());

            // PreActing hook
            notifyHooks(new PreActingEvent(round, toolCalls.stream()
                .map(tc -> Map.of("name", tc.toolName(), "arguments", tc.arguments()))
                .toList()));

            // 执行工具
            List<ToolResult> results = executeTools(toolCalls);

            // PostActing hook
            notifyHooks(new PostActingEvent(round, results.stream()
                .map(r -> {
                    Map<String, Object> map = new LinkedHashMap<>();
                    map.put("name", r.toolCallId());
                    map.put("content", r.content());
                    map.put("success", r.success());
                    return map;
                })
                .toList()));

            // 更新对话历史
            String callSummary = toolCalls.stream()
                .map(tc -> tc.toolName() + "(" + tc.arguments() + ")")
                .collect(Collectors.joining(", "));
            history.add(Message.assistant("[调用工具: " + callSummary + "]"));

            String resultSummary = results.stream()
                .map(r -> r.content())
                .collect(Collectors.joining("\n"));
            history.add(Message.tool(resultSummary));
        }

        LOG.warn("已达到最大迭代次数 ({})，返回当前响应", maxIterations);
        return response;
    }

    /* ──────────────────────────────────────────
     *  流式 ReAct 循环
     *  ────────────────────────────────────────── */

    /**
     * 执行流式 ReAct 循环。
     */
    public CompletableFuture<ChatResponse> callStream(ChatRequest request, StreamingToolListener listener) {
        List<Message> history = new ArrayList<>(request.messages());
        LOG.info("[ReAct流式] 开始, 消息数={}, maxIterations={}", history.size(), maxIterations);

        String toolPrompt = promptStrategy.buildToolPrompt(toolRegistry.getDefinitions());
        if (!toolPrompt.isEmpty()) {
            injectToolPrompt(history, toolPrompt);
        }

        ChatResponse[] lastResponse = {null};
        Set<String> executedKeys = new HashSet<>();

        try {
            for (int round = 0; round < maxIterations; round++) {
                LOG.info("[ReAct流式] 第 {}/{} 轮推理", round + 1, maxIterations);

                notifyHooks(new PreReasoningEvent(round, history));

                boolean expectToolCall = round == 0 || !executedKeys.isEmpty();
                ChatRequest currentRequest = buildCurrentRequest(request, history, expectToolCall);

                StringBuilder buffer = new StringBuilder();
                int[] decision = {0}; // 0=未决定, 1=实时转发, 2=缓冲

                CompletableFuture<ChatResponse> future =
                    model.chatStream(currentRequest, new ChatStreamListener() {
                        @Override
                        public void onToken(String token) {
                            buffer.append(token);
                            if (decision[0] == 2) return;

                            if (decision[0] == 0 && buffer.length() >= 2) {
                                String start = buffer.toString().trim();
                                if (start.startsWith("[") || start.startsWith("{")
                                    || start.startsWith("```") || start.startsWith("tool_call")) {
                                    decision[0] = 2;
                                    return;
                                }
                                decision[0] = 1;
                                listener.onContentToken(buffer.toString());
                                return;
                            }
                            if (decision[0] == 1) {
                                listener.onContentToken(token);
                            }
                        }

                        @Override
                        public void onComplete(ChatResponse response) {
                            lastResponse[0] = response;
                        }

                        @Override
                        public void onError(Throwable error) {
                            throw new RuntimeException(error);
                        }
                    });

                future.join();
                String fullOutput = buffer.toString();

                notifyHooks(new PostReasoningEvent(round, lastResponse[0]));

                List<ToolCall> toolCalls = parseToolCalls(fullOutput);

                if (toolCalls.isEmpty()) {
                    LOG.info("[ReAct流式] 第 {} 轮无工具调用", round + 1);
                    if (decision[0] != 1) {
                        listener.onContentToken(fullOutput);
                    }
                    listener.onComplete(lastResponse[0]);
                    return CompletableFuture.completedFuture(lastResponse[0]);
                }

                // 去重
                toolCalls = toolCalls.stream()
                    .filter(tc -> {
                        String key = tc.toolName() + ":" + tc.arguments();
                        if (executedKeys.contains(key)) {
                            LOG.warn("[ReAct流式] 重复工具调用: {}", key);
                            return false;
                        }
                        executedKeys.add(key);
                        return true;
                    })
                    .toList();

                if (toolCalls.isEmpty()) {
                    listener.onContentToken(fullOutput);
                    listener.onComplete(lastResponse[0]);
                    return CompletableFuture.completedFuture(lastResponse[0]);
                }

                LOG.info("[ReAct流式] 第 {} 轮: {} 个工具调用", round + 1, toolCalls.size());

                // 通知每个工具调用
                for (ToolCall tc : toolCalls) {
                    listener.onToolCall(tc);
                }

                notifyHooks(new PreActingEvent(round, toolCalls.stream()
                    .map(tc -> Map.of("name", tc.toolName(), "arguments", tc.arguments()))
                    .toList()));

                List<ToolResult> results = executeTools(toolCalls);

                for (ToolResult r : results) {
                    listener.onToolResult(r);
                }

                notifyHooks(new PostActingEvent(round, results.stream()
                    .map(r -> {
                        Map<String, Object> map = new LinkedHashMap<>();
                        map.put("name", r.toolCallId());
                        map.put("content", r.content());
                        map.put("success", r.success());
                        return map;
                    })
                    .toList()));

                String callSummary = toolCalls.stream()
                    .map(tc -> tc.toolName() + "(" + tc.arguments() + ")")
                    .collect(Collectors.joining(", "));
                history.add(Message.assistant("[调用工具: " + callSummary + "]"));
                history.add(Message.tool(results.stream()
                    .map(ToolResult::content)
                    .collect(Collectors.joining("\n"))));
            }

            LOG.warn("[ReAct流式] 达到最大迭代 ({})，返回当前响应", maxIterations);
            listener.onComplete(lastResponse[0]);
            return CompletableFuture.completedFuture(lastResponse[0]);
        } catch (Exception e) {
            LOG.error("[ReAct流式] 异常: {}", e.getMessage(), e);
            notifyHooks(new ErrorEvent(-1, "stream", e));
            listener.onError(e);
            return CompletableFuture.failedFuture(e);
        }
    }

    /* ──────────────────────────────────────────
     *  Hook 通知
     *  ────────────────────────────────────────── */

    private void notifyHooks(HookEvent event) {
        for (Hook hook : hooks) {
            try {
                hook.onEvent(event);
            } catch (Exception e) {
                LOG.warn("Hook 执行异常: {}", e.getMessage());
            }
        }
    }

    /* ──────────────────────────────────────────
     *  内部辅助方法
     *  ────────────────────────────────────────── */

    private ChatRequest buildCurrentRequest(ChatRequest original, List<Message> history, boolean expectToolCall) {
        ChatRequest.Builder builder = ChatRequest.builder()
            .messages(history)
            .temperature(original.temperature())
            .maxTokens(original.maxTokens())
            .topK(original.topK())
            .topP(original.topP())
            .repeatPenalty(original.repeatPenalty())
            .seed(original.seed())
            .stopTokens(original.stopTokens());

        // 工具调用轮次启用 JSON 模式（仅本地推理有效）
        if (jsonModeForTools && expectToolCall) {
            builder.jsonMode(true);
        }

        return builder.build();
    }

    private void injectToolPrompt(List<Message> history, String toolPrompt) {
        int lastSystemIndex = -1;
        for (int i = history.size() - 1; i >= 0; i--) {
            if (history.get(i).role() == Role.SYSTEM) {
                lastSystemIndex = i;
                break;
            }
        }
        if (lastSystemIndex >= 0) {
            Message existing = history.get(lastSystemIndex);
            history.set(lastSystemIndex, new Message(Role.SYSTEM, existing.content() + toolPrompt));
        } else {
            history.add(0, Message.system(toolPrompt.trim()));
        }
    }

    /**
     * 解析多个工具调用 — 支持 JSON 数组和单个 JSON 对象。
     */
    private List<ToolCall> parseToolCalls(String content) {
        if (content == null || content.isBlank()) return List.of();

        String cleaned = content.trim();
        if (cleaned.startsWith("```")) {
            cleaned = cleanMarkdownBlock(cleaned);
        }

        // 策略1：JSON 数组 [{"name": ..., "arguments": ...}, ...]
        try {
            JsonNode node = MAPPER.readTree(cleaned);
            if (node.isArray()) {
                List<ToolCall> calls = new ArrayList<>();
                for (JsonNode item : node) {
                    ToolCall tc = parseSingleToolCall(item);
                    if (tc != null) calls.add(tc);
                }
                if (!calls.isEmpty()) return calls;
            }
            // 单个 JSON 对象
            ToolCall single = parseSingleToolCall(node);
            if (single != null) return List.of(single);
        } catch (JsonProcessingException ignored) {
        }

        // 策略2：在文本中搜索嵌入的 JSON 工具调用
        List<ToolCall> embedded = parseEmbeddedToolCalls(cleaned);
        if (!embedded.isEmpty()) return embedded;

        // 策略3：tool_call("name", {...}) 格式
        return parseToolCallFunction(content);
    }

    private ToolCall parseSingleToolCall(JsonNode node) {
        if (node.has("name") && (node.has("arguments") || node.has("args"))) {
            String name = node.get("name").asText();
            JsonNode argsNode = node.has("arguments") ? node.get("arguments") : node.get("args");
            String args = argsNode.isObject() ? argsNode.toString() : argsNode.asText();
            return ToolCall.of(name, args);
        }
        // OpenAI 格式: {"function": {"name": ..., "arguments": ...}}
        if (node.has("function") && node.get("function").has("name")) {
            JsonNode func = node.get("function");
            String name = func.get("name").asText();
            String args = func.has("arguments") ? func.get("arguments").asText() : "{}";
            return ToolCall.of(name, args);
        }
        return null;
    }

    private List<ToolCall> parseEmbeddedToolCalls(String text) {
        List<ToolCall> calls = new ArrayList<>();
        int start = 0;
        while (start < text.length()) {
            int jsonStart = findNextJsonToolCall(text, start);
            if (jsonStart < 0) break;
            String json = extractBalancedJson(text, jsonStart);
            if (json != null) {
                try {
                    JsonNode node = MAPPER.readTree(json);
                    ToolCall tc = parseSingleToolCall(node);
                    if (tc != null) {
                        calls.add(tc);
                        start = jsonStart + json.length();
                        continue;
                    }
                } catch (JsonProcessingException ignored) {
                }
            }
            start = jsonStart + 1;
        }
        return calls;
    }

    private List<ToolCall> parseToolCallFunction(String text) {
        Matcher matcher = TOOL_CALL_PREFIX.matcher(text);
        List<ToolCall> calls = new ArrayList<>();
        while (matcher.find()) {
            String name = matcher.group(1);
            int jsonStart = matcher.end();
            String json = extractBalancedJson(text, jsonStart);
            if (json != null) {
                calls.add(ToolCall.of(name, json));
            }
        }
        return calls;
    }

    private int findNextJsonToolCall(String text, int fromIndex) {
        for (ToolDefinition tool : toolRegistry.getDefinitions()) {
            String pattern = "\"" + tool.name() + "\"";
            int idx = text.indexOf(pattern, fromIndex);
            if (idx > 0) {
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

    private List<ToolResult> executeTools(List<ToolCall> toolCalls) {
        return toolCalls.stream()
            .map(tc -> {
                try {
                    return toolRegistry.execute(tc);
                } catch (Exception e) {
                    LOG.error("工具执行失败: {} - {}", tc.toolName(), e.getMessage());
                    return ToolResult.failure(tc.id(), "执行错误: " + e.getMessage());
                }
            })
            .toList();
    }

    private static String cleanMarkdownBlock(String text) {
        return text.replaceAll("^```(?:json)?\\s*", "")
                   .replaceAll("\\s*```$", "")
                   .trim();
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

    /* ──────────────────────────────────────────
     *  访问器
     *  ────────────────────────────────────────── */

    public Model getModel() { return model; }
    public ToolRegistry getToolRegistry() { return toolRegistry; }

    /* ──────────────────────────────────────────
     *  Builder
     *  ────────────────────────────────────────── */

    public static class Builder {
        private Model model;
        private ToolRegistry toolRegistry;
        private int maxIterations = 10;
        private boolean jsonModeForTools = false;
        private PromptStrategy promptStrategy = new MarkdownPromptStrategy();
        private final List<Hook> hooks = new ArrayList<>();

        public Builder model(Model model) { this.model = model; return this; }
        public Builder toolRegistry(ToolRegistry toolRegistry) { this.toolRegistry = toolRegistry; return this; }
        public Builder maxIterations(int maxIterations) { this.maxIterations = maxIterations; return this; }
        public Builder jsonModeForTools(boolean jsonModeForTools) { this.jsonModeForTools = jsonModeForTools; return this; }
        public Builder promptStrategy(PromptStrategy promptStrategy) { this.promptStrategy = promptStrategy; return this; }
        public Builder addHook(Hook hook) { this.hooks.add(hook); return this; }

        public ReActAgent build() {
            return new ReActAgent(this);
        }
    }
}
