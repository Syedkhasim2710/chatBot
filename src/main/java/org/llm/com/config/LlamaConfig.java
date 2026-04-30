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

/**
 * Ollama fallback ChatClient using a second local model (e.g. mistral).
 *
 * <p>This client is used automatically whenever the primary Ollama model fails.
 * Both primary and fallback run locally via <a href="https://ollama.com">Ollama</a>
 * — no API key or internet connection required.
 *
 * <p>Pull models with:
 * <pre>
 *   ollama pull llama3.2   # primary
 *   ollama pull mistral    # fallback
 * </pre>
 */
@Configuration
public class LlamaConfig {

    @Value("${ollama.fallback.base-url:http://localhost:11434}")
    private String baseUrl;

    @Value("${ollama.fallback.model:mistral}")
    private String fallbackModel;

    @Value("${ollama.fallback.temperature:0.7}")
    private double temperature;

    @Value("${ollama.fallback.num-predict:2048}")
    private int numPredict;

    @Value("${chatbot.ai.system-prompt}")
    private String systemPrompt;

    /**
     * Fallback {@link ChatClient} backed by a second Ollama model.
     * Bean qualifier: {@code llamaChatClient}.
     */
    @Bean("llamaChatClient")
    public ChatClient llamaChatClient(ChatMemory chatMemory) {
        OllamaApi ollamaApi = OllamaApi.builder()
                .baseUrl(baseUrl)
                .build();

        OllamaChatModel model = OllamaChatModel.builder()
                .ollamaApi(ollamaApi)
                .defaultOptions(OllamaOptions.builder()
                        .model(fallbackModel)
                        .temperature(temperature)
                        .numPredict(numPredict)
                        .build())
                .build();

        return ChatClient.builder(model)
                .defaultSystem(systemPrompt)
                .defaultAdvisors(MessageChatMemoryAdvisor.builder(chatMemory).build())
                .build();
    }
}