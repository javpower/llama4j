package com.llama4j.web;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;

@SpringBootApplication
public class WebAgentApplication {

    public static void main(String[] args) {
        SpringApplication.run(WebAgentApplication.class, args);
    }
}
