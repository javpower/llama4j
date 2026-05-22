package com.llama4j.agent.agent;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class ContextLoader {

    private static final Logger LOG = LoggerFactory.getLogger(ContextLoader.class);

    private static final List<String> DEFAULT_CONTEXT_FILES = List.of(
        "CLAUDE.md",
        "CLAUDE.local.md",
        ".claude/instructions.md"
    );

    private final Path workDir;

    public ContextLoader(Path workDir) {
        this.workDir = workDir;
    }

    public Map<String, String> loadContext() {
        Map<String, String> contexts = new LinkedHashMap<>();

        // Search in workDir
        for (String name : DEFAULT_CONTEXT_FILES) {
            Path file = workDir.resolve(name);
            String content = loadFile(file);
            if (!content.isEmpty()) {
                contexts.put(name, content);
            }
        }

        // Search in git root if different from workDir
        Path gitRoot = findGitRoot();
        if (gitRoot != null && !gitRoot.equals(workDir)) {
            for (String name : DEFAULT_CONTEXT_FILES) {
                if (!contexts.containsKey(name)) {
                    Path file = gitRoot.resolve(name);
                    String content = loadFile(file);
                    if (!content.isEmpty()) {
                        contexts.put(name, content);
                    }
                }
            }
        }

        // Search in user home
        Path homeDir = Path.of(System.getProperty("user.home"), ".claude");
        Path homeFile = homeDir.resolve("CLAUDE.md");
        if (!contexts.containsKey("CLAUDE.md")) {
            String content = loadFile(homeFile);
            if (!content.isEmpty()) {
                contexts.put("~/.claude/CLAUDE.md", content);
            }
        }

        return contexts;
    }

    private String loadFile(Path path) {
        try {
            if (Files.exists(path) && Files.isRegularFile(path)) {
                String content = Files.readString(path);
                if (content.length() > 10000) {
                    content = content.substring(0, 10000) + "\n... (truncated)";
                }
                return content;
            }
        } catch (IOException e) {
            LOG.warn("Failed to load context file: {}", path, e);
        }
        return "";
    }

    private Path findGitRoot() {
        Path current = workDir;
        while (current != null) {
            if (Files.exists(current.resolve(".git"))) {
                return current;
            }
            current = current.getParent();
        }
        return null;
    }
}
