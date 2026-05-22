package com.llama4j.session;

import com.llama4j.native_.SessionState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Objects;
import java.util.Optional;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 基于内存的 LRU 会话存储实现
 *
 * <p>使用 {@link LinkedHashMap} 实现线程安全的 LRU 缓存。
 * 当会话数量达到上限时，自动淘汰最久未访问的会话。</p>
 *
 * <h2>适用场景</h2>
 * <ul>
 *   <li>开发和测试环境</li>
 *   <li>单实例部署（无需跨进程共享会话）</li>
 *   <li>会话数量有限的场景</li>
 * </ul>
 */
public class InMemorySessionStore implements SessionStore {

    private static final Logger LOG = LoggerFactory.getLogger(InMemorySessionStore.class);
    private static final int DEFAULT_MAX_SESSIONS = 1000;

    private final LinkedHashMap<String, Session> store;

    public InMemorySessionStore() {
        this(DEFAULT_MAX_SESSIONS);
    }

    public InMemorySessionStore(int maxSessions) {
        if (maxSessions <= 0) throw new IllegalArgumentException("maxSessions 必须为正数");
        this.store = new LinkedHashMap<>(16, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<String, Session> eldest) {
                if (size() > maxSessions) {
                    LOG.debug("LRU 淘汰会话: {}", eldest.getKey());
                    return true;
                }
                return false;
            }
        };
    }

    @Override
    public synchronized void save(Session session) {
        Objects.requireNonNull(session, "会话不能为 null");
        store.put(session.id(), session);
        LOG.debug("已保存会话: {}", session.id());
    }

    @Override
    public synchronized Optional<Session> findById(String id) {
        return Optional.ofNullable(store.get(id));
    }

    @Override
    public synchronized void updateKvCache(String sessionId, SessionState state) {
        store.computeIfPresent(sessionId, (id, existing) -> existing.withKvCacheState(state));
        LOG.debug("已更新会话 KV 缓存: {}", sessionId);
    }

    @Override
    public synchronized void delete(String sessionId) {
        Session removed = store.remove(sessionId);
        if (removed != null) {
            LOG.debug("已删除会话: {}", sessionId);
        }
    }

    /** 获取当前活跃会话数量 */
    public synchronized int size() {
        return store.size();
    }
}
