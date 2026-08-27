package com.aops.agent.config;

import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.util.concurrent.Executor;

/**
 * LLM model beans (DeepSeek via OpenAI-compatible API) and the investigation
 * async executor.
 */
@Configuration
public class AgentFactory {

    @Bean
    public RestClient.Builder restClientBuilder() {
        return RestClient.builder();
    }

    @Bean
    public ChatModel mainChatModel(AopsProperties props) {
        return OpenAiChatModel.builder()
                .baseUrl(props.agent().baseUrl())
                .apiKey(props.agent().apiKey())
                .modelName(props.agent().mainModel())
                .temperature(props.agent().temperature())
                .timeout(Duration.ofSeconds(props.agent().timeoutSeconds()))
                .build();
    }

    @Bean
    public ChatModel fastChatModel(AopsProperties props) {
        return OpenAiChatModel.builder()
                .baseUrl(props.agent().baseUrl())
                .apiKey(props.agent().apiKey())
                .modelName(props.agent().fastModel())
                .temperature(0.0)
                .timeout(Duration.ofSeconds(props.agent().timeoutSeconds()))
                .build();
    }

    @Bean(name = "aopsExecutor")
    public Executor aopsExecutor(AopsProperties props) {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(props.async().corePoolSize());
        executor.setMaxPoolSize(props.async().maxPoolSize());
        executor.setQueueCapacity(props.async().queueCapacity());
        executor.setThreadNamePrefix("aops-investigator-");
        executor.initialize();
        return executor;
    }
}
