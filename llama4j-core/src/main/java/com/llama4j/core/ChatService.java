package com.llama4j.core;

import com.llama4j.chat.ChatTemplateEngine;
import com.llama4j.chat.Message;
import com.llama4j.chat.Role;
import com.llama4j.exception.InferenceException;
import com.llama4j.native_.GenerateParams;
import com.llama4j.native_.ImageData;
import com.llama4j.native_.LlamaContext;
import com.llama4j.native_.MultimodalContext;
import com.llama4j.native_.TokenCallback;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
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

    private static final Pattern TURN_BOUNDARY = Pattern.compile(
        "<\\|im_end\\|>|<\\|im_start\\|>|<\\|eot_id\\|>|<\\|start_header_id\\|>|" +
        "\\[/INST\\]|\\[INST\\]|\\[User\\]|\\[Assistant\\]|\\[System\\]|\\[Tool\\]"
    );

    /** 原生推理上下文 */
    private final LlamaContext context;

    /** 对话模板引擎 — 负责将消息列表渲染为提示词 */
    private final ChatTemplateEngine templateEngine;

    /** 推理统计收集器 */
    private final InferenceStats stats;

    /** 流式推理的异步执行器 */
    private final ExecutorService streamExecutor;

    /** 多模态推理上下文（null 表示纯文本模式） */
    private volatile MultimodalContext multimodalContext;

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

    /**
     * 启用多模态支持。
     *
     * @param mmprojPath mmproj 文件路径（通常与模型路径相同）
     */
    public void enableMultimodal(String mmprojPath) {
        this.multimodalContext = context.enableMultimodal(mmprojPath);
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

        // 多模态路径：检查是否有图片且多模态已启用
        boolean hasImages = !request.images().isEmpty()
            || request.messages().stream().anyMatch(Message::hasImages);

        if (hasImages && multimodalContext != null) {
            return chatMultimodal(request, startTime);
        }

        return chatTextOnly(request, startTime);
    }

    /** 纯文本推理（不走多模态路径，避免递归） */
    private ChatResponse chatTextOnly(ChatRequest request, long startTime) {
        String prompt = renderPrompt(request.messages());
        LOG.debug("渲染后的提示词长度: {} 字符", prompt.length());

        List<String> stopTokens = resolveStopTokens(request);
        GenerateParams params = GenerateParams.builder(prompt)
            .maxTokens(request.maxTokens())
            .temperature(request.temperature())
            .topK(request.topK())
            .topP(request.topP())
            .repeatPenalty(request.repeatPenalty())
            .seed(request.seed())
            .stopTokens(stopTokens)
            .grammar(request.grammar())
            .jsonMode(request.jsonMode())
            .build();

        String result = context.generate(params);
        return buildResponse(result, startTime);
    }

    /* ──────────────────────────────────────────
     *  多模态推理
     *  ────────────────────────────────────────── */

    private ChatResponse chatMultimodal(ChatRequest request, long startTime) {
        // 收集所有图片数据
        List<byte[]> imageBytes = new ArrayList<>();
        for (ImageData img : request.images()) {
            imageBytes.add(img.data());
        }
        for (Message msg : request.messages()) {
            for (var imgBlock : msg.imageBlocks()) {
                if (imgBlock.data() != null) {
                    imageBytes.add(imgBlock.data());
                }
            }
        }

        if (imageBytes.isEmpty()) {
            LOG.warn("多模态请求但没有图片数据，回退到文本模式");
            return chatTextOnly(request, startTime);
        }

        // 渲染 prompt，为包含图片的消息插入 <__media__> 标记
        int requestImageCount = (int) request.images().stream()
            .filter(img -> img.data() != null)
            .count();
        String prompt = renderMultimodalPrompt(request.messages(), requestImageCount);
        LOG.debug("多模态 prompt 长度: {}, 图片数: {}", prompt.length(), imageBytes.size());

        List<String> stopTokens = resolveStopTokens(request);
        GenerateParams params = GenerateParams.builder(prompt)
            .maxTokens(request.maxTokens())
            .temperature(request.temperature())
            .topK(request.topK())
            .topP(request.topP())
            .repeatPenalty(request.repeatPenalty())
            .seed(request.seed())
            .stopTokens(stopTokens)
            .build();

        byte[][] imageArray = imageBytes.toArray(new byte[0][]);
        String result = multimodalContext.generate(params, imageArray);
        LOG.info("多模态推理完成");
        return buildResponse(result, startTime);
    }

    /** 构建推理响应（共用逻辑：strip、统计、记录） */
    private ChatResponse buildResponse(String result, long startTime) {
        long latencyMs = System.currentTimeMillis() - startTime;
        result = stripHallucinatedTurns(result);
        int[] genStats = context.getGenerateStats();
        int promptTokens = genStats[0];
        int completionTokens = genStats[1];
        double tps = latencyMs > 0 ? (double) completionTokens / (latencyMs / 1000.0) : 0.0;

        ChatResponse response = ChatResponse.of(result, promptTokens, completionTokens, tps, latencyMs);
        stats.recordInference(promptTokens, completionTokens, tps, latencyMs);
        LOG.info("推理完成: {} 补全token, {}ms, {} tok/s", completionTokens, latencyMs, String.format("%.1f", tps));
        return response;
    }

    /**
     * 渲染多模态 prompt：在包含图片的 user 消息前插入 &lt;__media__&gt; 标记。
     *
     * @param messages 消息列表
     * @param totalImageCount 来自 request.images() 的图片总数，用于在最后一个 user 消息前插入标记
     */
    private String renderMultimodalPrompt(List<Message> messages, int totalImageCount) {
        // 找到最后一个 user 消息的索引
        int lastUserIdx = -1;
        for (int i = messages.size() - 1; i >= 0; i--) {
            if (messages.get(i).role() == Role.USER) {
                lastUserIdx = i;
                break;
            }
        }

        List<Message> modifiedMessages = new ArrayList<>();
        for (int i = 0; i < messages.size(); i++) {
            Message msg = messages.get(i);
            int markerCount = 0;
            if (msg.hasImages()) {
                markerCount += msg.imageBlocks().size();
            }
            // 来自 request.images() 的图片统一标记在最后一个 user 消息前
            if (i == lastUserIdx && totalImageCount > 0) {
                markerCount += totalImageCount;
            }
            if (markerCount > 0) {
                String mediaMarkers = "<__media__>\n".repeat(markerCount);
                String newContent = mediaMarkers + msg.content();
                modifiedMessages.add(new Message(msg.role(), newContent));
            } else {
                modifiedMessages.add(msg);
            }
        }
        return renderPrompt(modifiedMessages);
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

        boolean hasImages = !request.images().isEmpty()
            || request.messages().stream().anyMatch(Message::hasImages);

        if (hasImages && multimodalContext != null) {
            return chatStreamMultimodal(request, listener);
        }

        return CompletableFuture.supplyAsync(() -> {
            try {
                long startTime = System.currentTimeMillis();
                String prompt = renderPrompt(request.messages());

                StringBuilder resultBuilder = new StringBuilder();

                List<String> stopTokens = resolveStopTokens(request);
                GenerateParams params = GenerateParams.builder(prompt)
                    .maxTokens(request.maxTokens())
                    .temperature(request.temperature())
                    .topK(request.topK())
                    .topP(request.topP())
                    .repeatPenalty(request.repeatPenalty())
                    .seed(request.seed())
                    .stopTokens(stopTokens)
                    .grammar(request.grammar())
                    .jsonMode(request.jsonMode())
                    .build();

                context.generateStream(params, token -> {
                    resultBuilder.append(token);
                    listener.onToken(token);
                });

                String result = stripHallucinatedTurns(resultBuilder.toString());
                ChatResponse response = buildResponse(result, startTime);
                listener.onComplete(response);
                return response;
            } catch (Exception e) {
                listener.onError(e);
                throw new InferenceException("流式推理失败", e);
            }
        }, streamExecutor);
    }

    /** 多模态流式推理 */
    private CompletableFuture<ChatResponse> chatStreamMultimodal(ChatRequest request, ChatStreamListener listener) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                long startTime = System.currentTimeMillis();

                List<byte[]> imageBytes = new ArrayList<>();
                for (ImageData img : request.images()) {
                    imageBytes.add(img.data());
                }
                for (Message msg : request.messages()) {
                    for (var imgBlock : msg.imageBlocks()) {
                        if (imgBlock.data() != null) {
                            imageBytes.add(imgBlock.data());
                        }
                    }
                }

                if (imageBytes.isEmpty()) {
                    // 无图片数据，回退到文本流式
                    return chatStreamTextOnly(request, listener, startTime);
                }

                int requestImageCount = (int) request.images().stream()
                    .filter(img -> img.data() != null)
                    .count();
                String prompt = renderMultimodalPrompt(request.messages(), requestImageCount);

                List<String> stopTokens = resolveStopTokens(request);
                GenerateParams params = GenerateParams.builder(prompt)
                    .maxTokens(request.maxTokens())
                    .temperature(request.temperature())
                    .topK(request.topK())
                    .topP(request.topP())
                    .repeatPenalty(request.repeatPenalty())
                    .seed(request.seed())
                    .stopTokens(stopTokens)
                    .build();

                StringBuilder resultBuilder = new StringBuilder();
                byte[][] imageArray = imageBytes.toArray(new byte[0][]);
                multimodalContext.generateStream(params, imageArray, token -> {
                    resultBuilder.append(token);
                    listener.onToken(token);
                });

                String result = stripHallucinatedTurns(resultBuilder.toString());
                ChatResponse response = buildResponse(result, startTime);
                listener.onComplete(response);
                return response;
            } catch (Exception e) {
                listener.onError(e);
                throw new InferenceException("多模态流式推理失败", e);
            }
        }, streamExecutor);
    }

    /** 纯文本流式推理（不走多模态，避免递归） */
    private ChatResponse chatStreamTextOnly(ChatRequest request, ChatStreamListener listener, long startTime) {
        String prompt = renderPrompt(request.messages());
        StringBuilder resultBuilder = new StringBuilder();

        List<String> stopTokens = resolveStopTokens(request);
        GenerateParams params = GenerateParams.builder(prompt)
            .maxTokens(request.maxTokens())
            .temperature(request.temperature())
            .topK(request.topK())
            .topP(request.topP())
            .repeatPenalty(request.repeatPenalty())
            .seed(request.seed())
            .stopTokens(stopTokens)
            .build();

        context.generateStream(params, token -> {
            resultBuilder.append(token);
            listener.onToken(token);
        });

        String result = stripHallucinatedTurns(resultBuilder.toString());
        return buildResponse(result, startTime);
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
     * 并使用对应的格式化规则将消息列表转换为模型可理解的输入。
     * 优先使用 llama.cpp 内置模板引擎，失败时回退到 Java 实现。</p>
     *
     * @param messages 消息列表
     * @return 渲染后的提示词字符串
     */
    public String renderPrompt(List<Message> messages) {
        try {
            String[] roles = messages.stream().map(m -> m.role().value()).toArray(String[]::new);
            String[] contents = messages.stream().map(Message::content).toArray(String[]::new);
            return context.applyChatTemplate(roles, contents, true);
        } catch (Exception e) {
            LOG.warn("原生模板渲染失败，回退到 Java 模板引擎: {}", e.getMessage());
            String chatTemplate = context.getChatTemplate();
            return templateEngine.renderConversation(chatTemplate, messages);
        }
    }

    private List<String> resolveStopTokens(ChatRequest request) {
        List<String> tokens = new ArrayList<>(request.stopTokens());
        if (tokens.isEmpty()) {
            String chatTemplate = context.getChatTemplate();
            if (chatTemplate != null) {
                if (chatTemplate.contains("<|im_start|>")) {
                    tokens.add("<|im_end|>");
                } else if (chatTemplate.contains("<|eot_id|>")) {
                    tokens.add("<|eot_id|>");
                }
            }
        }
        return tokens;
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

    /** 关闭流式执行器和推理上下文，释放原生资源 */
    public void shutdown() {
        streamExecutor.shutdownNow();
        try {
            if (!streamExecutor.awaitTermination(5, java.util.concurrent.TimeUnit.SECONDS)) {
                LOG.warn("Stream executor did not terminate within 5s");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        if (multimodalContext != null) {
            multimodalContext.close();
        }
        context.close();
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
