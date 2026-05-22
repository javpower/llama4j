package com.llama4j.web.service;

import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.*;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Service
public class TerminalService {

    private static final Logger LOG = LoggerFactory.getLogger(TerminalService.class);
    private static final int MAX_SESSIONS = 10;

    private final Map<String, Process> sessions = new ConcurrentHashMap<>();
    private final ExecutorService executor = Executors.newCachedThreadPool(r -> {
        Thread t = new Thread(r, "terminal-io");
        t.setDaemon(true);
        return t;
    });

    public String createSession(String workspacePath) {
        if (sessions.size() >= MAX_SESSIONS) {
            throw new IllegalStateException("Maximum terminal sessions reached (" + MAX_SESSIONS + ")");
        }

        String sessionId = "term_" + UUID.randomUUID().toString();
        try {
            boolean isWindows = System.getProperty("os.name").toLowerCase().contains("win");
            String[] cmd = isWindows ? new String[]{"cmd.exe"} : new String[]{"bash", "--login"};

            ProcessBuilder pb = new ProcessBuilder(cmd);
            pb.directory(new File(workspacePath));
            pb.environment().put("TERM", "xterm-256color");
            pb.redirectErrorStream(true);

            Process process = pb.start();
            sessions.put(sessionId, process);
            LOG.info("Terminal session created: {} in {}", sessionId, workspacePath);
            return sessionId;
        } catch (IOException e) {
            throw new RuntimeException("Failed to create terminal: " + e.getMessage(), e);
        }
    }

    public Process getProcess(String sessionId) {
        return sessions.get(sessionId);
    }

    public void writeToProcess(String sessionId, String data) {
        Process process = sessions.get(sessionId);
        if (process != null && process.isAlive()) {
            try {
                OutputStream os = process.getOutputStream();
                os.write(data.getBytes());
                os.flush();
            } catch (IOException e) {
                LOG.error("Failed to write to terminal {}: {}", sessionId, e.getMessage());
            }
        }
    }

    public void resizeProcess(String sessionId, int cols, int rows) {
        if (sessions.containsKey(sessionId)) {
            writeToProcess(sessionId, String.format("stty cols %d rows %d 2>/dev/null\n", cols, rows));
        }
    }

    public void closeSession(String sessionId) {
        Process process = sessions.remove(sessionId);
        if (process != null) {
            destroyGracefully(process);
            LOG.info("Terminal session closed: {}", sessionId);
        }
    }

    public ExecutorService getExecutor() {
        return executor;
    }

    @PreDestroy
    public void closeAll() {
        sessions.forEach((id, process) -> destroyGracefully(process));
        sessions.clear();
        executor.shutdownNow();
    }

    private void destroyGracefully(Process process) {
        if (!process.isAlive()) return;
        process.destroy(); // SIGTERM
        try {
            if (!process.waitFor(3, java.util.concurrent.TimeUnit.SECONDS)) {
                process.destroyForcibly(); // SIGKILL
            }
        } catch (InterruptedException e) {
            process.destroyForcibly();
            Thread.currentThread().interrupt();
        }
    }
}
