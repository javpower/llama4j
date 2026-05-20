package com.llama4j.spring.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * OpenAI 兼容的聊天补全请求 — 请求体 DTO
 *
 * <p>镜像 OpenAI Chat Completion API 的请求格式，实现线级兼容。
 * 任何使用 OpenAI SDK 的客户端都可以直接对接 llama4j 服务。</p>
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

    /** 单条消息 */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record ChatMessage(
        String role,
        String content,
        @JsonProperty("tool_calls") List<ToolCallInfo> toolCalls,
        @JsonProperty("tool_call_id") String toolCallId
    ) {
        public ChatMessage(String role, String content) {
            this(role, content, null, null);
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
        java.util.Map<String, PropertySchema> properties,
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
