package org.llm.com.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** A user's chat session — stores only identity + active flag + messages. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChatSession {

    @Builder.Default
    private String sessionId = UUID.randomUUID().toString();

    /** Name or email supplied at session creation. */
    private String identity;

    @Builder.Default
    private boolean active = true;

    @Builder.Default
    private Instant createdAt = Instant.now();

    @Builder.Default
    private Instant lastActiveAt = Instant.now();

    @Builder.Default
    private List<ChatMessage> messages = new ArrayList<>();

    public void addMessage(ChatMessage message) {
        this.messages.add(message);
        this.lastActiveAt = Instant.now();
    }

    public int getMessageCount() {
        return messages.size();
    }
}
