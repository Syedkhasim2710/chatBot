package org.llm.com.dto.request;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Create a new chat session. Supply your name or email as identity. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
@Schema(description = "Request to open a new chat session")
public class CreateSessionRequest {

    @NotBlank(message = "identity is required (name or email)")
    @Size(max = 100)
    @Schema(description = "Your name or email address",
            example = "khasim@email.com",
            requiredMode = Schema.RequiredMode.REQUIRED)
    private String identity;
}
