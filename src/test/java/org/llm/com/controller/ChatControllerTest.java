package org.llm.com.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.llm.com.dto.request.CreateSessionRequest;
import org.llm.com.dto.request.SendMessageRequest;
import org.llm.com.dto.response.ChatResponse;
import org.llm.com.exception.SessionNotFoundException;
import org.llm.com.model.ChatMessage;
import org.llm.com.model.ChatSession;
import org.llm.com.model.MessageRole;
import org.llm.com.config.SecurityConfig;
import org.llm.com.service.ChatService;
import org.llm.com.service.SessionService;
import org.llm.com.service.DocumentParserService;
import org.llm.com.service.BatchIngestService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Slice tests for the REST controllers using MockMvc.
 *
 * <p>No Spring AI / Ollama beans are loaded — services are mocked via {@code @MockitoBean}.
 */
@WebMvcTest({SessionController.class, ChatController.class})
@Import(SecurityConfig.class)
@DisplayName("Controller layer (MockMvc)")
class ChatControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private SessionService sessionService;

    @MockitoBean
    private ChatService chatService;

    @MockitoBean
    private DocumentParserService documentParserService;

    @MockitoBean
    private BatchIngestService batchIngestService;

    private ChatSession testSession;

    @BeforeEach
    void setUp() {
        testSession = ChatSession.builder()
                .sessionId("sess-test-001")
                .identity("alice@example.com")
                .build();
    }

    // ─────────────────────── POST /api/v1/sessions ───────────────────────────

    @Test
    @DisplayName("POST /api/v1/sessions — should create session and return 201")
    void createSession_shouldReturn201() throws Exception {
        when(sessionService.createSession(any(CreateSessionRequest.class)))
                .thenReturn(testSession);

        CreateSessionRequest req = CreateSessionRequest.builder()
                .identity("alice@example.com")
                .build();

        mockMvc.perform(post("/api/v1/sessions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.data.sessionId", is("sess-test-001")))
                .andExpect(jsonPath("$.data.identity", is("alice@example.com")));
    }

    @Test
    @DisplayName("POST /api/v1/sessions — should return 400 when identity is missing")
    void createSession_shouldReturn400WhenIdentityMissing() throws Exception {
        CreateSessionRequest req = CreateSessionRequest.builder().build(); // no identity

        mockMvc.perform(post("/api/v1/sessions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success", is(false)));
    }

    // ─────────────────────── GET /api/v1/sessions/{id} ───────────────────────

    @Test
    @DisplayName("GET /api/v1/sessions/{id} — should return 200 for known session")
    void getSession_shouldReturn200() throws Exception {
        when(sessionService.getSession("sess-test-001")).thenReturn(testSession);

        mockMvc.perform(get("/api/v1/sessions/sess-test-001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.sessionId", is("sess-test-001")))
                .andExpect(jsonPath("$.data.identity", is("alice@example.com")));
    }

    @Test
    @DisplayName("GET /api/v1/sessions/{id} — should return 404 for unknown session")
    void getSession_shouldReturn404() throws Exception {
        when(sessionService.getSession("no-such-session"))
                .thenThrow(new SessionNotFoundException("no-such-session"));

        mockMvc.perform(get("/api/v1/sessions/no-such-session"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success", is(false)));
    }

    // ─────────────────────── POST /api/v1/sessions/{id}/chat ─────────────────

    @Test
    @DisplayName("POST /chat — should return 200 with AI reply")
    void sendMessage_shouldReturn200() throws Exception {
        when(sessionService.getSession("sess-test-001")).thenReturn(testSession);

        ChatResponse aiResp = ChatResponse.builder()
                .sessionId("sess-test-001")
                .identity("alice@example.com")
                .userMessage("What is virtual threading?")
                .assistantMessage("Virtual threads are lightweight JVM threads introduced in JDK 21.")
                .model("llama3.2")
                .timestamp(Instant.now())
                .totalMessages(2)
                .build();

        when(chatService.sendMessage(eq("sess-test-001"), any(SendMessageRequest.class)))
                .thenReturn(aiResp);

        SendMessageRequest req = SendMessageRequest.builder()
                .content("What is virtual threading?")
                .build();

        mockMvc.perform(post("/api/v1/sessions/sess-test-001/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.data.assistantMessage",
                        is("Virtual threads are lightweight JVM threads introduced in JDK 21.")));
    }

    @Test
    @DisplayName("POST /chat — should return 400 when message content is blank")
    void sendMessage_shouldReturn400WhenContentBlank() throws Exception {
        SendMessageRequest req = SendMessageRequest.builder().content("  ").build();

        mockMvc.perform(post("/api/v1/sessions/sess-test-001/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest());
    }

    // ─────────────────────── GET /api/v1/sessions/{id}/history ───────────────

    @Test
    @DisplayName("GET /history — should return ordered message list")
    void getHistory_shouldReturnMessages() throws Exception {
        ChatMessage m1 = ChatMessage.builder().role(MessageRole.USER).content("Hi").build();
        ChatMessage m2 = ChatMessage.builder().role(MessageRole.ASSISTANT).content("Hello! How can I help?").build();

        when(chatService.getHistory("sess-test-001")).thenReturn(List.of(m1, m2));

        mockMvc.perform(get("/api/v1/sessions/sess-test-001/history"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(2)))
                .andExpect(jsonPath("$.data[0].role", is("USER")))
                .andExpect(jsonPath("$.data[1].role", is("ASSISTANT")));
    }

    // ─────────────────────── DELETE /api/v1/sessions/{id} ────────────────────

    @Test
    @DisplayName("DELETE /api/v1/sessions/{id} — should return 200 on deactivation")
    void deleteSession_shouldReturn200() throws Exception {
        mockMvc.perform(delete("/api/v1/sessions/sess-test-001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)));

        verify(sessionService).deactivateSession("sess-test-001");
    }

    @Test
    @DisplayName("DELETE /api/v1/sessions/{id} — should return 404 when session missing")
    void deleteSession_shouldReturn404() throws Exception {
        doThrow(new SessionNotFoundException("missing-id"))
                .when(sessionService).deactivateSession("missing-id");

        mockMvc.perform(delete("/api/v1/sessions/missing-id"))
                .andExpect(status().isNotFound());
    }
}
