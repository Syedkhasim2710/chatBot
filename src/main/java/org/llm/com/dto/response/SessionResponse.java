package org.llm.com.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import org.llm.com.model.ChatSession;

import java.time.Instant;

/**
 * API representation of a {@link ChatSession}.
 */
@Schema(description = "Chat session summary")
public record SessionResponse(
        @Schema(example = "550e8400-e29b-41d4-a716-446655440000")
        String sessionId,
        @Schema(example = "khasim@email.com")
        String identity,
        boolean active,
        int messageCount,
        Instant createdAt,
        Instant lastActiveAt
) {
    public static SessionResponse from(ChatSession s) {
        return new SessionResponse(
                s.getSessionId(),
                s.getIdentity(),
                s.isActive(),
                s.getMessageCount(),
                s.getCreatedAt(),
                s.getLastActiveAt()
        );
    }
}
