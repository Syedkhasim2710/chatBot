package org.llm.com.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * Generic API envelope for all responses.
 *
 * <p>Follows the pattern:
 * <pre>
 * {
 *   "success": true,
 *   "message": "Session created",
 *   "data": { ... },
 *   "timestamp": "2026-04-25T10:00:00Z"
 * }
 * </pre>
 *
 * @param <T> type of the payload object
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "Standard API response envelope")
public class ApiResponse<T> {

    @Schema(description = "Whether the operation succeeded", example = "true")
    private boolean success;

    @Schema(description = "Human-readable status message", example = "Session created successfully")
    private String message;

    @Schema(description = "Response payload")
    private T data;

    @Builder.Default
    @Schema(description = "UTC timestamp of the response")
    private Instant timestamp = Instant.now();

    // ───────────────── Factory helpers ─────────────────────────────────────

    public static <T> ApiResponse<T> ok(String message, T data) {
        return ApiResponse.<T>builder()
                .success(true)
                .message(message)
                .data(data)
                .build();
    }

    public static <T> ApiResponse<T> ok(T data) {
        return ok("OK", data);
    }

    public static <T> ApiResponse<T> error(String message) {
        return ApiResponse.<T>builder()
                .success(false)
                .message(message)
                .build();
    }
}

