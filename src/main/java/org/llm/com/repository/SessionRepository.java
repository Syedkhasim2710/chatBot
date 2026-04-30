package org.llm.com.repository;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import lombok.extern.slf4j.Slf4j;
import org.llm.com.model.ChatSession;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

/**
 * Caffeine-backed session store.
 *
 * <p>Caffeine provides O(1) reads/writes, automatic TTL-based expiry, and
 * optional size-based eviction — all without a scheduled eviction job.
 * Sessions are expired automatically after {@code ttlMinutes} of inactivity
 * (access-based expiry: each chat call resets the TTL).
 *
 * <p>For production replace with Redis via {@code spring-boot-starter-data-redis}.
 */
@Slf4j
@Repository
public class SessionRepository {

    private final Cache<String, ChatSession> cache;

    public SessionRepository(
            @Value("${chatbot.session.ttl-minutes:60}") int ttlMinutes,
            @Value("${chatbot.session.max-sessions:10000}") int maxSessions) {

        this.cache = Caffeine.newBuilder()
                .maximumSize(maxSessions)
                .expireAfterAccess(ttlMinutes, TimeUnit.MINUTES)
                .removalListener((key, session, cause) ->
                        log.info("Session evicted [{}] cause={}", key, cause))
                .build();

        log.info("SessionRepository initialised — ttl={}min maxSize={}", ttlMinutes, maxSessions);
    }

    public ChatSession save(ChatSession session) {
        cache.put(session.getSessionId(), session);
        return session;
    }

    public Optional<ChatSession> findById(String sessionId) {
        return Optional.ofNullable(cache.getIfPresent(sessionId));
    }

    public Collection<ChatSession> findAll() {
        return List.copyOf(cache.asMap().values());
    }

    public List<ChatSession> findByIdentity(String identity) {
        return cache.asMap().values().stream()
                .filter(s -> identity.equalsIgnoreCase(s.getIdentity()))
                .toList();
    }

    public void invalidate(String sessionId) {
        cache.invalidate(sessionId);
    }

    public long size() {
        return cache.estimatedSize();
    }
}

