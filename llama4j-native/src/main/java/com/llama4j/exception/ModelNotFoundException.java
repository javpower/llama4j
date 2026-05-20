package com.llama4j.exception;

/**
 * 模型未找到异常
 *
 * <p>当请求的 GGUF 模型文件无法找到或加载失败时抛出。</p>
 */
public class ModelNotFoundException extends Llama4jException {

    /** 错误码常量 */
    public static final String CODE = "MODEL_NOT_FOUND";

    /** 模型文件路径 */
    private final String modelPath;

    /** @param modelPath 未找到的模型路径 */
    public ModelNotFoundException(String modelPath) {
        super(CODE, "模型未找到: " + modelPath);
        this.modelPath = modelPath;
    }

    /**
     * @param modelPath 未找到的模型路径
     * @param cause     原始异常
     */
    public ModelNotFoundException(String modelPath, Throwable cause) {
        super(CODE, "模型未找到: " + modelPath, cause);
        this.modelPath = modelPath;
    }

    /** @return 模型文件路径 */
    public String getModelPath() {
        return modelPath;
    }
}
