package com.aops.agent;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.scheduling.annotation.EnableAsync;

/**
 * AI Ops Agent — Java implementation of the HolmesGPT-style troubleshooting agent.
 */
@SpringBootApplication
@EnableAsync
@ConfigurationPropertiesScan
public class AgentApplication {

    public static void main(String[] args) {
        SpringApplication.run(AgentApplication.class, args);
    }
}
