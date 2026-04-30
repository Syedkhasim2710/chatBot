package org.llm.com.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/** A single message in a chat session. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChatMessage {

    private MessageRole role;
    private String content;

    @Builder.Default
    private Instant timestamp = Instant.now();
}
