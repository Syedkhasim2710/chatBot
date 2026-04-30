package org.llm.com.model;

/**
 * Role of a participant in a conversation.
 */
public enum MessageRole {
    /** Human / end-user message. */
    USER,

    /** AI-generated response. */
    ASSISTANT,

    /** System instruction injected by the application. */
    SYSTEM
}

