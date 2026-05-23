package com.llama4j.web.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.llama4j.agent.CliAgent;
import com.llama4j.core.ChatResponse;
import com.llama4j.tools.ToolCall;
import com.llama4j.tools.ToolResult;
import com.llama4j.tools.StreamingToolListener;
import com.llama4j.web.service.AgentSessionManager;
import com.llama4j.web.service.WorkspaceService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import jakarta.annotation.PreDestroy;
import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

@RestController
@RequestMapping("/api/agent")
public class AgentController {

    private static final Logger LOG = LoggerFactory.getLogger(AgentController.class);
    private static final int MAX_CONCURRENT_STREAMS = 4;

    private final AgentSessionManager sessionManager;
    private final WorkspaceService workspaceService;
    private final CliAgent cliAgent;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final ExecutorService executor = new ThreadPoolExecutor(
        1, MAX_CONCURRENT_STREAMS,
        60L, TimeUnit.SECONDS,
        new LinkedBlockingQueue<>(16),
        r -> {
            Thread t = new Thread(r, "agent-stream");
            t.setDaemon(true);
            return t;
        },
        new ThreadPoolExecutor.CallerRunsPolicy()
    );

    public AgentController(AgentSessionManager sessionManager, WorkspaceService workspaceService, CliAgent cliAgent) {
        this.sessionManager = sessionManager;
        this.workspaceService = workspaceService;
        this.cliAgent = cliAgent;
    }

    @PreDestroy
    public void shutdown() {
        LOG.info("Shutting down agent executor");
        executor.shutdownNow();
        try {
            if (!executor.awaitTermination(5, java.util.concurrent.TimeUnit.SECONDS)) {
                LOG.warn("Agent executor did not terminate within 5s");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    @PostMapping("/chat")
    public SseEmitter chat(@RequestBody Map<String, String> body) {
        SseEmitter emitter = new SseEmitter(300_000L); // 5 min timeout

        String message = body.get("message");
        String sessionId = body.get("sessionId");

        if (message == null || message.isBlank()) {
            try {
                sendEvent(emitter, "error", Map.of("message", "Message cannot be empty"));
            } catch (IOException ignored) {}
            emitter.complete();
            return emitter;
        }
        if (message.length() > 100_000) {
            try {
                sendEvent(emitter, "error", Map.of("message", "Message too long (max 100K characters)"));
            } catch (IOException ignored) {}
            emitter.complete();
            return emitter;
        }

        executor.submit(() -> {
            try {
                AgentSessionManager.AgentSession session;
                if (sessionId != null) {
                    session = sessionManager.getSession(sessionId);
                } else {
                    var ws = workspaceService.getCurrentWorkspace();
                    if (ws == null) {
                        sendEvent(emitter, "error", Map.of("message", "No active workspace. Please open a workspace first."));
                        emitter.complete();
                        return;
                    }
                    String wsPath = ws.path();
                    session = sessionManager.createSession(cliAgent, wsPath);
                }

                if (session == null) {
                    sendEvent(emitter, "error", Map.of("message", "Session not found"));
                    emitter.complete();
                    return;
                }

                // Send session info
                sendEvent(emitter, "session", Map.of("id", session.id()));

                session.addToHistory(com.llama4j.chat.Message.user(message));

                var request = sessionManager.buildRequest(session, message);

                session.agent().chatStream(request, new StreamingToolListener() {
                    private final StringBuilder content = new StringBuilder();
                    private volatile boolean aborted = false;

                    private boolean isActive() {
                        return !aborted;
                    }

                    private void abortStream(IOException e) {
                        if (!aborted) {
                            aborted = true;
                            LOG.warn("SSE emitter closed, aborting stream: {}", e.getMessage());
                            emitter.completeWithError(e);
                        }
                    }

                    @Override
                    public void onContentToken(String token) {
                        if (!isActive()) return;
                        try {
                            sendEvent(emitter, "content_delta", Map.of("token", token));
                            content.append(token);
                        } catch (IOException e) {
                            abortStream(e);
                        }
                    }

                    @Override
                    public void onToolCall(ToolCall toolCall) {
                        if (!isActive()) return;
                        try {
                            sendEvent(emitter, "tool_call", Map.of(
                                "id", toolCall.id(),
                                "name", toolCall.toolName(),
                                "arguments", toolCall.arguments()
                            ));
                        } catch (IOException e) {
                            abortStream(e);
                        }
                    }

                    @Override
                    public void onToolResult(ToolResult result) {
                        if (!isActive()) return;
                        try {
                            String resultContent = result.content();
                            if (resultContent.length() > 500) {
                                resultContent = resultContent.substring(0, 500) + "...";
                            }
                            sendEvent(emitter, "tool_result", Map.of(
                                "id", result.toolCallId(),
                                "success", result.success(),
                                "content", resultContent
                            ));
                        } catch (IOException e) {
                            abortStream(e);
                        }
                    }

                    @Override
                    public void onThinking(String partialContent) {
                        if (!isActive()) return;
                        try {
                            sendEvent(emitter, "thinking", Map.of("content", "Thinking..."));
                        } catch (IOException e) {
                            abortStream(e);
                        }
                    }

                    @Override
                    public void onComplete(ChatResponse response) {
                        if (!isActive()) return;
                        try {
                            session.addToHistory(com.llama4j.chat.Message.assistant(content.toString()));
                            sendEvent(emitter, "done", Map.of(
                                "promptTokens", response.promptTokens(),
                                "completionTokens", response.completionTokens(),
                                "tokensPerSecond", response.tokensPerSecond(),
                                "latencyMs", response.latencyMs()
                            ));
                            emitter.complete();
                        } catch (IOException e) {
                            abortStream(e);
                        }
                    }

                    @Override
                    public void onError(Throwable error) {
                        if (!isActive()) return;
                        try {
                            sendEvent(emitter, "error", Map.of("message", error.getMessage()));
                            emitter.complete();
                        } catch (IOException e) {
                            abortStream(e);
                        }
                    }
                }).join();

            } catch (Exception e) {
                LOG.error("Agent chat error", e);
                try {
                    sendEvent(emitter, "error", Map.of("message", e.getMessage()));
                    emitter.complete();
                } catch (IOException ex) {
                    emitter.completeWithError(ex);
                }
            }
        });

        return emitter;
    }

    @GetMapping("/sessions")
    public ResponseEntity<?> listSessions() {
        return ResponseEntity.ok(Map.of("sessions", sessionManager.listSessions()));
    }

    @PostMapping("/session/new")
    public ResponseEntity<?> newSession() {
        var ws = workspaceService.getCurrentWorkspace();
        if (ws == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "No active workspace"));
        }
        var session = sessionManager.createSession(cliAgent, ws.path());
        return ResponseEntity.ok(Map.of("id", session.id()));
    }

    @DeleteMapping("/session/{id}")
    public ResponseEntity<?> deleteSession(@PathVariable String id) {
        sessionManager.deleteSession(id);
        return ResponseEntity.ok(Map.of("success", true));
    }

    @PostMapping("/tools/approve")
    public ResponseEntity<?> approveTool(@RequestBody Map<String, Object> body) {
        String toolName = (String) body.get("toolName");
        boolean alwaysAllow = (Boolean) body.getOrDefault("alwaysAllow", false);
        // Permission approval is handled client-side before sending the request
        return ResponseEntity.ok(Map.of("success", true));
    }

    private void sendEvent(SseEmitter emitter, String eventName, Object data) throws IOException {
        emitter.send(SseEmitter.event()
            .name(eventName)
            .data(objectMapper.writeValueAsString(data)));
    }
}
