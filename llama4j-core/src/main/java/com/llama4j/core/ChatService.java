package com.llama4j.core;

import com.llama4j.chat.ChatTemplateEngine;
import com.llama4j.chat.Message;
import com.llama4j.exception.InferenceException;
import com.llama4j.native_.GenerateParams;
import com.llama4j.native_.LlamaContext;
import com.llama4j.native_.TokenCallback;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.regex.Pattern;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 高层聊天服务 — 模型推理与对话模板的编排中心
 *
 * <p>本类是大多数用户的主要 API 入口。它封装了底层 {@link LlamaContext}
 * 的原生调用，并在此基础上添加了对话模板渲染、会话管理和流式支持。
 * 完整的推理管线为：消息列表 → 模板渲染 → 分词 → 推理 → 响应构建。</p>
 *
 * <h2>核心职责</h2>
 * <ol>
 *   <li><strong>模板渲染</strong> — 将多轮对话消息通过 {@link ChatTemplateEngine}
 *       渲染为模型可理解的单一提示词字符串</li>
 *   <li><strong>推理编排</strong> — 调用原生层执行推理，收集生成结果</li>
 *   <li><strong>流式支持</strong> — 通过 {@link ExecutorService} 异步执行推理，
 *       逐 token 回调监听器</li>
 *   <li><strong>统计收集</strong> — 记录每次推理的性能指标</li>
 * </ol>
 *
 * <h2>使用示例</h2>
 * <pre>{@code
 * ChatService service = new ChatService(context, templateEngine);
 *
 * // 同步调用
 * ChatResponse response = service.chat(ChatRequest.builder()
 *     .system("你是一个有帮助的AI助手。")
 *     .addMessage(Role.USER, "你好！")
 *     .build());
 * System.out.println(response.content());
 *
 * // 流式调用
 * service.chatStream(request, new ChatStreamListener() {
 *     public void onToken(String token) { System.out.print(token); }
 *     public void onComplete(ChatResponse r) { System.out.println("\n完成！"); }
 *     public void onError(Throwable e) { e.printStackTrace(); }
 * });
 * }</pre>
 */
public final class ChatService {

    private static final Logger LOG = LoggerFactory.getLogger(ChatService.class);

    /** 匹配多轮对话中的回合边界标记，用于清理小模型的幻觉输出 */
    private static final Pattern TURN_BOUNDARY = Pattern.compile(
        "<\\|im_end\\|>|<\\|im_start\\|>|<\\|eot_id\\|>|<\\|start_header_id\\|>|\\[/INST\\]|\\[INST\\]"
    );

    /** 原生推理上下文 */
    private final LlamaContext context;

    /** 对话模板引擎 — 负责将消息列表渲染为提示词 */
    private final ChatTemplateEngine templateEngine;

    /** 推理统计收集器 */
    private final InferenceStats stats;

    /** 流式推理的异步执行器 */
    private final ExecutorService streamExecutor;

    /**
     * 创建 ChatService 实例。
     *
     * @param context        已加载的原生推理上下文
     * @param templateEngine 对话模板引擎
     */
    public ChatService(LlamaContext context, ChatTemplateEngine templateEngine) {
        this.context = Objects.requireNonNull(context, "LlamaContext 不能为 null");
        this.templateEngine = Objects.requireNonNull(templateEngine, "ChatTemplateEngine 不能为 null");
        this.stats = new InferenceStats();
        this.streamExecutor = Executors.newFixedThreadPool(
            Runtime.getRuntime().availableProcessors(),
            new DaemonThreadFactory("llama4j-stream"));
        LOG.info("ChatService 初始化完成");
    }

    /* ──────────────────────────────────────────
     *  同步推理
     *  ────────────────────────────────────────── */

    /**
     * 执行同步聊天补全。
     *
     * <p>工作流程：</p>
     * <ol>
     *   <li>通过模板引擎将消息列表渲染为提示词字符串</li>
     *   <li>构造 GenerateParams 并调用原生层推理</li>
     *   <li>收集生成结果和性能指标</li>
     *   <li>构建并返回 ChatResponse</li>
     * </ol>
     *
     * @param request 聊天请求
     * @return 聊天响应
     * @throws InferenceException 如果推理失败
     */
    public ChatResponse chat(ChatRequest request) {
        Objects.requireNonNull(request, "请求不能为 null");
        long startTime = System.currentTimeMillis();

        // 步骤 1：渲染对话模板
        String prompt = renderPrompt(request.messages());
        LOG.debug("渲染后的提示词长度: {} 字符", prompt.length());

        // 步骤 2：构造生成参数
        GenerateParams params = GenerateParams.builder(prompt)
            .maxTokens(request.maxTokens())
            .temperature(request.temperature())
            .topK(request.topK())
            .topP(request.topP())
            .repeatPenalty(request.repeatPenalty())
            .seed(request.seed())
            .build();

        // 步骤 3：执行推理
        String result = context.generate(params);
        long latencyMs = System.currentTimeMillis() - startTime;

        // 步骤 4：后处理 — 清理小模型可能生成的多轮对话幻觉
        result = stripHallucinatedTurns(result);

        // 步骤 5：构建响应
        int promptTokens = context.tokenize(prompt, true).length;
        int completionTokens = context.tokenize(result, false).length;
        double tps = latencyMs > 0 ? (double) completionTokens / (latencyMs / 1000.0) : 0.0;

        ChatResponse response = ChatResponse.of(result, promptTokens, completionTokens, tps, latencyMs);

        // 记录统计
        stats.recordInference(promptTokens, completionTokens, tps, latencyMs);
        LOG.info("推理完成: {} 补全token, {}ms, {} tok/s", completionTokens, latencyMs, String.format("%.1f", tps));

        return response;
    }

    /* ──────────────────────────────────────────
     *  流式推理
     *  ────────────────────────────────────────── */

    /**
     * 执行流式聊天补全。
     *
     * <p>推理在独立线程上异步执行，每生成一个 token 就通过
     * {@link ChatStreamListener#onToken} 回调。生成完成后调用
     * {@link ChatStreamListener#onComplete}，出错时调用
     * {@link ChatStreamListener#onError}。</p>
     *
     * @param request  聊天请求
     * @param listener 流式监听器
     * @return CompletableFuture，在推理完成时完成
     */
    public CompletableFuture<ChatResponse> chatStream(ChatRequest request, ChatStreamListener listener) {
        Objects.requireNonNull(request, "请求不能为 null");
        Objects.requireNonNull(listener, "监听器不能为 null");

        return CompletableFuture.supplyAsync(() -> {
            try {
                long startTime = System.currentTimeMillis();
                String prompt = renderPrompt(request.messages());

                // 用于收集所有生成的 token
                StringBuilder resultBuilder = new StringBuilder();

                GenerateParams params = GenerateParams.builder(prompt)
                    .maxTokens(request.maxTokens())
                    .temperature(request.temperature())
                    .topK(request.topK())
                    .topP(request.topP())
                    .repeatPenalty(request.repeatPenalty())
                    .seed(request.seed())
                    .build();

                // 执行流式推理，逐 token 回调
                context.generateStream(params, token -> {
                    resultBuilder.append(token);
                    listener.onToken(token);
                });

                // 构建最终响应
                long latencyMs = System.currentTimeMillis() - startTime;
                String result = stripHallucinatedTurns(resultBuilder.toString());
                int promptTokens = context.tokenize(prompt, true).length;
                int completionTokens = context.tokenize(result, false).length;
                double tps = latencyMs > 0 ? (double) completionTokens / (latencyMs / 1000.0) : 0.0;

                ChatResponse response = ChatResponse.of(result, promptTokens, completionTokens, tps, latencyMs);
                stats.recordInference(promptTokens, completionTokens, tps, latencyMs);
                listener.onComplete(response);

                return response;
            } catch (Exception e) {
                listener.onError(e);
                throw new InferenceException("流式推理失败", e);
            }
        }, streamExecutor);
    }

    /* ──────────────────────────────────────────
     *  内部辅助方法
     *  ────────────────────────────────────────── */

    /**
     * 清理小模型可能幻觉生成的多轮对话标记。
     *
     * <p>小型模型（如 1.5B）在生成时可能会"角色扮演"出完整的对话回合，
     * 在输出中包含下一轮的起始标记。本方法在第一个回合边界标记处截断，
     * 保留有效内容。</p>
     */
    private static String stripHallucinatedTurns(String text) {
        var matcher = TURN_BOUNDARY.matcher(text);
        if (matcher.find()) {
            return text.substring(0, matcher.start()).trim();
        }
        return text;
    }

    /**
     * 通过模板引擎将消息列表渲染为提示词字符串。
     *
     * <p>模板引擎会自动检测模型内嵌的对话模板格式（Llama 3、ChatML 等），
     * 并使用对应的格式化规则将消息列表转换为模型可理解的输入。</p>
     */
    private String renderPrompt(List<Message> messages) {
        String chatTemplate = context.getChatTemplate();
        return templateEngine.renderConversation(chatTemplate, messages);
    }

    /* ──────────────────────────────────────────
     *  访问器
     *  ────────────────────────────────────────── */

    /** 获取推理统计收集器 */
    public InferenceStats getStats() {
        return stats;
    }

    /** 获取底层原生上下文 */
    public LlamaContext getContext() {
        return context;
    }

    /* ──────────────────────────────────────────
     *  生命周期
     *  ────────────────────────────────────────── */

    /** 关闭流式执行器，释放资源 */
    public void shutdown() {
        streamExecutor.shutdownNow();
        LOG.info("ChatService 已关闭");
    }

    /* ──────────────────────────────────────────
     *  内部类
     *  ────────────────────────────────────────── */

    /**
     * 守护线程工厂 — 创建的线程不会阻止 JVM 退出
     */
    private static final class DaemonThreadFactory implements ThreadFactory {
        private final AtomicInteger counter = new AtomicInteger(0);
        private final String namePrefix;

        DaemonThreadFactory(String namePrefix) {
            this.namePrefix = namePrefix;
        }

        @Override
        public Thread newThread(Runnable r) {
            Thread t = new Thread(r, namePrefix + "-" + counter.incrementAndGet());
            t.setDaemon(true); // 守护线程：JVM 退出时自动终止
            return t;
        }
    }
}
