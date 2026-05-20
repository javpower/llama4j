package com.llama4j.chat;

import com.llama4j.chat.format.*;
import com.llama4j.chat.jinja.TemplateParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

/**
 * 对话模板检测与渲染引擎
 *
 * <p>本引擎是从原始 GGUF chat template 字符串到最终提示词的完整管线编排器。
 * 它支持两种渲染策略：</p>
 * <ol>
 *   <li><strong>Jinja2 渲染</strong> — 对于嵌入了 Jinja2 模板的模型，
 *       使用内置的 {@link TemplateParser} 直接渲染模板</li>
 *   <li><strong>格式化渲染</strong> — 对于模板匹配已知格式的模型，
 *       使用对应的 {@link ChatFormat} 渲染对话</li>
 * </ol>
 *
 * <h2>自动检测流程</h2>
 * <pre>
 * GGUF chat template 字符串
 *     │
 *     ├── 遍历所有注册的 ChatFormat
 *     │   └── 某个 format.matches(template) == true → 使用该格式渲染
 *     │
 *     ├── 没有匹配的格式，但模板看起来像 Jinja2 → 使用 TemplateParser 渲染
 *     │
 *     └── 模板为空或无法识别 → 使用 DefaultFormat 兜底渲染
 * </pre>
 *
 * <h2>使用示例</h2>
 * <pre>{@code
 * ChatTemplateEngine engine = new ChatTemplateEngine();
 * String prompt = engine.renderConversation(chatTemplate, messages);
 * }</pre>
 */
public final class ChatTemplateEngine {

    private static final Logger LOG = LoggerFactory.getLogger(ChatTemplateEngine.class);

    /**
     * 已注册的格式列表 — 按优先级排序。
     *
     * <p>检测时按列表顺序遍历，第一个匹配的格式会被使用。
     * 因此，更具体的格式（如 Llama3Format）应排在更通用的格式前面。</p>
     */
    private final List<ChatFormat> formats;

    /**
     * 创建引擎，注册所有内置格式。
     *
     * <p>注册顺序即为检测优先级。Llama3 排在最前面，因为它的标记
     * 最具特征性；DefaultFormat 排在最后，因为它永远不主动匹配。</p>
     */
    public ChatTemplateEngine() {
        this.formats = List.of(
            new Llama3Format(),    // Meta Llama 3 / 3.1
            new ChatMLFormat(),    // Qwen, Yi, DeepSeek V2
            new GemmaFormat(),     // Google Gemma 2
            new Phi3Format(),      // Microsoft Phi-3
            new MistralFormat(),   // Mistral, Mixtral
            new VicunaFormat(),    // Vicuna, LongChat
            new AlpacaFormat(),    // Alpaca, OpenAssistant
            new DeepSeekFormat(),  // DeepSeek Coder/V2
            new YiFormat(),        // Yi-34B, Yi-1.5
            new DefaultFormat()    // 兜底格式
        );
        LOG.info("已注册 {} 种对话格式: {}", formats.size(),
            formats.stream().map(ChatFormat::name).toList());
    }

    /**
     * 渲染对话消息列表为提示词字符串。
     *
     * <p>自动检测模板格式并选择最佳渲染策略。</p>
     *
     * @param chatTemplate GGUF 元数据中的 chat template 字符串（可能为空）
     * @param messages     对话消息列表
     * @return 格式化后的提示词字符串
     */
    public String renderConversation(String chatTemplate, List<Message> messages) {
        // 策略 1：尝试匹配已知格式
        if (chatTemplate != null && !chatTemplate.isBlank()) {
            for (ChatFormat format : formats) {
                if (format.matches(chatTemplate)) {
                    LOG.debug("匹配到对话格式: {}", format.name());
                    return format.render(messages);
                }
            }

            // 策略 2：使用 Jinja2 解析器渲染
            if (looksLikeJinja2(chatTemplate)) {
                LOG.debug("使用 Jinja2 解析器渲染模板");
                return renderWithJinja2(chatTemplate, messages);
            }
        }

        // 策略 3：兜底格式
        LOG.debug("无法识别模板格式，使用默认格式");
        return new DefaultFormat().render(messages);
    }

    /**
     * 获取所有已注册的格式名称。
     *
     * @return 格式名称列表
     */
    public List<String> getSupportedFormats() {
        return formats.stream().map(ChatFormat::name).toList();
    }

    /* ──────────────────────────────────────────
     *  内部辅助方法
     *  ────────────────────────────────────────── */

    /** 检查模板是否包含 Jinja2 特征标记 */
    private boolean looksLikeJinja2(String template) {
        return (template.contains("{{") && template.contains("}}"))
            || (template.contains("{%") && template.contains("%}"));
    }

    /** 使用 Jinja2 解析器渲染模板 */
    private String renderWithJinja2(String chatTemplate, List<Message> messages) {
        try {
            Map<String, Object> context = new HashMap<>();

            // 将消息列表转换为 Map 列表，供 Jinja2 模板访问
            List<Map<String, String>> messageMaps = new ArrayList<>();
            for (Message msg : messages) {
                Map<String, String> map = new LinkedHashMap<>();
                map.put("role", msg.role().value());
                map.put("content", msg.content());
                messageMaps.add(map);
            }

            // 构建模板上下文
            context.put("messages", messageMaps);
            context.put("bos_token", "");
            context.put("eos_token", "");
            context.put("add_generation_prompt", true);

            return TemplateParser.render(chatTemplate, context);
        } catch (Exception e) {
            LOG.warn("Jinja2 渲染失败，回退到默认格式: {}", e.getMessage());
            return new DefaultFormat().render(messages);
        }
    }
}
