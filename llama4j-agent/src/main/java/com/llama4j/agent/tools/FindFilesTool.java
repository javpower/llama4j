package com.llama4j.agent.tools;

import com.llama4j.tools.annotation.Tool;
import com.llama4j.tools.annotation.ToolParam;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.PatternSyntaxException;

public class FindFilesTool {

    private static final Logger LOG = LoggerFactory.getLogger(FindFilesTool.class);
    private static final int MAX_RESULTS = 200;
    private static final int MAX_PATTERN_LENGTH = 200;

    @Tool(name = "find_files", description = "Find files matching a glob pattern (e.g. '*.java', '**/*.test.ts'). Returns matching file paths.")
    public String findFiles(
        @ToolParam(description = "Glob pattern to match (e.g. '*.java', '**/*.xml')") String pattern,
        @ToolParam(description = "Absolute path to the directory to search in") String path
    ) {
        try {
            Path dirPath = Path.of(path);
            if (!Files.exists(dirPath)) {
                return "Error: Directory not found: " + path;
            }

            if (pattern.length() > MAX_PATTERN_LENGTH) {
                return "Error: Pattern too long (max " + MAX_PATTERN_LENGTH + " chars)";
            }

            PathMatcher matcher;
            try {
                matcher = FileSystems.getDefault().getPathMatcher("glob:" + pattern);
            } catch (PatternSyntaxException e) {
                return "Error: Invalid glob pattern: " + e.getMessage();
            }
            List<String> results = new ArrayList<>();

            Files.walkFileTree(dirPath, new SimpleFileVisitor<>() {
                @Override
                public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
                    String name = dir.getFileName().toString();
                    if (name.equals(".git") || name.equals("node_modules") || name.equals("target")) {
                        return FileVisitResult.SKIP_SUBTREE;
                    }
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                    if (results.size() >= MAX_RESULTS) return FileVisitResult.TERMINATE;
                    if (matcher.matches(file.getFileName()) || matcher.matches(dirPath.relativize(file))) {
                        results.add(dirPath.relativize(file).toString());
                    }
                    return FileVisitResult.CONTINUE;
                }
            });

            if (results.isEmpty()) {
                return "No files found matching pattern: " + pattern;
            }

            StringBuilder sb = new StringBuilder();
            for (String r : results) {
                sb.append(r).append("\n");
            }
            if (results.size() >= MAX_RESULTS) {
                sb.append("... results truncated at ").append(MAX_RESULTS).append(" files\n");
            }
            return sb.toString();
        } catch (IOException e) {
            LOG.error("Failed to find files: {}", path, e);
            return "Error finding files: " + e.getMessage();
        }
    }
}
