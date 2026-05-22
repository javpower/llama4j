package com.llama4j.agent.tools;

import com.llama4j.tools.annotation.Tool;
import com.llama4j.tools.annotation.ToolParam;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

public class ReadFileTool {

    private static final Logger LOG = LoggerFactory.getLogger(ReadFileTool.class);
    private static final int MAX_LINES = 2000;
    private static final long MAX_FILE_SIZE = 10 * 1024 * 1024; // 10MB

    @Tool(name = "read_file", description = "Read the contents of a file with line numbers. Optionally specify offset and limit to read specific portions of large files.")
    public String readFile(
        @ToolParam(description = "Absolute path to the file to read") String path,
        @ToolParam(description = "Start line number (1-based)", type = "integer", required = false) int offset,
        @ToolParam(description = "Maximum number of lines to read", type = "integer", required = false) int limit
    ) {
        try {
            Path filePath = Path.of(path);
            if (!Files.exists(filePath)) {
                return "Error: File not found: " + path;
            }
            if (!Files.isRegularFile(filePath)) {
                return "Error: Not a regular file: " + path;
            }

            long fileSize = Files.size(filePath);
            if (fileSize > MAX_FILE_SIZE) {
                return "Error: File too large (" + fileSize + " bytes, max " + MAX_FILE_SIZE + "). Use offset and limit to read portions.";
            }

            int totalLines = countLines(filePath);
            int startLine = (offset > 0) ? Math.min(offset - 1, totalLines) : 0;
            int endLine = (limit > 0) ? Math.min(startLine + limit, totalLines) : totalLines;

            if (endLine - startLine > MAX_LINES) {
                endLine = startLine + MAX_LINES;
            }

            StringBuilder sb = new StringBuilder();
            try (BufferedReader reader = Files.newBufferedReader(filePath)) {
                int currentLine = 0;
                String line;
                while ((line = reader.readLine()) != null) {
                    if (currentLine >= endLine) break;
                    if (currentLine >= startLine) {
                        sb.append(String.format("%4d | %s%n", currentLine + 1, line));
                    }
                    currentLine++;
                }
            }

            if (startLine > 0 || endLine < totalLines) {
                sb.append(String.format("... showing lines %d-%d of %d total%n", startLine + 1, endLine, totalLines));
            }

            return sb.toString();
        } catch (IOException e) {
            LOG.error("Failed to read file: {}", path, e);
            return "Error reading file: " + e.getMessage();
        }
    }

    private int countLines(Path filePath) throws IOException {
        try (Stream<String> lines = Files.lines(filePath)) {
            return (int) lines.count();
        }
    }
}
