package com.llama4j.web.model;

import java.util.List;

public record FileNode(
    String name,
    String path,
    String type,  // "file" or "directory"
    long size,
    List<FileNode> children
) {
    public static FileNode directory(String name, String path, List<FileNode> children) {
        return new FileNode(name, path, "directory", 0, children);
    }

    public static FileNode file(String name, String path, long size) {
        return new FileNode(name, path, "file", size, null);
    }
}
