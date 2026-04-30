package org.llm.com;

import dev.langchain4j.model.chat.ChatLanguageModel;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/**
 * Smoke test — verifies the Spring application context loads successfully.
 *
 * All three AI provider beans are mocked — no real Ollama / Groq calls.
 * RAG is disabled in application-test.yml (chatbot.rag.enabled=false)
 * so no embedding model is required.
 */
@SpringBootTest
@ActiveProfiles("test")
@DisplayName("Application context loads")
class ChatbotApplicationTest {

    @MockitoBean(name = "chatClient")
    private ChatClient primaryChatClient;

    @MockitoBean(name = "llamaChatClient")
    private ChatClient fallbackChatClient;

    @MockitoBean(name = "groqChatModel")
    private ChatLanguageModel groqChatModel;

    @Test
    @DisplayName("Spring context should start without errors")
    void contextLoads() {
    }
}
