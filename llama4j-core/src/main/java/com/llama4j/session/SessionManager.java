package com.llama4j.session;

import com.llama4j.exception.Llama4jException;
import com.llama4j.native_.LlamaContext;
import com.llama4j.native_.SessionState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Objects;

/**
 * 会话生命周期管理器
 *
 * <p>提供会话创建、检查点和恢复的高层 API。协调 {@link SessionStore}
 * （持久化）和 {@link LlamaContext}（原生 KV 缓存）之间的交互。</p>
 *
 * <h2>完整生命周期</h2>
 * <ol>
 *   <li><strong>创建</strong> — {@link #createSession} 生成唯一 ID 并持久化</li>
 *   <li><strong>检查点</strong> — {@link #checkpoint} 保存当前 KV 缓存到存储</li>
 *   <li><strong>恢复</strong> — {@link #resumeSession} 从存储加载 KV 缓存到原生层</li>
 *   <li><strong>删除</strong> — {@link #deleteSession} 清理会话及其状态</li>
 * </ol>
 *
 * <h2>使用示例</h2>
 * <pre>{@code
 * SessionManager manager = new SessionManager(new InMemorySessionStore());
 *
 * // 创建会话
 * Session session = manager.createSession("qwen2.5-7b");
 *
 * // ... 进行多轮对话 ...
 *
 * // 保存检查点
 * manager.checkpoint(session.id(), context);
 *
 * // 稍后恢复
 * Session restored = manager.resumeSession(session.id(), context);
 * }</pre>
 */
public class SessionManager {

    private static final Logger LOG = LoggerFactory.getLogger(SessionManager.class);

    private final SessionStore store;

    /**
     * 创建会话管理器。
     *
     * @param store 会话持久化后端
     */
    public SessionManager(SessionStore store) {
        this.store = Objects.requireNonNull(store, "SessionStore 不能为 null");
    }

    /**
     * 创建新会话。
     *
     * @param modelId 模型标识符
     * @return 新创建的会话
     */
    public Session createSession(String modelId) {
        Session session = Session.create(modelId);
        store.save(session);
        LOG.info("已创建会话: {} (模型: {})", session.id(), modelId);
        return session;
    }

    /**
     * 恢复已有会话，将 KV 缓存加载到原生上下文。
     *
     * <p>恢复流程：</p>
     * <ol>
     *   <li>从存储中查找会话</li>
     *   <li>如果会话有 KV 缓存状态，加载到原生上下文</li>
     *   <li>更新最后活跃时间</li>
     * </ol>
     *
     * @param sessionId 会话 ID
     * @param context   原生上下文（用于恢复 KV 缓存）
     * @return 恢复的会话
     */
    public Session resumeSession(String sessionId, LlamaContext context) {
        Objects.requireNonNull(context, "LlamaContext 不能为 null");

        Session session = store.findById(sessionId)
            .orElseThrow(() -> new SessionNotFoundException(sessionId));

        if (session.kvCacheState() != null) {
            context.loadSession(session.kvCacheState());
            LOG.info("已恢复会话 KV 缓存: {} ({} 字节)",
                     sessionId, session.kvCacheState().size());
        } else {
            LOG.info("会话无 KV 缓存状态: {}，将从头开始", sessionId);
        }

        return session.touch();
    }

    /**
     * 保存检查点 — 将当前 KV 缓存状态持久化。
     *
     * @param sessionId 会话 ID
     * @param context   原生上下文（从中保存 KV 缓存）
     */
    public void checkpoint(String sessionId, LlamaContext context) {
        Objects.requireNonNull(context, "LlamaContext 不能为 null");

        if (!store.exists(sessionId)) {
            throw new SessionNotFoundException(sessionId);
        }

        SessionState kvState = context.saveSession();
        store.updateKvCache(sessionId, kvState);
        LOG.info("已保存检查点: {} ({} 字节)", sessionId, kvState.size());
    }

    /** 删除会话及其关联状态 */
    public void deleteSession(String sessionId) {
        store.delete(sessionId);
        LOG.info("已删除会话: {}", sessionId);
    }

    /** 根据 ID 查找会话，未找到则抛出异常 */
    public Session getSession(String sessionId) {
        return store.findById(sessionId)
            .orElseThrow(() -> new SessionNotFoundException(sessionId));
    }
}
