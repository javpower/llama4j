package com.llama4j.spring;

import com.llama4j.core.LocalModel;
import com.llama4j.core.Model;
import com.llama4j.core.ModelRegistry;
import com.llama4j.metrics.LlamaMetrics;
import com.llama4j.native_.LlamaContext;
import com.llama4j.native_.ModelParams;
import com.llama4j.providers.openai.OpenAIConfig;
import com.llama4j.providers.openai.OpenAIModel;
import com.llama4j.repo.GgufRepository;
import com.llama4j.spring.security.ApiKeyFilter;
import com.llama4j.tools.ReActAgent;
import com.llama4j.tools.ToolRegistry;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import jakarta.annotation.PreDestroy;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;

import java.nio.file.Path;
import java.util.List;

/**
 * llama4j Spring Boot 自动配置
 *
 * <p>支持同时配置多个模型（本地 + 云端），自动注册到 {@link ModelRegistry}。</p>
 */
@AutoConfiguration
@EnableConfigurationProperties(LlamaProperties.class)
@Import({LlamaEndpoint.class})
public class LlamaAutoConfiguration {

    private static final Logger LOG = LoggerFactory.getLogger(LlamaAutoConfiguration.class);

    private ModelRegistry modelRegistry;

    @PreDestroy
    void shutdown() {
        if (modelRegistry != null) {
            LOG.info("正在关闭模型注册中心，释放原生资源...");
            modelRegistry.close();
            LOG.info("模型注册中心已关闭");
        }
    }

    /**
     * 创建模型注册中心 — 遍历配置中的所有模型并注册。
     */
    @Bean
    @ConditionalOnMissingBean
    ModelRegistry modelRegistry(LlamaProperties props, GgufRepository repository) {
        ModelRegistry registry = new ModelRegistry();
        this.modelRegistry = registry;
        List<LlamaProperties.ModelConfig> models = props.getModels();

        if (models == null || models.isEmpty()) {
            throw new IllegalStateException(
                "未配置任何模型。请在 application.yml 中配置 llama4j.models 列表");
        }

        for (LlamaProperties.ModelConfig mc : models) {
            String name = mc.getName();
            if (name == null || name.isBlank()) {
                throw new IllegalStateException("每个模型必须指定 name");
            }

            Model model = switch (mc.getType()) {
                case "local" -> createLocalModel(mc, repository);
                case "openai" -> createOpenAIModel(mc);
                default -> throw new IllegalStateException("未知的模型类型: " + mc.getType());
            };

            registry.register(name, model);
            LOG.info("已注册模型: {} ({})", name, model.getModelName());
        }

        // 设置默认模型
        String defaultName = props.getDefaultModel();
        if (defaultName != null && !defaultName.isBlank()) {
            registry.setDefaultModel(defaultName);
        }

        LOG.info("模型注册完成: {} 个模型, 默认: {}", registry.size(), registry.defaultModelName());
        return registry;
    }

    private Model createLocalModel(LlamaProperties.ModelConfig mc, GgufRepository repository) {
        String modelPath = resolveModelPath(mc, repository);
        ModelParams params = ModelParams.builder()
            .nCtx(mc.getNCtx())
            .nGpuLayers(mc.getNGpuLayers())
            .nThreads(mc.getNThreads())
            .build();
        LOG.info("加载本地模型: {} (nCtx={}, nGpuLayers={})", modelPath, mc.getNCtx(), mc.getNGpuLayers());
        return LocalModel.fromFile(modelPath, params);
    }

    private Model createOpenAIModel(LlamaProperties.ModelConfig mc) {
        OpenAIConfig config = OpenAIConfig.builder()
            .apiKey(mc.getApiKey())
            .baseUrl(mc.getBaseUrl())
            .modelName(mc.getModelName())
            .build();
        return new OpenAIModel(config);
    }

    private String resolveModelPath(LlamaProperties.ModelConfig mc, GgufRepository repository) {
        if (mc.getPath() != null && !mc.getPath().isBlank()) {
            return mc.getPath();
        }
        if (mc.getModelId() != null && !mc.getModelId().isBlank()) {
            Path resolved = repository.resolve(mc.getModelId());
            return resolved.toAbsolutePath().toString();
        }
        throw new IllegalStateException(
            "本地模型 " + mc.getName() + " 未配置 path 或 model-id");
    }

    /* ──────────────────────────────────────────
     *  通用 Bean
     *  ────────────────────────────────────────── */

    @Bean
    @ConditionalOnMissingBean
    ToolRegistry toolRegistry() {
        return new ToolRegistry();
    }

    @Bean
    @ConditionalOnMissingBean
    GgufRepository ggufRepository() {
        return new GgufRepository();
    }

    @Bean
    @ConditionalOnMissingBean
    LlamaMetrics llamaMetrics(MeterRegistry meterRegistry) {
        return new LlamaMetrics(meterRegistry);
    }

    /* ──────────────────────────────────────────
     *  API 安全
     *  ────────────────────────────────────────── */

    @Bean
    @ConditionalOnMissingBean(name = "apiKeyFilter")
    @ConditionalOnProperty(prefix = "llama4j.api", name = "key")
    FilterRegistrationBean<ApiKeyFilter> apiKeyFilter(LlamaProperties props) {
        String key = props.getApi().getKey();
        LOG.info("API 安全校验已启用");
        FilterRegistrationBean<ApiKeyFilter> registration = new FilterRegistrationBean<>();
        registration.setFilter(new ApiKeyFilter(key));
        registration.addUrlPatterns("/v1/*");
        registration.setOrder(1);
        return registration;
    }
}
