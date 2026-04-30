package org.llm.com.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * API representation of a single AI chat exchange (one user turn + one assistant turn).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Result of one AI chat exchange")
public class ChatResponse {

    @Schema(description = "Session this message belongs to")
    private String sessionId;

    @Schema(description = "User identity")
    private String identity;

    @Schema(description = "The original user question", example = "What are virtual threads?")
    private String userMessage;

    @Schema(description = "The AI-generated reply")
    private String assistantMessage;

    @Schema(description = "AI model used", example = "llama3.2")
    private String model;

    @Schema(description = "AI provider/framework that generated the reply",
            example = "Spring AI / Ollama",
            allowableValues = {"Spring AI / Ollama", "Spring AI / Ollama fallback", "LangChain4j / Groq"})
    private String provider;

    @Schema(description = "Response generation timestamp")
    private Instant timestamp;

    @Schema(description = "Total messages in session so far", example = "6")
    private int totalMessages;
}
