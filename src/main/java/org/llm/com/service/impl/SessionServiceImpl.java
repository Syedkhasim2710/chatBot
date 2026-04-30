package org.llm.com.service.impl;

import lombok.extern.slf4j.Slf4j;
import org.llm.com.dto.request.CreateSessionRequest;
import org.llm.com.exception.SessionNotFoundException;
import org.llm.com.model.ChatSession;
import org.llm.com.repository.SessionRepository;
import org.llm.com.service.SessionService;
import org.springframework.stereotype.Service;

import java.util.List;

@Slf4j
@Service
public class SessionServiceImpl implements SessionService {

    private final SessionRepository repository;

    public SessionServiceImpl(SessionRepository repository) {
        this.repository = repository;
    }

    @Override
    public ChatSession createSession(CreateSessionRequest request) {
        ChatSession session = ChatSession.builder()
                .identity(request.getIdentity())
                .build();
        repository.save(session);
        log.info("Session [{}] created for identity=[{}]", session.getSessionId(), session.getIdentity());
        return session;
    }

    @Override
    public ChatSession getSession(String sessionId) {
        return repository.findById(sessionId)
                .orElseThrow(() -> new SessionNotFoundException(sessionId));
    }

    @Override
    public List<ChatSession> getActiveSessions() {
        return repository.findAll().stream()
                .filter(ChatSession::isActive)
                .toList();
    }

    @Override
    public List<ChatSession> getSessionsByIdentity(String identity) {
        return repository.findByIdentity(identity);
    }

    @Override
    public void deactivateSession(String sessionId) {
        ChatSession session = getSession(sessionId);
        session.setActive(false);
        repository.save(session);
        log.info("Session [{}] deactivated", sessionId);
    }
}
