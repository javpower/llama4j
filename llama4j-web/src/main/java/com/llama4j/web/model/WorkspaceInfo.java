package com.llama4j.web.model;

import java.time.Instant;

public record WorkspaceInfo(
    String id,
    String path,
    String name,
    Instant openedAt
) {
    public static WorkspaceInfo of(String path) {
        java.io.File dir = new java.io.File(path);
        return new WorkspaceInfo(
            "ws_" + System.currentTimeMillis(),
            dir.getAbsolutePath(),
            dir.getName(),
            Instant.now()
        );
    }
}
