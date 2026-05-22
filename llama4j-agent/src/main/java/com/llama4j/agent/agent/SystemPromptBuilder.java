package com.llama4j.agent.agent;

import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;

public class SystemPromptBuilder {

    private final String modelIdentity;
    private final Path workDir;

    public SystemPromptBuilder(String modelIdentity, Path workDir) {
        this.modelIdentity = modelIdentity;
        this.workDir = workDir;
    }

    public String build(Map<String, String> contextFiles) {
        StringBuilder sb = new StringBuilder();

        sb.append("You are an AI coding assistant powered by ").append(modelIdentity);
        sb.append(". You help users write, edit, debug, and understand code.\n\n");

        sb.append("## Capabilities\n");
        sb.append("You have access to tools for reading, writing, and editing files, ");
        sb.append("searching the codebase, and running shell commands. ");
        sb.append("Use these tools proactively to help the user.\n\n");

        sb.append("## Guidelines\n");
        sb.append("- Read files before editing them to understand the current state\n");
        sb.append("- Make minimal, targeted changes rather than rewriting entire files\n");
        sb.append("- Explain what you plan to do before making changes\n");
        sb.append("- When running commands, prefer safe, non-destructive operations\n");
        sb.append("- If unsure about a destructive operation, ask the user first\n");
        sb.append("- Use search tools to find relevant code before making changes\n");
        sb.append("- After making changes, verify them by reading the modified file or running tests\n");
        sb.append("- Always use absolute file paths\n\n");

        sb.append("## Working Directory\n");
        sb.append(workDir.toAbsolutePath()).append("\n\n");

        sb.append("## Platform\n");
        sb.append(System.getProperty("os.name")).append(" ");
        sb.append(System.getProperty("os.arch")).append(" ");
        sb.append(System.getProperty("os.version")).append("\n");
        sb.append("Java ").append(System.getProperty("java.version")).append("\n\n");

        sb.append("## Current Date\n");
        sb.append(LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE)).append(" ");
        sb.append(LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm"))).append("\n");

        if (!contextFiles.isEmpty()) {
            sb.append("\n## Project Context\n");
            for (Map.Entry<String, String> entry : contextFiles.entrySet()) {
                sb.append("\n### ").append(entry.getKey()).append("\n");
                sb.append(entry.getValue()).append("\n");
            }
        }

        return sb.toString();
    }
}
