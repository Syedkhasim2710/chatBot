package org.llm.com.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.llm.com.dto.request.CreateSessionRequest;
import org.llm.com.exception.SessionNotFoundException;
import org.llm.com.model.ChatSession;
import org.llm.com.repository.SessionRepository;
import org.llm.com.service.impl.SessionServiceImpl;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link SessionServiceImpl}.
 *
 * <p>The repository is mocked with Mockito — no Spring context needed.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("SessionServiceImpl")
class SessionServiceTest {

    @Mock
    private SessionRepository sessionRepository;

    private SessionServiceImpl sessionService;

    @BeforeEach
    void setUp() {
        sessionService = new SessionServiceImpl(sessionRepository);
    }

    // ─────────────────────── createSession ──────────────────────────────────

    @Test
    @DisplayName("createSession() should persist and return a new active session")
    void createSession_shouldReturnActiveSession() {
        CreateSessionRequest req = new CreateSessionRequest("khasim@example.com");

        when(sessionRepository.save(any(ChatSession.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        ChatSession result = sessionService.createSession(req);

        assertThat(result.getIdentity()).isEqualTo("khasim@example.com");
        assertThat(result.isActive()).isTrue();
        assertThat(result.getSessionId()).isNotBlank();
        verify(sessionRepository, times(1)).save(any());
    }

    // ─────────────────────── getSession ─────────────────────────────────────

    @Test
    @DisplayName("getSession() should return session when it exists and is active")
    void getSession_shouldReturnActiveSession() {
        ChatSession expected = ChatSession.builder()
                .sessionId("sess-abc")
                .identity("user@example.com")
                .build();

        when(sessionRepository.findById("sess-abc")).thenReturn(Optional.of(expected));

        ChatSession result = sessionService.getSession("sess-abc");

        assertThat(result.getSessionId()).isEqualTo("sess-abc");
    }

    @Test
    @DisplayName("getSession() should throw SessionNotFoundException for unknown session ID")
    void getSession_shouldThrowWhenNotFound() {
        when(sessionRepository.findById("unknown")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> sessionService.getSession("unknown"))
                .isInstanceOf(SessionNotFoundException.class)
                .hasMessageContaining("unknown");
    }

    // ─────────────────────── getActiveSessions ───────────────────────────────

    @Test
    @DisplayName("getActiveSessions() should return only sessions with active=true")
    void getActiveSessions_shouldReturnOnlyActive() {
        ChatSession active  = ChatSession.builder().sessionId("s1").identity("a@b.com").build();
        ChatSession inactive = ChatSession.builder().sessionId("s2").identity("c@d.com").active(false).build();

        when(sessionRepository.findAll()).thenReturn(List.of(active, inactive));

        List<ChatSession> result = sessionService.getActiveSessions();

        assertThat(result).hasSize(1);
            assertThat(result.getFirst().getSessionId()).isEqualTo("s1");
    }

    // ─────────────────────── getSessionsByIdentity ───────────────────────────

    @Test
    @DisplayName("getSessionsByIdentity() should delegate to repository")
    void getSessionsByIdentity_shouldDelegate() {
        ChatSession s = ChatSession.builder().sessionId("s1").identity("alice@example.com").build();
        when(sessionRepository.findByIdentity("alice@example.com")).thenReturn(List.of(s));

        List<ChatSession> result = sessionService.getSessionsByIdentity("alice@example.com");

        assertThat(result).hasSize(1);
            assertThat(result.getFirst().getIdentity()).isEqualTo("alice@example.com");
    }

    // ─────────────────────── deactivateSession ───────────────────────────────

    @Test
    @DisplayName("deactivateSession() should set active=false and save")
    void deactivateSession_shouldMarkInactive() {
        ChatSession session = ChatSession.builder()
                .sessionId("sess-del")
                .identity("user@example.com")
                .build();

        when(sessionRepository.findById("sess-del")).thenReturn(Optional.of(session));
        when(sessionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        sessionService.deactivateSession("sess-del");

        assertThat(session.isActive()).isFalse();
        verify(sessionRepository).save(session);
    }
}
