package com.llama4j.spring;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

/**
 * llama4j Spring Boot 配置属性
 *
 * <pre>
 * llama4j:
 *   api:
 *     key: your-secret-key       # 可选，不配则不校验
 *   default-model: qwen-local
 *   models:
 *     qwen-local:
 *       type: local
 *       path: /models/qwen2.5-7b-q4_k_m.gguf
 *       n-ctx: 4096
 *       n-gpu-layers: -1
 *       n-threads: 8
 *     deepseek:
 *       type: openai
 *       api-key: sk-xxx
 *       base-url: https://api.deepseek.com
 *       model-name: deepseek-chat
 *     gpt4o:
 *       type: openai
 *       api-key: sk-xxx
 *       base-url: https://api.openai.com
 *       model-name: gpt-4o
 *   inference:
 *     temperature: 0.7
 *     max-tokens: 2048
 * </pre>
 */
@ConfigurationProperties(prefix = "llama4j")
public class LlamaProperties {

    private ApiConfig api = new ApiConfig();
    private String defaultModel;
    private List<ModelConfig> models = new ArrayList<>();
    private InferenceConfig inference = new InferenceConfig();

    public ApiConfig getApi() { return api; }
    public void setApi(ApiConfig api) { this.api = api; }

    public String getDefaultModel() { return defaultModel; }
    public void setDefaultModel(String defaultModel) { this.defaultModel = defaultModel; }

    public List<ModelConfig> getModels() { return models; }
    public void setModels(List<ModelConfig> models) { this.models = models; }

    public InferenceConfig getInference() { return inference; }
    public void setInference(InferenceConfig inference) { this.inference = inference; }

    /** API 安全校验配置 */
    public static class ApiConfig {
        private String key;

        public String getKey() { return key; }
        public void setKey(String key) { this.key = key; }
    }

    /** 单个模型配置 */
    public static class ModelConfig {
        private String name;
        private String type = "local";  // local / openai
        private String path;
        private String modelId;
        private int nCtx = 4096;
        private int nGpuLayers = -1;
        private int nThreads = Runtime.getRuntime().availableProcessors();
        private String apiKey;
        private String baseUrl;
        private String modelName;
        private String mmprojPath;

        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public String getType() { return type; }
        public void setType(String type) { this.type = type; }
        public String getPath() { return path; }
        public void setPath(String path) { this.path = path; }
        public String getModelId() { return modelId; }
        public void setModelId(String modelId) { this.modelId = modelId; }
        public int getNCtx() { return nCtx; }
        public void setNCtx(int nCtx) { this.nCtx = nCtx; }
        public int getNGpuLayers() { return nGpuLayers; }
        public void setNGpuLayers(int nGpuLayers) { this.nGpuLayers = nGpuLayers; }
        public int getNThreads() { return nThreads; }
        public void setNThreads(int nThreads) { this.nThreads = nThreads; }
        public String getApiKey() { return apiKey; }
        public void setApiKey(String apiKey) { this.apiKey = apiKey; }
        public String getBaseUrl() { return baseUrl; }
        public void setBaseUrl(String baseUrl) { this.baseUrl = baseUrl; }
        public String getModelName() { return modelName; }
        public void setModelName(String modelName) { this.modelName = modelName; }
        public String getMmprojPath() { return mmprojPath; }
        public void setMmprojPath(String mmprojPath) { this.mmprojPath = mmprojPath; }
    }

    public static class InferenceConfig {
        private float temperature = 0.7f;
        private int maxTokens = 2048;
        private int topK = 40;
        private float topP = 0.9f;
        private float repeatPenalty = 1.1f;

        public float getTemperature() { return temperature; }
        public void setTemperature(float temperature) { this.temperature = temperature; }
        public int getMaxTokens() { return maxTokens; }
        public void setMaxTokens(int maxTokens) { this.maxTokens = maxTokens; }
        public int getTopK() { return topK; }
        public void setTopK(int topK) { this.topK = topK; }
        public float getTopP() { return topP; }
        public void setTopP(float topP) { this.topP = topP; }
        public float getRepeatPenalty() { return repeatPenalty; }
        public void setRepeatPenalty(float repeatPenalty) { this.repeatPenalty = repeatPenalty; }
    }
}
