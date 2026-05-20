package com.llama4j.exception;

/**
 * llama4j 运行时异常基类
 *
 * <p>所有 llama4j 特定异常的公共父类，允许调用方用单一类型
 * 捕获所有库内部错误。异常层次结构如下：</p>
 *
 * <pre>
 * Llama4jException (基类)
 * ├── ModelNotFoundException    — 模型文件未找到或加载失败
 * ├── InferenceException        — 推理过程中出错
 * ├── SessionNotFoundException  — 会话未找到
 * └── ToolNotFoundException     — 工具未注册
 * </pre>
 *
 * <p>设计为 RuntimeException（非受检异常），因为大多数 llama4j 错误
 * 属于不可恢复的运行时问题（如模型损坏、内存不足），强制捕获
 * 不会带来实际价值。</p>
 */
public class Llama4jException extends RuntimeException {

    /** 错误码标识 */
    private final String errorCode;

    /**
     * @param errorCode 错误码
     * @param message   错误描述
     */
    public Llama4jException(String errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    /**
     * @param errorCode 错误码
     * @param message   错误描述
     * @param cause     原始异常
     */
    public Llama4jException(String errorCode, String message, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
    }

    /**
     * @param errorCode 错误码
     * @param cause     原始异常
     */
    public Llama4jException(String errorCode, Throwable cause) {
        super(cause);
        this.errorCode = errorCode;
    }

    /** @return 错误码标识 */
    public String getErrorCode() {
        return errorCode;
    }
}
