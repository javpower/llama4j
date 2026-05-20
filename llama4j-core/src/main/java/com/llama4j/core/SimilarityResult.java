package com.llama4j.core;

/**
 * 相似度搜索结果 — 不可变容器
 *
 * @param text  候选文本
 * @param score 与查询文本的相似度分数
 */
public record SimilarityResult(String text, double score) {
    public SimilarityResult {
        if (text == null) throw new IllegalArgumentException("文本不能为 null");
    }
}
