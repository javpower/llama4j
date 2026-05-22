package com.llama4j.web.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.*;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

@Service
public class FileService {

    private static final Logger LOG = LoggerFactory.getLogger(FileService.class);
    private static final int MAX_LINES = 5000;
    private static final int MAX_SEARCH_RESULTS = 200;
    private static final int MAX_FIND_RESULTS = 500;
    private static final int MAX_PATTERN_LENGTH = 200;
    private static final Set<String> SKIP_DIRS = Set.of(
        ".git", "node_modules", "__pycache__", "target", "build", "dist"
    );

    private static final Map<String, String> LANGUAGE_MAP = Map.ofEntries(
        Map.entry(".java", "java"), Map.entry(".py", "python"),
        Map.entry(".js", "javascript"), Map.entry(".ts", "typescript"),
        Map.entry(".jsx", "javascript"), Map.entry(".tsx", "typescript"),
        Map.entry(".html", "html"), Map.entry(".css", "css"),
        Map.entry(".json", "json"), Map.entry(".xml", "xml"),
        Map.entry(".yaml", "yaml"), Map.entry(".yml", "yaml"),
        Map.entry(".md", "markdown"), Map.entry(".sql", "sql"),
        Map.entry(".sh", "shell"), Map.entry(".bash", "shell"),
        Map.entry(".go", "go"), Map.entry(".rs", "rust"),
        Map.entry(".c", "c"), Map.entry(".cpp", "cpp"),
        Map.entry(".h", "c"), Map.entry(".hpp", "cpp"),
        Map.entry(".rb", "ruby"), Map.entry(".php", "php"),
        Map.entry(".swift", "swift"), Map.entry(".kt", "kotlin"),
        Map.entry(".scala", "scala"), Map.entry(".lua", "lua"),
        Map.entry(".r", "r"), Map.entry(".R", "r"),
        Map.entry(".toml", "toml"), Map.entry(".ini", "ini"),
        Map.entry(".properties", "properties"), Map.entry(".gradle", "groovy"),
        Map.entry(".vue", "html"), Map.entry(".svelte", "html")
    );

    public Map<String, Object> readFile(String workspacePath, String relativePath, int offset, int limit) {
        Path filePath = resolveAndValidate(workspacePath, relativePath);
        if (!Files.exists(filePath)) {
            throw new IllegalArgumentException("File not found: " + relativePath);
        }

        try {
            long fileSize = Files.size(filePath);
            if (fileSize > 50 * 1024 * 1024) {
                throw new IllegalArgumentException("File too large: " + fileSize + " bytes");
            }

            int start = Math.max(0, offset > 0 ? offset - 1 : 0);
            int readLimit = limit > 0 ? Math.min(limit, MAX_LINES) : MAX_LINES;

            StringBuilder content = new StringBuilder();
            int totalLines;
            try (BufferedReader reader = Files.newBufferedReader(filePath)) {
                String line;
                int currentLine = 0;
                int collected = 0;
                while ((line = reader.readLine()) != null) {
                    if (currentLine >= start && collected < readLimit) {
                        content.append(line).append("\n");
                        collected++;
                    }
                    currentLine++;
                }
                totalLines = currentLine;
            }

            String ext = getExtension(relativePath);
            String language = LANGUAGE_MAP.getOrDefault(ext, "plaintext");

            return Map.of(
                "content", content.toString(),
                "totalLines", totalLines,
                "language", language,
                "path", relativePath
            );
        } catch (IOException e) {
            throw new RuntimeException("Failed to read file: " + e.getMessage(), e);
        }
    }

    public void writeFile(String workspacePath, String relativePath, String content) {
        Path filePath = resolveAndValidate(workspacePath, relativePath);
        try {
            Path parent = filePath.getParent();
            if (parent != null) Files.createDirectories(parent);
            Files.writeString(filePath, content);
        } catch (IOException e) {
            throw new RuntimeException("Failed to write file: " + e.getMessage(), e);
        }
    }

    public Map<String, Object> editFile(String workspacePath, String relativePath, String oldText, String newText) {
        Path filePath = resolveAndValidate(workspacePath, relativePath);
        if (!Files.exists(filePath)) {
            throw new IllegalArgumentException("File not found: " + relativePath);
        }

        try {
            String content = Files.readString(filePath);
            int firstIdx = content.indexOf(oldText);
            if (firstIdx == -1) {
                return Map.of("success", false, "error", "oldText not found in file");
            }
            int lastIdx = content.lastIndexOf(oldText);
            if (firstIdx != lastIdx) {
                return Map.of("success", false, "error", "oldText matches multiple locations");
            }

            String newContent = content.substring(0, firstIdx) + newText + content.substring(firstIdx + oldText.length());
            Files.writeString(filePath, newContent);

            int lineNum = content.substring(0, firstIdx).split("\n").length;
            return Map.of("success", true, "line", lineNum);
        } catch (IOException e) {
            throw new RuntimeException("Failed to edit file: " + e.getMessage(), e);
        }
    }

    public List<Map<String, Object>> searchFiles(String workspacePath, String pattern, String glob, String searchPath) {
        Path dirPath = resolveAndValidate(workspacePath, searchPath != null ? searchPath : ".");
        List<Map<String, Object>> results = new ArrayList<>();

        try {
            Pattern regex = compileSafePattern(pattern);
            PathMatcher matcher = (glob != null && !glob.isEmpty())
                ? FileSystems.getDefault().getPathMatcher("glob:" + glob) : null;

            Files.walkFileTree(dirPath, new SimpleFileVisitor<>() {
                @Override
                public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
                    String name = dir.getFileName().toString();
                    return SKIP_DIRS.contains(name) ? FileVisitResult.SKIP_SUBTREE : FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                    if (results.size() >= MAX_SEARCH_RESULTS) return FileVisitResult.TERMINATE;
                    if (matcher != null && !matcher.matches(file.getFileName())) return FileVisitResult.CONTINUE;

                    try (BufferedReader reader = Files.newBufferedReader(file)) {
                        String line;
                        int lineNum = 0;
                        while ((line = reader.readLine()) != null && results.size() < MAX_SEARCH_RESULTS) {
                            lineNum++;
                            if (regex.matcher(line).find()) {
                                results.add(Map.of(
                                    "file", dirPath.relativize(file).toString(),
                                    "line", lineNum,
                                    "content", line.trim()
                                ));
                            }
                        }
                    } catch (IOException ignored) {}
                    return FileVisitResult.CONTINUE;
                }
            });
        } catch (IOException e) {
            throw new RuntimeException("Search failed: " + e.getMessage(), e);
        }
        return results;
    }

    public List<String> findFiles(String workspacePath, String pattern, String searchPath) {
        Path dirPath = resolveAndValidate(workspacePath, searchPath != null ? searchPath : ".");
        List<String> results = new ArrayList<>();

        try {
            PathMatcher matcher = FileSystems.getDefault().getPathMatcher("glob:" + pattern);
            Files.walkFileTree(dirPath, new SimpleFileVisitor<>() {
                @Override
                public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
                    String name = dir.getFileName().toString();
                    return SKIP_DIRS.contains(name) ? FileVisitResult.SKIP_SUBTREE : FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                    if (results.size() >= MAX_FIND_RESULTS) return FileVisitResult.TERMINATE;
                    if (matcher.matches(file.getFileName()) || matcher.matches(dirPath.relativize(file))) {
                        results.add(dirPath.relativize(file).toString());
                    }
                    return FileVisitResult.CONTINUE;
                }
            });
        } catch (IOException e) {
            throw new RuntimeException("Find failed: " + e.getMessage(), e);
        }
        return results;
    }

    private String getExtension(String path) {
        int dot = path.lastIndexOf('.');
        return dot >= 0 ? path.substring(dot) : "";
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

    /** Validate resolved path stays within workspace, resolving symlinks */
    private Path resolveAndValidate(String workspacePath, String relativePath) {
        try {
            Path workspace = Path.of(workspacePath).toAbsolutePath().toRealPath();
            Path resolved = workspace.resolve(relativePath).normalize();

            // For existing paths, resolve symlinks via toRealPath()
            if (Files.exists(resolved)) {
                resolved = resolved.toRealPath();
            } else {
                // For new files, validate the parent directory instead
                Path parent = resolved.getParent();
                if (parent != null && Files.exists(parent)) {
                    Path realParent = parent.toRealPath();
                    if (!realParent.startsWith(workspace)) {
                        throw new SecurityException("Path traversal blocked: " + relativePath);
                    }
                }
            }

            if (!resolved.startsWith(workspace)) {
                throw new SecurityException("Path traversal blocked: " + relativePath);
            }
            return resolved;
        } catch (IOException e) {
            throw new SecurityException("Path validation failed: " + e.getMessage());
        }
    }
}
