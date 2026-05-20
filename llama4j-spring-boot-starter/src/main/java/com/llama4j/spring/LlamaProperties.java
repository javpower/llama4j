package com.llama4j.spring;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * llama4j Spring Boot 配置属性
 *
 * <p>所有属性以 {@code llama4j} 为前缀，在 application.yml 中配置：</p>
 *
 * <pre>
 * llama4j:
 *   model:
 *     path: /models/qwen2.5-7b-q4_k_m.gguf
 *     n-ctx: 4096
 *     n-gpu-layers: -1
 *     n-threads: 8
 *   inference:
 *     temperature: 0.7
 *     max-tokens: 2048
 *     top-k: 40
 *     top-p: 0.9
 *     repeat-penalty: 1.1
 * </pre>
 */
@ConfigurationProperties(prefix = "llama4j")
public class LlamaProperties {

    private ModelConfig model = new ModelConfig();
    private InferenceConfig inference = new InferenceConfig();
    private ServerConfig server = new ServerConfig();

    public ModelConfig getModel() { return model; }
    public void setModel(ModelConfig model) { this.model = model; }

    public InferenceConfig getInference() { return inference; }
    public void setInference(InferenceConfig inference) { this.inference = inference; }

    public ServerConfig getServer() { return server; }
    public void setServer(ServerConfig server) { this.server = server; }

    public static class ModelConfig {
        private String path;
        private String id;
        private int nCtx = 4096;
        private int nGpuLayers = -1;
        private int nThreads = Runtime.getRuntime().availableProcessors();

        public String getPath() { return path; }
        public void setPath(String path) { this.path = path; }
        public String getId() { return id; }
        public void setId(String id) { this.id = id; }
        public int getNCtx() { return nCtx; }
        public void setNCtx(int nCtx) { this.nCtx = nCtx; }
        public int getNGpuLayers() { return nGpuLayers; }
        public void setNGpuLayers(int nGpuLayers) { this.nGpuLayers = nGpuLayers; }
        public int getNThreads() { return nThreads; }
        public void setNThreads(int nThreads) { this.nThreads = nThreads; }
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

    public static class ServerConfig {
        private boolean openaiCompatible = true;
        private int maxConcurrentRequests = 4;

        public boolean isOpenaiCompatible() { return openaiCompatible; }
        public void setOpenaiCompatible(boolean openaiCompatible) { this.openaiCompatible = openaiCompatible; }
        public int getMaxConcurrentRequests() { return maxConcurrentRequests; }
        public void setMaxConcurrentRequests(int maxConcurrentRequests) { this.maxConcurrentRequests = maxConcurrentRequests; }
    }
}
