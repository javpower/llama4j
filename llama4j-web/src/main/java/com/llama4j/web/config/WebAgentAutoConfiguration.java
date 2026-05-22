package com.llama4j.web.config;

import com.llama4j.agent.AgentConfig;
import com.llama4j.agent.CliAgent;
import com.llama4j.agent.tools.*;
import com.llama4j.core.Model;
import com.llama4j.core.ModelRegistry;
import com.llama4j.tools.ToolRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class WebAgentAutoConfiguration {

    private static final Logger LOG = LoggerFactory.getLogger(WebAgentAutoConfiguration.class);

    @Bean
    @ConditionalOnMissingBean
    public AgentConfig agentConfig() {
        return AgentConfig.defaults();
    }

    @Bean
    @ConditionalOnMissingBean
    public ToolRegistry agentToolRegistry() {
        ToolRegistry registry = new ToolRegistry();
        registry.scanAndRegister(new ReadFileTool());
        registry.scanAndRegister(new WriteFileTool());
        registry.scanAndRegister(new EditFileTool());
        registry.scanAndRegister(new ListFilesTool());
        registry.scanAndRegister(new SearchFilesTool());
        registry.scanAndRegister(new FindFilesTool());
        registry.scanAndRegister(new RunCommandTool());
        registry.scanAndRegister(new WebSearchTool());
        registry.scanAndRegister(new WebFetchTool());
        LOG.info("Registered {} agent tools", registry.size());
        return registry;
    }

    @Bean
    @ConditionalOnMissingBean
    public CliAgent cliAgent(ModelRegistry modelRegistry, ToolRegistry agentToolRegistry, AgentConfig config) {
        Model defaultModel = modelRegistry.getDefault();
        if (defaultModel == null) {
            throw new IllegalStateException("No default model configured");
        }
        return new CliAgent(defaultModel, agentToolRegistry, config);
    }
}
