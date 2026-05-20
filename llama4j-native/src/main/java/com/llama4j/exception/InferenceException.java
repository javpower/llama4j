package com.llama4j.exception;

/**
 * 推理异常
 *
 * <p>当模型推理过程中发生错误时抛出。</p>
 */
public class InferenceException extends Llama4jException {

    public static final String CODE = "INFERENCE_ERROR";

    public InferenceException(String message) {
        super(CODE, message);
    }

    public InferenceException(String message, Throwable cause) {
        super(CODE, message, cause);
    }
}
