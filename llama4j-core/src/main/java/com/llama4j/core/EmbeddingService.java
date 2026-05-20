package com.llama4j.core;

import com.llama4j.native_.EmbeddingVector;
import com.llama4j.native_.LlamaContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/**
 * 嵌入向量服务 — 高级嵌入 API
 *
 * <p>在 {@link LlamaContext#embed} 基础上提供类型安全的嵌入操作，
 * 包括批量嵌入、相似度计算和最近邻搜索。</p>
 *
 * <h2>使用示例</h2>
 * <pre>{@code
 * try (LlamaContext ctx = new LlamaContext(modelPath, ModelParams.DEFAULT)) {
 *     EmbeddingService service = new EmbeddingService(ctx);
 *
 *     // 单文本嵌入
 *     EmbeddingVector vec = service.embed("你好世界");
 *
 *     // 批量嵌入 + 最近邻搜索
 *     List<String> docs = List.of("猫是宠物", "狗是宠物", "汽车是交通工具");
 *     List<SimilarityResult> results = service.findMostSimilar("动物", docs, 2);
 *     results.forEach(r -> System.out.println(r.text() + ": " + r.score()));
 * }
 * }</pre>
 */
public final class EmbeddingService {

    private static final Logger LOG = LoggerFactory.getLogger(EmbeddingService.class);

    private final LlamaContext context;

    public EmbeddingService(LlamaContext context) {
        this.context = Objects.requireNonNull(context, "LlamaContext 不能为 null");
    }

    /**
     * 生成单个文本的嵌入向量。
     *
     * @param text 输入文本
     * @return 嵌入向量
     */
    public EmbeddingVector embed(String text) {
        Objects.requireNonNull(text, "文本不能为 null");
        float[] raw = context.embed(text);
        return new EmbeddingVector(text, raw);
    }

    /**
     * 批量生成多个文本的嵌入向量。
     *
     * @param texts 输入文本列表
     * @return 嵌入向量列表（与输入顺序一致）
     */
    public List<EmbeddingVector> embedBatch(List<String> texts) {
        Objects.requireNonNull(texts, "文本列表不能为 null");
        List<EmbeddingVector> results = new ArrayList<>(texts.size());
        for (String text : texts) {
            results.add(embed(text));
        }
        LOG.debug("批量嵌入完成: {} 条文本", texts.size());
        return results;
    }

    /**
     * 计算两段文本的余弦相似度。
     *
     * @param text1 第一段文本
     * @param text2 第二段文本
     * @return 余弦相似度 [-1, 1]
     */
    public double similarity(String text1, String text2) {
        EmbeddingVector v1 = embed(text1);
        EmbeddingVector v2 = embed(text2);
        return v1.cosineSimilarity(v2);
    }

    /**
     * 从候选文本中找出与查询最相似的 top-K 个结果。
     *
     * @param query     查询文本
     * @param candidates 候选文本列表
     * @param topK      返回的最大结果数
     * @return 按相似度降序排列的结果列表
     */
    public List<SimilarityResult> findMostSimilar(String query, List<String> candidates, int topK) {
        Objects.requireNonNull(query, "查询文本不能为 null");
        Objects.requireNonNull(candidates, "候选列表不能为 null");
        if (topK <= 0) throw new IllegalArgumentException("topK 必须为正数");

        EmbeddingVector queryVec = embed(query);
        List<SimilarityResult> all = new ArrayList<>(candidates.size());
        for (String candidate : candidates) {
            EmbeddingVector candVec = embed(candidate);
            double score = queryVec.cosineSimilarity(candVec);
            all.add(new SimilarityResult(candidate, score));
        }

        all.sort(Comparator.comparingDouble(SimilarityResult::score).reversed());
        return all.subList(0, Math.min(topK, all.size()));
    }
}
