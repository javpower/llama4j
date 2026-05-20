package com.llama4j.session;

import com.llama4j.native_.SessionState;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * 聊天会话 — 不可变对象
 *
 * <p>跟踪对话状态，包括消息历史、KV 缓存状态和元数据。
 * 每个会话有唯一 ID，可用于在分布式系统中关联对话上下文。</p>
 *
 * <h2>会话生命周期</h2>
 * <pre>
 * 创建 → 添加消息 → 推理 → 检查点(保存KV缓存) → 继续对话 → ... → 删除
 * </pre>
 *
 * <h2>不可变设计</h2>
 * <p>所有修改操作（touch、withKvCacheState）都返回新实例，
 * 而不是修改当前对象。这确保了在并发环境下的安全性。</p>
 *
 * @param id           唯一会话标识符
 * @param modelId      关联的模型标识符
 * @param createdAt    创建时间戳
 * @param lastActiveAt 最后活跃时间戳
 * @param kvCacheState 序列化的 KV 缓存状态（可能为 null）
 */
public record Session(
    String id,
    String modelId,
    Instant createdAt,
    Instant lastActiveAt,
    SessionState kvCacheState
) {

    public Session {
        Objects.requireNonNull(id, "会话 ID 不能为 null");
        Objects.requireNonNull(modelId, "模型 ID 不能为 null");
        Objects.requireNonNull(createdAt, "创建时间不能为 null");
    }

    /**
     * 创建新会话，自动生成 UUID 和当前时间戳。
     *
     * @param modelId 模型标识符
     * @return 新会话实例
     */
    public static Session create(String modelId) {
        Instant now = Instant.now();
        return new Session(UUID.randomUUID().toString(), modelId, now, now, null);
    }

    /** 更新最后活跃时间戳（返回新实例） */
    public Session touch() {
        return new Session(id, modelId, createdAt, Instant.now(), kvCacheState);
    }

    /** 更新 KV 缓存状态（返回新实例） */
    public Session withKvCacheState(SessionState state) {
        return new Session(id, modelId, createdAt, Instant.now(), state);
    }
}
