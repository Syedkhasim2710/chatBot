package org.llm.com.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import org.llm.com.model.ChatMessage;

import java.time.Instant;

@Schema(description = "A single message in the conversation history")
public record MessageResponse(
        @Schema(example = "USER") String role,
        @Schema(example = "What are virtual threads?") String content,
        Instant timestamp
) {
    public static MessageResponse from(ChatMessage msg) {
        return new MessageResponse(msg.getRole().name(), msg.getContent(), msg.getTimestamp());
    }
}
