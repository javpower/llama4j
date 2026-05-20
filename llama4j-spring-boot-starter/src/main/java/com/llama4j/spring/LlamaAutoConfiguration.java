package com.llama4j.spring;

import com.llama4j.chat.ChatTemplateEngine;
import com.llama4j.core.ChatService;
import com.llama4j.metrics.LlamaMetrics;
import com.llama4j.native_.LlamaContext;
import com.llama4j.native_.ModelParams;
import com.llama4j.repo.GgufRepository;
import com.llama4j.tools.ToolEnabledChatService;
import com.llama4j.tools.ToolRegistry;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import jakarta.annotation.PreDestroy;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;

import java.nio.file.Path;

/**
 * llama4j Spring Boot 自动配置
 *
 * <p>当 starter 在 classpath 上且配置了模型路径或 ID 时，自动装配所有 llama4j 组件。
 * 遵循 Spring Boot 约定优于配置的原则，提供合理的默认值。</p>
 *
 * <h2>自动装配的 Bean</h2>
 * <ul>
 *   <li>{@link LlamaContext} — 原生模型上下文（单例，关闭时自动释放）</li>
 *   <li>{@link ChatTemplateEngine} — 模板渲染引擎</li>
 *   <li>{@link ChatService} — 高层聊天服务</li>
 *   <li>{@link ToolRegistry} — 函数调用工具注册表</li>
 *   <li>{@link ToolEnabledChatService} — 支持工具调用的聊天服务</li>
 *   <li>{@link GgufRepository} — GGUF 模型仓库</li>
 *   <li>{@link LlamaMetrics} — Micrometer 指标（需要 MeterRegistry）</li>
 *   <li>{@link LlamaHealthIndicator} — Actuator 健康检查</li>
 * </ul>
 *
 * <h2>条件装配</h2>
 * <p>所有 Bean 都标注了 {@code @ConditionalOnMissingBean}，允许用户
 * 通过自定义 Bean 覆盖默认实现。例如，可以提供自定义的 SessionStore
 * 来替换默认的 InMemorySessionStore。</p>
 */
@AutoConfiguration
@EnableConfigurationProperties(LlamaProperties.class)
@Import(LlamaEndpoint.class)
public class LlamaAutoConfiguration {

    private static final Logger LOG = LoggerFactory.getLogger(LlamaAutoConfiguration.class);

    private ChatService chatService;

    /**
     * 创建原生模型上下文 Bean。
     *
     * <p>根据配置加载模型，支持本地路径和 HuggingFace ID 两种方式。
     * Bean 的销毁方法设置为 close()，确保 Spring 容器关闭时释放原生资源。</p>
     */
    @Bean(destroyMethod = "close")
    @ConditionalOnMissingBean
    LlamaContext llamaContext(LlamaProperties props, GgufRepository repository) {
        String modelPath = resolveModelPath(props, repository);

        ModelParams params = ModelParams.builder()
            .nCtx(props.getModel().getNCtx())
            .nGpuLayers(props.getModel().getNGpuLayers())
            .nThreads(props.getModel().getNThreads())
            .build();

        LOG.info("正在加载模型: {} (参数: {})", modelPath, params);
        return new LlamaContext(modelPath, params);
    }

    /** 创建模板引擎 Bean */
    @Bean
    @ConditionalOnMissingBean
    ChatTemplateEngine chatTemplateEngine() {
        return new ChatTemplateEngine();
    }

    /** 创建聊天服务 Bean */
    @Bean
    @ConditionalOnMissingBean
    ChatService chatService(LlamaContext context, ChatTemplateEngine engine) {
        this.chatService = new ChatService(context, engine);
        return this.chatService;
    }

    @PreDestroy
    void shutdown() {
        if (chatService != null) {
            chatService.shutdown();
            LOG.info("ChatService 已优雅关闭");
        }
    }

    /** 创建工具注册表 Bean */
    @Bean
    @ConditionalOnMissingBean
    ToolRegistry toolRegistry() {
        return new ToolRegistry();
    }

    /** 创建支持工具调用的聊天服务 Bean */
    @Bean
    @ConditionalOnMissingBean
    ToolEnabledChatService toolEnabledChatService(ChatService chatService, ToolRegistry toolRegistry) {
        return new ToolEnabledChatService(chatService, toolRegistry);
    }

    /** 创建模型仓库 Bean */
    @Bean
    @ConditionalOnMissingBean
    GgufRepository ggufRepository() {
        return new GgufRepository();
    }

    /** 创建指标收集器 Bean（需要 Micrometer MeterRegistry） */
    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnClass(MeterRegistry.class)
    LlamaMetrics llamaMetrics(MeterRegistry meterRegistry) {
        return new LlamaMetrics(meterRegistry);
    }

    /** 创建健康检查指示器 Bean */
    @Bean
    @ConditionalOnMissingBean
    LlamaHealthIndicator llamaHealthIndicator(LlamaContext context) {
        return new LlamaHealthIndicator(context);
    }

    /* ──────────────────────────────────────────
     *  内部辅助方法
     * ────────────────────────────────────────── */

    /** 解析模型路径 — 优先使用本地路径，回退到 HuggingFace ID */
    private String resolveModelPath(LlamaProperties props, GgufRepository repository) {
        if (props.getModel().getPath() != null && !props.getModel().getPath().isBlank()) {
            return props.getModel().getPath();
        }

        if (props.getModel().getId() != null && !props.getModel().getId().isBlank()) {
            Path resolved = repository.resolve(props.getModel().getId());
            return resolved.toAbsolutePath().toString();
        }

        throw new IllegalStateException(
            "未配置模型。请设置 llama4j.model.path 或 llama4j.model.id");
    }
}
