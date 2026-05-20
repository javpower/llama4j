package com.llama4j.session;

import com.llama4j.native_.SessionState;

import java.util.Optional;

/**
 * 会话持久化后端抽象接口
 *
 * <p>定义了会话存储和检索的标准 API。不同的后端实现可以
 * 根据部署需求灵活替换：</p>
 * <ul>
 *   <li>{@link InMemorySessionStore} — 单实例部署，内存存储</li>
 *   <li>Redis 实现 — 分布式部署，跨实例共享会话</li>
 *   <li>文件系统实现 — 持久化存储，重启后恢复</li>
 * </ul>
 *
 * <p>接口设计遵循 Spring Data Repository 风格，便于与
 * Spring 生态集成。</p>
 */
public interface SessionStore {

    /**
     * 持久化一个新会话或更新已有会话。
     *
     * @param session 要保存的会话
     */
    void save(Session session);

    /**
     * 根据唯一标识符查找会话。
     *
     * @param id 会话 ID
     * @return 包含会话的 Optional（如果找到）
     */
    Optional<Session> findById(String id);

    /**
     * 更新会话的 KV 缓存状态。
     *
     * @param sessionId 会话 ID
     * @param state     新的 KV 缓存状态
     */
    void updateKvCache(String sessionId, SessionState state);

    /**
     * 删除指定会话。
     *
     * @param sessionId 要删除的会话 ID
     */
    void delete(String sessionId);

    /**
     * 检查会话是否存在（默认实现）。
     *
     * @param sessionId 会话 ID
     * @return 如果存在返回 true
     */
    default boolean exists(String sessionId) {
        return findById(sessionId).isPresent();
    }
}
