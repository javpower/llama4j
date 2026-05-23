package com.llama4j.web.config;

import com.llama4j.web.controller.TerminalWebSocketHandler;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {

    private final TerminalWebSocketHandler terminalHandler;
    private final WebProperties webProperties;

    public WebSocketConfig(TerminalWebSocketHandler terminalHandler, WebProperties webProperties) {
        this.terminalHandler = terminalHandler;
        this.webProperties = webProperties;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        var handler = registry.addHandler(terminalHandler, "/ws/terminal");

        String[] origins = webProperties.getCors().getAllowedOrigins();
        if (origins.length > 0) {
            handler.setAllowedOrigins(origins);
        } else {
            // Default: allow same-origin only for security
            handler.setAllowedOrigins("http://localhost:8080", "http://127.0.0.1:8080");
        }
    }
}
