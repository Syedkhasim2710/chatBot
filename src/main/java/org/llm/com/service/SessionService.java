package org.llm.com.service;

import org.llm.com.dto.request.CreateSessionRequest;
import org.llm.com.model.ChatSession;

import java.util.List;

public interface SessionService {
    ChatSession createSession(CreateSessionRequest request);
    ChatSession getSession(String sessionId);
    List<ChatSession> getActiveSessions();
    List<ChatSession> getSessionsByIdentity(String identity);
    void deactivateSession(String sessionId);
}
