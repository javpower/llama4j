package com.llama4j.session;

import com.llama4j.native_.SessionState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Objects;
import java.util.Optional;

/**
 * 基于内存的会话存储实现
 *
 * <p>使用 {@link java.util.concurrent.ConcurrentHashMap} 实现线程安全的
 * 会话存储。适用于单实例部署场景。对于分布式环境，应使用 Redis 等实现。</p>
 *
 * <h2>适用场景</h2>
 * <ul>
 *   <li>开发和测试环境</li>
 *   <li>单实例部署（无需跨进程共享会话）</li>
 *   <li>会话数量有限的场景</li>
 * </ul>
 *
 * <h2>不适用场景</h2>
 * <ul>
 *   <li>多实例部署（会话无法跨实例共享）</li>
 *   <li>需要持久化（重启后会话丢失）</li>
 *   <li>会话数量极大（内存压力）</li>
 * </ul>
 */
public class InMemorySessionStore implements SessionStore {

    private static final Logger LOG = LoggerFactory.getLogger(InMemorySessionStore.class);

    /** 线程安全的会话存储映射 */
    private final java.util.concurrent.ConcurrentHashMap<String, Session> store =
        new java.util.concurrent.ConcurrentHashMap<>();

    @Override
    public void save(Session session) {
        Objects.requireNonNull(session, "会话不能为 null");
        store.put(session.id(), session);
        LOG.debug("已保存会话: {}", session.id());
    }

    @Override
    public Optional<Session> findById(String id) {
        return Optional.ofNullable(store.get(id));
    }

    @Override
    public void updateKvCache(String sessionId, SessionState state) {
        // 使用 computeIfPresent 原子更新，避免并发问题
        store.computeIfPresent(sessionId, (id, existing) -> existing.withKvCacheState(state));
        LOG.debug("已更新会话 KV 缓存: {}", sessionId);
    }

    @Override
    public void delete(String sessionId) {
        Session removed = store.remove(sessionId);
        if (removed != null) {
            LOG.debug("已删除会话: {}", sessionId);
        }
    }

    /** 获取当前活跃会话数量 */
    public int size() {
        return store.size();
    }
}
