package com.llama4j.session;

import com.llama4j.exception.Llama4jException;

/**
 * 会话未找到异常
 *
 * <p>当请求的会话 ID 在存储中不存在时抛出。</p>
 */
public class SessionNotFoundException extends Llama4jException {

    public static final String CODE = "SESSION_NOT_FOUND";

    private final String sessionId;

    public SessionNotFoundException(String sessionId) {
        super(CODE, "会话未找到: " + sessionId);
        this.sessionId = sessionId;
    }

    public String getSessionId() {
        return sessionId;
    }
}
