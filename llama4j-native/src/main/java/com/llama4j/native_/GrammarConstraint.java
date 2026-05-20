package com.llama4j.native_;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Objects;

/**
 * Grammar 约束采样器 — AutoCloseable 封装
 *
 * <p>包装 llama.cpp 原生 grammar sampler 的不透明指针，提供安全的生命周期管理。
 * 使用 try-with-resources 确保 sampler 资源正确释放。</p>
 *
 * <h2>使用示例</h2>
 * <pre>{@code
 * try (LlamaContext ctx = new LlamaContext(modelPath, ModelParams.DEFAULT);
 *      GrammarConstraint gc = GrammarConstraint.json(ctx)) {
 *     GenerateParams params = GenerateParams.builder("生成一个 JSON 对象")
 *         .grammar(gc)
 *         .maxTokens(256)
 *         .build();
 *     String result = ctx.generate(params);
 * }
 * }</pre>
 *
 * <h2>注意</h2>
 * <p>Grammar sampler 是有状态的——每次生成调用会推进其内部状态。
 * 每个 {@code GrammarConstraint} 实例应用于一次生成调用后应关闭并重新创建。</p>
 */
public final class GrammarConstraint implements AutoCloseable {

    private static final Logger LOG = LoggerFactory.getLogger(GrammarConstraint.class);

    /** JSON 模式的标准 GBNF 语法 */
    private static final String JSON_GBNF = """
        root   ::= arr | obj
        arr    ::= "[" ws ( value ("," ws value)* )? "]" ws
        obj    ::= "{" ws ( member ("," ws member)* )? "}" ws
        member ::= string ws ":" ws value
        value  ::= string | number | arr | obj | "true" | "false" | "null"
        string ::= '"' ( [^"\\\\] | '\\\\' . )* '"' ws
        number ::= "-"? integer fraction? exponent?
        integer ::= "0" | [1-9] [0-9]*
        fraction ::= "." [0-9]+
        exponent ::= ("e" | "E") ("+" | "-")? [0-9]+
        ws     ::= [ \\t\\n\\r]*
        """;

    /** 原生 grammar sampler 的不透明指针 */
    private long grammarHandle;

    /** 拥有此 sampler 的上下文，用于释放资源 */
    private final LlamaContext context;

    /** 关闭标志 */
    private volatile boolean closed = false;

    private GrammarConstraint(LlamaContext context, long grammarHandle) {
        this.context = context;
        this.grammarHandle = grammarHandle;
    }

    /**
     * 使用自定义 GBNF 语法创建 grammar 约束。
     *
     * @param context     模型上下文
     * @param grammarStr  GBNF 语法字符串
     * @param grammarRoot 根规则名称（通常为 "root"）
     * @return GrammarConstraint 实例
     */
    public static GrammarConstraint create(LlamaContext context, String grammarStr, String grammarRoot) {
        Objects.requireNonNull(context, "LlamaContext 不能为 null");
        Objects.requireNonNull(grammarStr, "grammarStr 不能为 null");
        Objects.requireNonNull(grammarRoot, "grammarRoot 不能为 null");
        long handle = context.createGrammar(grammarStr, grammarRoot);
        if (handle == 0) {
            throw new IllegalStateException("Grammar sampler 创建失败");
        }
        LOG.debug("GrammarConstraint 创建成功 (handle={})", handle);
        return new GrammarConstraint(context, handle);
    }

    /**
     * 创建 JSON 模式的 grammar 约束。
     *
     * <p>确保模型输出为合法的 JSON 格式。适用于需要结构化输出的场景，
     * 如 API 响应解析、数据提取等。</p>
     *
     * @param context 模型上下文
     * @return JSON 模式的 GrammarConstraint 实例
     */
    public static GrammarConstraint json(LlamaContext context) {
        return create(context, JSON_GBNF, "root");
    }

    /** 获取原生句柄（包私有，供 LlamaContext 内部使用） */
    long handle() {
        return grammarHandle;
    }

    /** @return 是否已关闭 */
    public boolean isClosed() {
        return closed;
    }

    @Override
    public void close() {
        if (!closed) {
            LOG.debug("释放 GrammarConstraint (handle={})", grammarHandle);
            context.freeGrammar(grammarHandle);
            grammarHandle = 0;
            closed = true;
        }
    }

    @Override
    protected void finalize() {
        if (!closed) {
            LOG.warn("GrammarConstraint 未显式关闭 — 在终结器中释放原生资源（请使用 try-with-resources）");
            close();
        }
    }
}
