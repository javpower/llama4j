package com.llama4j.spring.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * OpenAI 兼容的聊天补全请求 — 请求体 DTO
 *
 * <p>镜像 OpenAI Chat Completion API 的请求格式，实现线级兼容。
 * 任何使用 OpenAI SDK 的客户端都可以直接对接 llama4j 服务。
 * 支持多模态内容：content 字段既可以是纯文本字符串，
 * 也可以是包含 text 和 image_url 类型的内容数组。</p>
 *
 * <h2>字段映射</h2>
 * <table>
 * <caption>字段映射</caption>
 *   <tr><th>OpenAI 字段</th><th>Java 字段</th><th>说明</th></tr>
 *   <tr><td>model</td><td>model</td><td>模型标识（llama4j 忽略，使用配置的模型）</td></tr>
 *   <tr><td>messages</td><td>messages</td><td>对话消息列表</td></tr>
 *   <tr><td>temperature</td><td>temperature</td><td>采样温度</td></tr>
 *   <tr><td>max_tokens</td><td>maxTokens</td><td>最大生成 token 数</td></tr>
 *   <tr><td>top_p</td><td>topP</td><td>核采样阈值</td></tr>
 *   <tr><td>stream</td><td>stream</td><td>是否流式输出</td></tr>
 *   <tr><td>tools</td><td>tools</td><td>工具定义列表</td></tr>
 * </table>
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ChatCompletionRequest(
    String model,
    List<ChatMessage> messages,
    Double temperature,
    @JsonProperty("max_tokens") Integer maxTokens,
    @JsonProperty("top_p") Double topP,
    Boolean stream,
    List<ToolSchema> tools
) {

    /**
     * 单条消息 — content 支持 String 或多模态内容数组。
     *
     * <p>当 content 是纯文本时，Jackson 反序列化为 String；
     * 当 content 是数组时，反序列化为 List&lt;Map&gt;。
     * textContent() 和 imageParts() 方法分别提取文本和图片内容。</p>
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record ChatMessage(
        String role,
        Object content,
        @JsonProperty("tool_calls") List<ToolCallInfo> toolCalls,
        @JsonProperty("tool_call_id") String toolCallId
    ) {
        public ChatMessage(String role, String content) {
            this(role, (Object) content, null, null);
        }

        /** 提取文本内容（兼容 String 和 List 格式） */
        @SuppressWarnings("unchecked")
        public String textContent() {
            if (content == null) return "";
            if (content instanceof String s) return s;
            if (content instanceof List<?> list) {
                return list.stream()
                    .filter(item -> item instanceof Map)
                    .map(item -> (Map<String, Object>) item)
                    .filter(m -> "text".equals(m.get("type")))
                    .map(m -> m.get("text") != null ? m.get("text").toString() : "")
                    .collect(Collectors.joining("\n"));
            }
            return content.toString();
        }

        /** 检查是否包含图片内容 */
        @SuppressWarnings("unchecked")
        public boolean hasImages() {
            if (content instanceof List<?> list) {
                return list.stream()
                    .filter(item -> item instanceof Map)
                    .map(item -> (Map<String, Object>) item)
                    .anyMatch(m -> "image_url".equals(m.get("type")));
            }
            return false;
        }

        /** 提取图片 URL 列表 */
        @SuppressWarnings("unchecked")
        public List<String> imageUrls() {
            if (content instanceof List<?> list) {
                return list.stream()
                    .filter(item -> item instanceof Map)
                    .map(item -> (Map<String, Object>) item)
                    .filter(m -> "image_url".equals(m.get("type")))
                    .map(m -> {
                        Object imageUrl = m.get("image_url");
                        if (imageUrl instanceof Map) {
                            Object url = ((Map<String, Object>) imageUrl).get("url");
                            return url != null ? url.toString() : null;
                        }
                        return null;
                    })
                    .filter(Objects::nonNull)
                    .toList();
            }
            return List.of();
        }
    }

    /** 工具调用信息 */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record ToolCallInfo(
        String id,
        String type,
        FunctionCall function
    ) {}

    /** 函数调用详情 */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record FunctionCall(
        String name,
        String arguments
    ) {}

    /** 工具定义 Schema */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record ToolSchema(
        String type,
        FunctionSchema function
    ) {}

    /** 函数 Schema 定义 */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record FunctionSchema(
        String name,
        String description,
        ParametersSchema parameters
    ) {}

    /** 参数 Schema */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record ParametersSchema(
        String type,
        Map<String, PropertySchema> properties,
        List<String> required
    ) {}

    /** 单个参数属性 Schema */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record PropertySchema(
        String type,
        String description,
        @JsonProperty("enum") List<String> enum_
    ) {}
}
