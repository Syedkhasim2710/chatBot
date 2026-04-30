package org.llm.com.config;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.ollama.OllamaChatModel;
import org.springframework.ai.ollama.api.OllamaApi;
import org.springframework.ai.ollama.api.OllamaOptions;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

/**
 * Primary Spring AI ChatClient — backed by a local Ollama model.
 *
 * <p>The client is built directly from {@link OllamaApi} + {@link OllamaChatModel}
 * instead of relying on the auto-configured {@code ChatClient.Builder}.
 * This avoids any bean-name collision with Spring AI's {@code ChatClientAutoConfiguration}
 * and makes the wiring explicit — no {@code allow-bean-definition-overriding} needed.
 *
 * <p>Pull the model first:
 * <pre>ollama pull llama3.2</pre>
 *
 * <p>For production, swap {@code InMemoryChatMemory} for a
 * {@code JdbcChatMemoryRepository} or {@code RedisChatMemoryRepository}.
 */
@Configuration
public class SpringAiConfig {

    @Value("${spring.ai.ollama.base-url:http://localhost:11434}")
    private String baseUrl;

    @Value("${spring.ai.ollama.chat.options.model:llama3.2}")
    private String model;

    @Value("${spring.ai.ollama.chat.options.temperature:0.7}")
    private double temperature;

    @Value("${spring.ai.ollama.chat.options.num-predict:2048}")
    private int numPredict;

    @Value("${chatbot.ai.system-prompt}")
    private String systemPrompt;

    /**
     * Primary {@link ChatClient} backed by the primary Ollama model.
     *
     * <p>Marked {@link Primary} so that any injection point that declares
     * {@code ChatClient} without a qualifier receives this bean automatically.
     * The fallback client in {@link LlamaConfig} is injected via
     * {@code @Qualifier("llamaChatClient")}.
     *
     * @param chatMemory auto-configured in-memory chat memory
     */
    @Primary
    @Bean("chatClient")
    public ChatClient chatClient(ChatMemory chatMemory) {
        OllamaApi ollamaApi = OllamaApi.builder()
                .baseUrl(baseUrl)
                .build();

        OllamaChatModel ollamaChatModel = OllamaChatModel.builder()
                .ollamaApi(ollamaApi)
                .defaultOptions(OllamaOptions.builder()
                        .model(model)
                        .temperature(temperature)
                        .numPredict(numPredict)
                        .build())
                .build();

        return ChatClient.builder(ollamaChatModel)
                .defaultSystem(systemPrompt)
                .defaultAdvisors(MessageChatMemoryAdvisor.builder(chatMemory).build())
                .build();
    }
}
