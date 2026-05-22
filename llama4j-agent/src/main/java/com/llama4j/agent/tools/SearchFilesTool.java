package com.llama4j.agent.tools;

import com.llama4j.tools.annotation.Tool;
import com.llama4j.tools.annotation.ToolParam;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

public class SearchFilesTool {

    private static final Logger LOG = LoggerFactory.getLogger(SearchFilesTool.class);
    private static final int MAX_RESULTS = 100;
    private static final int MAX_FILE_SIZE = 1_000_000; // 1MB
    private static final int MAX_PATTERN_LENGTH = 200;

    @Tool(name = "search_files", description = "Search for a text pattern in files (like grep). Returns matching lines with file paths and line numbers.")
    public String searchFiles(
        @ToolParam(description = "Search pattern (plain text or regex)") String pattern,
        @ToolParam(description = "Absolute path to the directory to search in") String path,
        @ToolParam(description = "File glob filter (e.g. '*.java', '*.py')", required = false) String glob
    ) {
        try {
            Path dirPath = Path.of(path);
            if (!Files.exists(dirPath)) {
                return "Error: Directory not found: " + path;
            }

            Pattern regex = compileSafePattern(pattern);
            PathMatcher matcher = (glob != null && !glob.isEmpty())
                ? FileSystems.getDefault().getPathMatcher("glob:" + glob)
                : null;

            StringBuilder sb = new StringBuilder();
            int[] matchCount = {0};

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
                    if (matchCount[0] >= MAX_RESULTS) return FileVisitResult.TERMINATE;
                    if (attrs.size() > MAX_FILE_SIZE) return FileVisitResult.CONTINUE;
                    if (matcher != null && !matcher.matches(file.getFileName())) return FileVisitResult.CONTINUE;

                    try (BufferedReader reader = Files.newBufferedReader(file)) {
                        String line;
                        int lineNum = 0;
                        while ((line = reader.readLine()) != null && matchCount[0] < MAX_RESULTS) {
                            lineNum++;
                            if (regex.matcher(line).find()) {
                                String relative = dirPath.relativize(file).toString();
                                sb.append(String.format("%s:%d: %s%n", relative, lineNum, line.trim()));
                                matchCount[0]++;
                            }
                        }
                    } catch (IOException e) {
                        // skip unreadable files
                    }
                    return FileVisitResult.CONTINUE;
                }
            });

            if (matchCount[0] == 0) {
                return "No matches found for pattern: " + pattern;
            }

            if (matchCount[0] >= MAX_RESULTS) {
                sb.append("... results truncated at ").append(MAX_RESULTS).append(" matches\n");
            }

            return sb.toString();
        } catch (IOException e) {
            LOG.error("Failed to search files: {}", path, e);
            return "Error searching files: " + e.getMessage();
        }
    }

    private Pattern compileSafePattern(String pattern) {
        if (pattern.length() > MAX_PATTERN_LENGTH) {
            throw new IllegalArgumentException("Pattern too long (max " + MAX_PATTERN_LENGTH + " chars)");
        }
        long repeats = pattern.chars().filter(c -> c == '*' || c == '+').count();
        if (repeats > 20) {
            throw new IllegalArgumentException("Pattern too complex: too many wildcards");
        }
        try {
            return Pattern.compile(pattern, Pattern.CASE_INSENSITIVE);
        } catch (PatternSyntaxException e) {
            return Pattern.compile(Pattern.quote(pattern), Pattern.CASE_INSENSITIVE);
        }
    }
}
