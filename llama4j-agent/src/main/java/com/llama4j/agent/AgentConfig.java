package com.llama4j.agent;

import com.llama4j.agent.agent.PermissionCategory;
import com.llama4j.agent.agent.PermissionLevel;

import java.util.Map;

public record AgentConfig(
    int maxIterations,
    float temperature,
    int maxTokens,
    int historyLimit,
    boolean jsonModeForTools,
    Map<PermissionCategory, PermissionLevel> permissions
) {
    public static AgentConfig defaults() {
        return new AgentConfig(
            15,
            0.7f,
            4096,
            50,
            false,
            Map.of(
                PermissionCategory.FILE_READ, PermissionLevel.AUTO_APPROVE,
                PermissionCategory.FILE_WRITE, PermissionLevel.ASK,
                PermissionCategory.SHELL_COMMAND, PermissionLevel.ASK,
                PermissionCategory.WEB_SEARCH, PermissionLevel.AUTO_APPROVE,
                PermissionCategory.WEB_FETCH, PermissionLevel.AUTO_APPROVE
            )
        );
    }
}
