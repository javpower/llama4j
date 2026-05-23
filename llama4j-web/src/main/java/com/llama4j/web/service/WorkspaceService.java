package com.llama4j.web.service;

import com.llama4j.web.model.FileNode;
import com.llama4j.web.model.WorkspaceInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class WorkspaceService {

    private static final Logger LOG = LoggerFactory.getLogger(WorkspaceService.class);
    private static final Set<String> SKIP_DIRS = Set.of(
        ".git", "node_modules", "__pycache__", ".idea", ".vscode",
        "target", "build", "dist", ".gradle", ".mvn", "vendor"
    );

    private final Map<String, WorkspaceInfo> workspaces = new ConcurrentHashMap<>();
    private volatile String activeWorkspaceId;

    public WorkspaceInfo openWorkspace(String path) {
        Path dirPath = Path.of(path).toAbsolutePath();
        if (!Files.exists(dirPath) || !Files.isDirectory(dirPath)) {
            throw new IllegalArgumentException("Directory not found: " + path);
        }

        WorkspaceInfo info = WorkspaceInfo.of(dirPath.toString());
        workspaces.put(info.id(), info);
        activeWorkspaceId = info.id();
        LOG.info("Opened workspace: {} ({})", info.name(), info.path());
        return info;
    }

    public WorkspaceInfo getCurrentWorkspace() {
        if (activeWorkspaceId == null) return null;
        return workspaces.get(activeWorkspaceId);
    }

    public void closeWorkspace() {
        if (activeWorkspaceId != null) {
            workspaces.remove(activeWorkspaceId);
            activeWorkspaceId = null;
        }
    }

    public FileNode getFileTree(int maxDepth) {
        WorkspaceInfo ws = getCurrentWorkspace();
        if (ws == null) throw new IllegalStateException("No active workspace");

        Path root = Path.of(ws.path());
        return buildFileTree(root, root, 0, maxDepth);
    }

    public FileNode getFileTreeAtPath(String relativePath, int maxDepth) {
        WorkspaceInfo ws = getCurrentWorkspace();
        if (ws == null) throw new IllegalStateException("No active workspace");

        Path root = Path.of(ws.path());
        Path target = root.resolve(relativePath).normalize();
        if (!target.startsWith(root)) {
            throw new IllegalArgumentException("Path outside workspace");
        }
        return buildFileTree(target, root, 0, maxDepth);
    }

    public List<String> listDirectories(String relativePath) {
        WorkspaceInfo ws = getCurrentWorkspace();
        if (ws == null) throw new IllegalStateException("No active workspace");

        Path root = Path.of(ws.path());
        Path target = relativePath.isEmpty() ? root : root.resolve(relativePath).normalize();

        // Path traversal guard
        if (!target.startsWith(root)) {
            throw new IllegalArgumentException("Path outside workspace");
        }

        List<String> dirs = new ArrayList<>();

        try (DirectoryStream<Path> stream = Files.newDirectoryStream(target)) {
            for (Path entry : stream) {
                if (Files.isDirectory(entry)) {
                    dirs.add(entry.getFileName().toString());
                }
            }
        } catch (IOException e) {
            LOG.error("Failed to list directories: {}", target, e);
        }
        Collections.sort(dirs);
        return dirs;
    }

    private FileNode buildFileTree(Path current, Path root, int depth, int maxDepth) {
        String name = current.equals(root) ? root.getFileName().toString() : current.getFileName().toString();
        String relativePath = root.relativize(current).toString();

        if (Files.isDirectory(current)) {
            if (depth >= maxDepth) {
                return FileNode.directory(name, relativePath, List.of());
            }

            List<FileNode> children = new ArrayList<>();
            try (DirectoryStream<Path> stream = Files.newDirectoryStream(current)) {
                for (Path entry : stream) {
                    String entryName = entry.getFileName().toString();
                    if (SKIP_DIRS.contains(entryName) || (entryName.startsWith(".") && !entryName.equals("."))) {
                        continue;
                    }
                    children.add(buildFileTree(entry, root, depth + 1, maxDepth));
                }
            } catch (IOException e) {
                LOG.warn("Failed to read directory: {}", current, e);
            }

            children.sort((a, b) -> {
                if (!a.type().equals(b.type())) {
                    return a.type().equals("directory") ? -1 : 1;
                }
                return a.name().compareToIgnoreCase(b.name());
            });

            return FileNode.directory(name, relativePath, children);
        } else {
            long size = 0;
            try {
                size = Files.size(current);
            } catch (IOException ignored) {}
            return FileNode.file(name, relativePath, size);
        }
    }
}
