package com.llama4j.core.hook;

/**
 * 错误事件 — 只读通知
 */
public final class ErrorEvent extends HookEvent {

    private final Throwable error;
    private final String phase;

    public ErrorEvent(int round, String phase, Throwable error) {
        super(round);
        this.phase = phase;
        this.error = error;
    }

    public Throwable error() { return error; }
    public String phase() { return phase; }
}
