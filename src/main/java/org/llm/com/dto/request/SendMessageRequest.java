package org.llm.com.dto.request;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request body to send a user message within an existing session.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
@Schema(description = "Payload to send a message to the AI chatbot")
public class SendMessageRequest {

    @NotBlank(message = "message content cannot be blank")
    @Size(min = 1, max = 8000, message = "message must be between 1 and 8000 characters")
    @Schema(
        description = "The user's message text",
        example = "Explain virtual threads in Java 21 and when to prefer them over platform threads.",
        requiredMode = Schema.RequiredMode.REQUIRED
    )
    private String content;
}
