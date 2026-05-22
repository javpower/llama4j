package com.llama4j.web.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.llama4j.web.service.TerminalService;
import com.llama4j.web.service.WorkspaceService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.*;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.InputStream;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class TerminalWebSocketHandler extends TextWebSocketHandler {

    private static final Logger LOG = LoggerFactory.getLogger(TerminalWebSocketHandler.class);

    private final TerminalService terminalService;
    private final WorkspaceService workspaceService;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final Map<WebSocketSession, String> sessionTerminals = new ConcurrentHashMap<>();

    public TerminalWebSocketHandler(TerminalService terminalService, WorkspaceService workspaceService) {
        this.terminalService = terminalService;
        this.workspaceService = workspaceService;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        String wsPath = workspaceService.getCurrentWorkspace().path();
        String termId = terminalService.createSession(wsPath);
        sessionTerminals.put(session, termId);

        // Start reading from terminal process
        terminalService.getExecutor().submit(() -> {
            Process process = terminalService.getProcess(termId);
            if (process == null) return;

            try (InputStream is = process.getInputStream()) {
                byte[] buffer = new byte[4096];
                int len;
                while ((len = is.read(buffer)) != -1 && session.isOpen()) {
                    String output = new String(buffer, 0, len);
                    session.sendMessage(new TextMessage(objectMapper.writeValueAsString(
                        Map.of("type", "output", "data", output)
                    )));
                }
            } catch (Exception e) {
                LOG.error("Terminal read error", e);
            } finally {
                try {
                    if (session.isOpen()) {
                        session.sendMessage(new TextMessage(objectMapper.writeValueAsString(
                            Map.of("type", "exit", "code", 0)
                        )));
                    }
                } catch (Exception ignored) {}
            }
        });

        LOG.info("Terminal WebSocket connected: {}", termId);
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) {
        try {
            Map<String, Object> payload = objectMapper.readValue(message.getPayload(), Map.class);
            String type = (String) payload.get("type");
            String termId = sessionTerminals.get(session);

            if (termId == null) return;

            switch (type) {
                case "input" -> terminalService.writeToProcess(termId, (String) payload.get("data"));
                case "resize" -> {
                    int cols = ((Number) payload.get("cols")).intValue();
                    int rows = ((Number) payload.get("rows")).intValue();
                    terminalService.resizeProcess(termId, cols, rows);
                }
            }
        } catch (Exception e) {
            LOG.error("Terminal message error", e);
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        String termId = sessionTerminals.remove(session);
        if (termId != null) {
            terminalService.closeSession(termId);
            LOG.info("Terminal WebSocket disconnected: {}", termId);
        }
    }
}
