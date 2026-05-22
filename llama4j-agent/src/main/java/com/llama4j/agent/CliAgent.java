package com.llama4j.agent;

import com.llama4j.core.ChatRequest;
import com.llama4j.core.ChatResponse;
import com.llama4j.core.Model;
import com.llama4j.tools.ReActAgent;
import com.llama4j.tools.ToolDefinition;
import com.llama4j.tools.ToolRegistry;
import com.llama4j.tools.StreamingToolListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.concurrent.CompletableFuture;

public class CliAgent {

    private static final Logger LOG = LoggerFactory.getLogger(CliAgent.class);

    private final ToolRegistry toolRegistry;
    private final AgentConfig config;
    private volatile Model model;
    private volatile ReActAgent reactAgent;
    private volatile boolean streaming = false;

    public CliAgent(Model model, ToolRegistry toolRegistry, AgentConfig config) {
        this.model = model;
        this.toolRegistry = toolRegistry;
        this.config = config;
        this.reactAgent = buildAgent(model);
        LOG.info("CliAgent initialized with model '{}', {} tools registered",
            model.getModelName(), toolRegistry.size());
    }

    public ChatResponse chat(ChatRequest request) {
        return reactAgent.call(request);
    }

    public CompletableFuture<ChatResponse> chatStream(ChatRequest request, StreamingToolListener listener) {
        streaming = true;
        return reactAgent.callStream(request, listener).whenComplete((r, e) -> streaming = false);
    }

    public List<ToolDefinition> getAvailableTools() {
        return List.copyOf(toolRegistry.getDefinitions());
    }

    public String getModelName() {
        return model.getModelName();
    }

    public ToolRegistry getToolRegistry() {
        return toolRegistry;
    }

    public AgentConfig getConfig() {
        return config;
    }

    public void switchModel(Model newModel) {
        if (streaming) {
            throw new IllegalStateException("Cannot switch model while streaming is active");
        }
        this.model = newModel;
        this.reactAgent = buildAgent(newModel);
        LOG.info("Switched to model '{}'", newModel.getModelName());
    }

    private ReActAgent buildAgent(Model m) {
        return ReActAgent.builder()
            .model(m)
            .toolRegistry(toolRegistry)
            .maxIterations(config.maxIterations())
            .jsonModeForTools(config.jsonModeForTools())
            .build();
    }
}
