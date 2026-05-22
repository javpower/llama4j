package com.llama4j.web.service;

import com.llama4j.agent.CliAgent;
import com.llama4j.agent.agent.ContextLoader;
import com.llama4j.agent.agent.PermissionManager;
import com.llama4j.agent.agent.SystemPromptBuilder;
import com.llama4j.chat.Message;
import com.llama4j.chat.Role;
import com.llama4j.core.*;
import com.llama4j.tools.ToolRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.nio.file.Path;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

@Service
public class AgentSessionManager {

    private static final Logger LOG = LoggerFactory.getLogger(AgentSessionManager.class);
    private static final int MAX_SESSIONS = 50;

    private final Map<String, AgentSession> sessions = new ConcurrentHashMap<>();

    public AgentSession createSession(CliAgent agent, String workspacePath) {
        String sessionId = "session_" + UUID.randomUUID().toString();

        Path workDir = Path.of(workspacePath);
        ContextLoader contextLoader = new ContextLoader(workDir);
        SystemPromptBuilder promptBuilder = new SystemPromptBuilder(agent.getModelName(), workDir);

        Map<String, String> contextFiles = contextLoader.loadContext();
        String systemPrompt = promptBuilder.build(contextFiles);

        AgentSession session = new AgentSession(
            sessionId, agent, systemPrompt, new CopyOnWriteArrayList<>(),
            new PermissionManager(agent.getConfig()), workspacePath, Instant.now()
        );

        // Evict oldest session if at capacity
        synchronized (this) {
            if (sessions.size() >= MAX_SESSIONS) {
                Optional<Map.Entry<String, AgentSession>> oldest = sessions.entrySet().stream()
                    .min(Comparator.comparingLong(e -> e.getValue().createdAt().toEpochMilli()));
                oldest.ifPresent(entry -> {
                    sessions.remove(entry.getKey());
                    LOG.info("Evicted oldest session {} to make room", entry.getKey());
                });
            }
            sessions.put(sessionId, session);
        }

        LOG.info("Agent session created: {}", sessionId);
        return session;
    }

    public AgentSession getSession(String sessionId) {
        return sessions.get(sessionId);
    }

    public List<Map<String, Object>> listSessions() {
        List<Map<String, Object>> list = new ArrayList<>();
        for (AgentSession session : sessions.values()) {
            list.add(Map.of(
                "id", session.id(),
                "workspace", session.workspacePath(),
                "messageCount", session.history().size(),
                "createdAt", session.createdAt().toString()
            ));
        }
        return list;
    }

    public void deleteSession(String sessionId) {
        sessions.remove(sessionId);
    }

    public ChatRequest buildRequest(AgentSession session, String userMessage) {
        if (session.history().size() > session.agent().getConfig().historyLimit()) {
            session.compact();
        }

        List<Message> messages = new ArrayList<>();
        messages.add(Message.system(session.systemPrompt()));
        messages.addAll(session.history());
        messages.add(Message.user(userMessage));

        return ChatRequest.builder()
            .messages(messages)
            .temperature(session.agent().getConfig().temperature())
            .maxTokens(session.agent().getConfig().maxTokens())
            .build();
    }

    public record AgentSession(
        String id,
        CliAgent agent,
        String systemPrompt,
        List<Message> history,
        PermissionManager permissions,
        String workspacePath,
        Instant createdAt
    ) {
        public void addToHistory(Message message) {
            history.add(message);
        }

        public void compact() {
            if (history.size() <= 10) return;
            // Keep first 2 and last 6 messages, summarize the rest
            List<Message> compacted = new ArrayList<>();
            compacted.addAll(history.subList(0, 2));
            compacted.add(Message.system("[Conversation compacted: " + (history.size() - 8) + " messages summarized]"));
            compacted.addAll(history.subList(history.size() - 6, history.size()));
            history.clear();
            history.addAll(compacted);
        }
    }
}
