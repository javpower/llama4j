package com.llama4j.agent.tools;

import com.llama4j.tools.annotation.Tool;
import com.llama4j.tools.annotation.ToolParam;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Set;

public class ListFilesTool {

    private static final Logger LOG = LoggerFactory.getLogger(ListFilesTool.class);

    private static final Set<String> SKIP_DIRS = Set.of(
        ".git", "node_modules", "__pycache__", ".idea", ".vscode",
        "target", "build", "dist", ".gradle", ".mvn", "vendor"
    );

    @Tool(name = "list_files", description = "List files and directories in a tree-like view. Skips hidden directories like .git, node_modules, target, build by default.")
    public String listFiles(
        @ToolParam(description = "Absolute path to the directory") String path,
        @ToolParam(description = "Maximum depth to recurse (default 3)", type = "integer", required = false) int depth
    ) {
        try {
            Path dirPath = Path.of(path);
            if (!Files.exists(dirPath)) {
                return "Error: Directory not found: " + path;
            }
            if (!Files.isDirectory(dirPath)) {
                return "Error: Not a directory: " + path;
            }

            int maxDepth = (depth > 0) ? depth : 3;
            StringBuilder sb = new StringBuilder();
            sb.append(dirPath.getFileName()).append("/\n");

            Files.walkFileTree(dirPath, new SimpleFileVisitor<>() {
                private int currentDepth = 0;

                @Override
                public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
                    if (currentDepth >= maxDepth) {
                        return FileVisitResult.SKIP_SUBTREE;
                    }
                    String dirName = dir.getFileName().toString();
                    if (SKIP_DIRS.contains(dirName) || (dirName.startsWith(".") && !dirName.equals("."))) {
                        return FileVisitResult.SKIP_SUBTREE;
                    }
                    if (!dir.equals(dirPath)) {
                        sb.append("  ".repeat(currentDepth));
                        sb.append("├── ").append(dirName).append("/\n");
                        currentDepth++;
                    }
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                    sb.append("  ".repeat(currentDepth));
                    sb.append("├── ").append(file.getFileName()).append("\n");
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult postVisitDirectory(Path dir, IOException exc) {
                    if (!dir.equals(dirPath)) {
                        currentDepth--;
                    }
                    return FileVisitResult.CONTINUE;
                }
            });

            return sb.toString();
        } catch (IOException e) {
            LOG.error("Failed to list files: {}", path, e);
            return "Error listing files: " + e.getMessage();
        }
    }
}
