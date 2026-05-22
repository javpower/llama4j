package com.llama4j.agent.agent;

import com.llama4j.agent.AgentConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class PermissionManager {

    private static final Logger LOG = LoggerFactory.getLogger(PermissionManager.class);

    private static final Map<String, PermissionCategory> TOOL_CATEGORIES = Map.of(
        "read_file", PermissionCategory.FILE_READ,
        "list_files", PermissionCategory.FILE_READ,
        "search_files", PermissionCategory.FILE_READ,
        "find_files", PermissionCategory.FILE_READ,
        "write_file", PermissionCategory.FILE_WRITE,
        "edit_file", PermissionCategory.FILE_WRITE,
        "run_command", PermissionCategory.SHELL_COMMAND,
        "web_search", PermissionCategory.WEB_SEARCH,
        "web_fetch", PermissionCategory.WEB_FETCH
    );

    private final Map<PermissionCategory, PermissionLevel> categoryLevels;
    private final Set<String> sessionApprovals = ConcurrentHashMap.newKeySet();

    public PermissionManager(AgentConfig config) {
        this.categoryLevels = new ConcurrentHashMap<>(config.permissions());
    }

    public PermissionManager(Map<PermissionCategory, PermissionLevel> levels) {
        this.categoryLevels = new ConcurrentHashMap<>(levels);
    }

    public PermissionLevel checkTool(String toolName, String arguments) {
        if (sessionApprovals.contains(toolName)) {
            return PermissionLevel.AUTO_APPROVE;
        }

        PermissionCategory category = TOOL_CATEGORIES.get(toolName);
        if (category == null) {
            LOG.warn("Unknown tool category for '{}', defaulting to ASK", toolName);
            return PermissionLevel.ASK;
        }

        return categoryLevels.getOrDefault(category, PermissionLevel.ASK);
    }

    public void approveAlways(String toolName) {
        sessionApprovals.add(toolName);
        LOG.info("Session approval granted for tool '{}'", toolName);
    }

    public void revokeAlways(String toolName) {
        sessionApprovals.remove(toolName);
        LOG.info("Session approval revoked for tool '{}'", toolName);
    }

    public void setCategoryLevel(PermissionCategory category, PermissionLevel level) {
        categoryLevels.put(category, level);
    }

    public Map<PermissionCategory, PermissionLevel> getLevels() {
        return Map.copyOf(categoryLevels);
    }
}
