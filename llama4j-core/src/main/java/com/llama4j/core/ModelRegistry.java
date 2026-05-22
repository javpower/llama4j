package com.llama4j.core;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 多模型注册中心 — 管理多个命名模型实例
 *
 * <p>支持同时注册多个本地模型和云端模型，每个模型有唯一名称。
 * 通过名称获取模型实例，也支持获取默认模型。</p>
 *
 * <h2>使用示例</h2>
 * <pre>{@code
 * ModelRegistry registry = new ModelRegistry();
 *
 * // 注册本地模型
 * registry.register("qwen-local", LocalModel.fromFile("/models/qwen2.5-7b.gguf"));
 *
 * // 注册云端模型
 * registry.register("deepseek", new OpenAIModel(OpenAIConfig.builder()
 *     .apiKey("sk-xxx")
 *     .baseUrl("https://api.deepseek.com")
 *     .modelName("deepseek-chat")
 *     .build()));
 *
 * registry.setDefaultModel("qwen-local");
 *
 * // 使用
 * Model model = registry.get("deepseek");
 * ChatResponse response = model.chat(request);
 *
 * // 使用默认模型
 * Model defaultModel = registry.getDefault();
 * }</pre>
 */
public class ModelRegistry implements AutoCloseable {

    private final ConcurrentHashMap<String, Model> models = new ConcurrentHashMap<>();
    private volatile String defaultModelName;

    public ModelRegistry() {}

    /** 关闭所有本地模型，释放原生资源 */
    @Override
    public void close() {
        for (var entry : models.entrySet()) {
            if (entry.getValue() instanceof LocalModel local) {
                local.close();
            }
        }
        models.clear();
    }

    /**
     * 注册模型。
     *
     * @param name  模型名称（唯一标识）
     * @param model 模型实例
     */
    public void register(String name, Model model) {
        Objects.requireNonNull(name, "模型名称不能为 null");
        Objects.requireNonNull(model, "Model 不能为 null");
        models.put(name, model);
        if (defaultModelName == null) {
            defaultModelName = name;
        }
    }

    /**
     * 取消注册模型。
     */
    public void unregister(String name) {
        models.remove(name);
        if (name.equals(defaultModelName)) {
            defaultModelName = models.isEmpty() ? null : models.keys().nextElement();
        }
    }

    /**
     * 按名称获取模型。
     *
     * @throws IllegalArgumentException 如果模型不存在
     */
    public Model get(String name) {
        Model model = models.get(name);
        if (model == null) {
            throw new IllegalArgumentException("模型不存在: " + name + "，可用模型: " + modelNames());
        }
        return model;
    }

    /**
     * 获取默认模型。
     *
     * @throws IllegalStateException 如果没有注册任何模型
     */
    public Model getDefault() {
        if (defaultModelName == null) {
            throw new IllegalStateException("没有注册任何模型");
        }
        return models.get(defaultModelName);
    }

    /** 设置默认模型名称 */
    public void setDefaultModel(String name) {
        if (!models.containsKey(name)) {
            throw new IllegalArgumentException("模型不存在: " + name);
        }
        this.defaultModelName = name;
    }

    /** 获取默认模型名称 */
    public String defaultModelName() {
        return defaultModelName;
    }

    /** 获取所有已注册模型名称 */
    public Set<String> modelNames() {
        return Collections.unmodifiableSet(models.keySet());
    }

    /** 获取已注册模型数量 */
    public int size() {
        return models.size();
    }

    /** 是否包含指定名称的模型 */
    public boolean contains(String name) {
        return models.containsKey(name);
    }
}
