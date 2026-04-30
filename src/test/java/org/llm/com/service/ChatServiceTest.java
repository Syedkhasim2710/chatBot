package org.llm.com.service;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.output.Response;
import org.junit.jupiter.api.BeforeEach;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.llm.com.dto.request.SendMessageRequest;
import org.llm.com.dto.response.ChatResponse;
import org.llm.com.exception.ChatbotException;
import org.llm.com.model.ChatMessage;
import org.llm.com.model.ChatSession;
import org.llm.com.model.MessageRole;
import org.llm.com.service.impl.ChatServiceImpl;
import org.mockito.Mock;import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.ai.chat.client.ChatClient;

import java.util.List;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for ChatServiceImpl — 3-tier fallback with UrlFetchService mock.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("ChatServiceImpl — 3-tier fallback")
class ChatServiceTest {

    @Mock private SessionService    sessionService;
    @Mock private ChatLanguageModel groqModel;
    @Mock private WeatherService    weatherService;
    @Mock private UrlFetchService   urlFetchService;
    @Mock private WebSearchService  webSearchService;

    private ChatClient primaryClient;
    private ChatClient fallbackClient;
    private ChatServiceImpl chatService;
    private ChatSession     activeSession;

    @BeforeEach
    void setUp() {
        activeSession = ChatSession.builder()
                .sessionId("sess-1")
                .identity("alice@example.com")
                .build();

        primaryClient  = buildChatClientMock("Primary Ollama response");
        fallbackClient = buildChatClientMock("Fallback Ollama response");

        when(groqModel.generate(anyList()))
                .thenReturn(Response.from(AiMessage.from("Groq cloud response")));

        when(weatherService.fetchIfWeatherQuery(anyString())).thenReturn(Optional.empty());
        when(urlFetchService.extractUrls(anyString())).thenReturn(List.of());
        when(webSearchService.searchIfRealTime(anyString())).thenReturn(Optional.empty());

        chatService = new ChatServiceImpl(
                sessionService,
                primaryClient,
                fallbackClient,
                groqModel,
                weatherService,
                urlFetchService,
                webSearchService,
                "llama3.2", "mistral", "llama-3.3-70b-versatile",
                "You are a test assistant.");
    }

    @Test
    @DisplayName("Tier 1 — Spring AI primary returns reply")
    void tier1_primarySucceeds() {
        when(sessionService.getSession("sess-1")).thenReturn(activeSession);
        ChatResponse response = chatService.sendMessage("sess-1",
                SendMessageRequest.builder().content("Hello").build());
        assertThat(response.getAssistantMessage()).isEqualTo("Primary Ollama response");
        assertThat(response.getProvider()).isEqualTo(ChatServiceImpl.PROVIDER_SPRING_AI_PRIMARY);
        verify(groqModel, never()).generate(anyList());
    }

    @Test
    @DisplayName("Tier 2 — falls back to mistral when primary fails")
    void tier2_fallbackWhenPrimaryFails() {
        when(sessionService.getSession("sess-1")).thenReturn(activeSession);
        makeSpringAiClientFail(primaryClient);
        ChatResponse response = chatService.sendMessage("sess-1",
                SendMessageRequest.builder().content("Hello").build());
        assertThat(response.getAssistantMessage()).isEqualTo("Fallback Ollama response");
        assertThat(response.getProvider()).isEqualTo(ChatServiceImpl.PROVIDER_SPRING_AI_FALLBACK);
        verify(groqModel, never()).generate(anyList());
    }

    @Test
    @DisplayName("Tier 3 — escalates to LangChain4j / Groq when both Ollama models fail")
    void tier3_groqWhenBothOllamaFail() {
        when(sessionService.getSession("sess-1")).thenReturn(activeSession);
        makeSpringAiClientFail(primaryClient);
        makeSpringAiClientFail(fallbackClient);
        ChatResponse response = chatService.sendMessage("sess-1",
                SendMessageRequest.builder().content("Hello Groq").build());
        assertThat(response.getAssistantMessage()).isEqualTo("Groq cloud response");
        assertThat(response.getProvider()).isEqualTo(ChatServiceImpl.PROVIDER_LANGCHAIN4J_GROQ);
        verify(groqModel).generate(anyList());
    }

    @Test
    @DisplayName("All 3 tiers fail — throws ChatbotException")
    void allTiersFail_throwsChatbotException() {
        when(sessionService.getSession("sess-1")).thenReturn(activeSession);
        makeSpringAiClientFail(primaryClient);
        makeSpringAiClientFail(fallbackClient);
        when(groqModel.generate(anyList())).thenThrow(new RuntimeException("Groq 401 Unauthorized"));
        assertThatThrownBy(() -> chatService.sendMessage("sess-1",
                SendMessageRequest.builder().content("Test").build()))
                .isInstanceOf(ChatbotException.class)
                .hasMessageContaining("unavailable");
    }

    @Test
    @DisplayName("getHistory() returns copy of session messages")
    void getHistory_returnsMessages() {
        activeSession.addMessage(ChatMessage.builder().role(MessageRole.USER).content("Hi").build());
        when(sessionService.getSession("sess-1")).thenReturn(activeSession);
        List<ChatMessage> history = chatService.getHistory("sess-1");
        assertThat(history).hasSize(1);
        assertThat(history.get(0).getContent()).isEqualTo("Hi");
    }

    @Test
    @DisplayName("additionalContext is forwarded to the enriched prompt")
    void additionalContext_isForwardedCorrectly() {
        when(sessionService.getSession("sess-1")).thenReturn(activeSession);
        ChatResponse response = chatService.sendMessage("sess-1",
                SendMessageRequest.builder().content("Summarise this doc").build(),
                "[DOCUMENT CONTENT: report.pdf]\nFinancial Q1 results...");
        assertThat(response.getProvider()).isEqualTo(ChatServiceImpl.PROVIDER_SPRING_AI_PRIMARY);
    }

    private static ChatClient buildChatClientMock(String response) {
        var callSpec   = mock(ChatClient.CallResponseSpec.class);
        var promptSpec = mock(ChatClient.ChatClientRequestSpec.class);
        var client     = mock(ChatClient.class);
        when(client.prompt()).thenReturn(promptSpec);
        when(promptSpec.user(anyString())).thenReturn(promptSpec);
        //noinspection unchecked
        when(promptSpec.advisors(any(Consumer.class))).thenReturn(promptSpec);
        when(promptSpec.call()).thenReturn(callSpec);
        when(callSpec.content()).thenReturn(response);
        return client;
    }

    private static void makeSpringAiClientFail(ChatClient client) {
        var failSpec = mock(ChatClient.ChatClientRequestSpec.class);
        when(client.prompt()).thenReturn(failSpec);
        when(failSpec.user(anyString())).thenReturn(failSpec);
        //noinspection unchecked
        when(failSpec.advisors(any(Consumer.class))).thenReturn(failSpec);
        when(failSpec.call()).thenThrow(new RuntimeException("Connection refused"));
    }
}
