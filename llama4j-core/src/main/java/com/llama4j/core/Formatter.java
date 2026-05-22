package com.llama4j.core;

import com.llama4j.chat.Message;

import java.util.List;

/**
 * 消息格式化策略接口 — 供应商特定的消息/响应转换
 *
 * <p>每个 LLM 供应商实现此接口，将统一的 {@link Message} 和 {@link ToolSchema}
 * 转换为供应商原生格式，并将供应商响应转换回 {@link ChatResponse}。</p>
 *
 * <h2>添加新供应商</h2>
 * <p>只需实现此接口 + {@link Model} 接口即可：</p>
 * <pre>{@code
 * class MyProviderFormatter implements Formatter<MyRequest, MyResponse> { ... }
 * class MyProviderModel implements Model { ... }
 * }</pre>
 *
 * @param <TReq>  供应商原生的请求消息类型
 * @param <TResp> 供应商原生的响应类型
 */
public interface Formatter<TReq, TResp> {

    /**
     * 将统一消息列表转换为供应商原生格式。
     */
    List<TReq> formatMessages(List<Message> messages);

    /**
     * 将供应商原生响应转换为统一 ChatResponse。
     */
    ChatResponse parseResponse(TResp response);

    /**
     * 将工具 schema 应用到供应商请求中。
     */
    void applyTools(List<ToolSchema> tools);
}
