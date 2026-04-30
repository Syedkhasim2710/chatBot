package org.llm.com.config;

import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

/**
 * LangChain4j cloud fallback — Groq (OpenAI-compatible, free tier).
 *
 * <p>This bean is the <em>last-resort</em> AI provider, used only when both
 * local Ollama models (Spring AI primary + Spring AI fallback) are unavailable.
 * Using a completely different framework (LangChain4j) and a different
 * infrastructure path (Groq cloud) gives true multi-layer resilience:
 *
 * <pre>
 *  Spring AI → llama3.2 (Ollama local)   — fastest, free, works offline
 *     ↓ fail
 *  Spring AI → mistral  (Ollama local)   — same infra, different model
 *     ↓ fail
 *  LangChain4j → Groq (cloud, free tier) — different framework + different infra
 * </pre>
 *
 * <p>Get a free Groq API key at <a href="https://console.groq.com">console.groq.com</a>
 * and set it via the {@code GROQ_API_KEY} environment variable.
 *
 * <p>If the key is absent the bean is still created; calls will return a
 * {@code ChatbotException} so the caller can surface a meaningful error message.
 *
 * <p><b>Why no {@code langchain4j-spring-boot-starter}?</b><br>
 * The Spring Boot auto-configuration starter registers its own {@code ChatModel} bean
 * which collides with Spring AI's auto-configured bean of the same type.  Using only
 * {@code langchain4j-open-ai} gives us the LangChain4j HTTP client without any Spring
 * auto-configuration, so no {@code allow-bean-definition-overriding} is needed.
 */
@Slf4j
@Configuration
public class GroqConfig {

    @Value("${groq.api-key:${GROQ_API_KEY:}}")
    private String groqApiKey;

    @Value("${groq.base-url:https://api.groq.com/openai/v1/}")
    private String groqBaseUrl;

    @Value("${groq.model:llama-3.3-70b-versatile}")
    private String groqModel;

    @Value("${groq.max-tokens:2048}")
    private int maxTokens;

    @Value("${groq.timeout-seconds:30}")
    private int timeoutSeconds;

    /**
     * LangChain4j {@link ChatLanguageModel} pointing at the Groq inference API.
     *
     * <p>The bean is always created even when {@code GROQ_API_KEY} is unset; the
     * placeholder key {@code "not-configured"} ensures the builder does not throw.
     * Actual API calls will then return a 401 which {@code ChatServiceImpl} catches and
     * converts to a descriptive {@link org.llm.com.exception.ChatbotException}.
     */
    @Bean("groqChatModel")
    public ChatLanguageModel groqChatModel() {
        if (groqApiKey == null || groqApiKey.isBlank()) {
            log.warn("[Groq] GROQ_API_KEY is not set — Groq cloud fallback will report " +
                     "'unavailable' if both Ollama models fail. " +
                     "Set GROQ_API_KEY to enable it: https://console.groq.com");
        } else {
            log.info("[Groq] Cloud fallback configured — model=[{}]", groqModel);
        }

        return OpenAiChatModel.builder()
                .apiKey(groqApiKey == null || groqApiKey.isBlank() ? "not-configured" : groqApiKey)
                .baseUrl(groqBaseUrl)
                .modelName(groqModel)
                .maxTokens(maxTokens)
                .timeout(Duration.ofSeconds(timeoutSeconds))
                .logRequests(false)   // [InfoSec] never log prompts
                .logResponses(false)
                .build();
    }
}

