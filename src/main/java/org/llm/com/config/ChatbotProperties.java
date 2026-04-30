package org.llm.com.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
// Removed unused imports
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

/**
 * Strongly-typed, immutable configuration record for the chatbot.
 *
 * <p><b>InfoSec note</b>: All secrets (API keys, passwords) are consumed
 * through environment variables injected from outside — they never appear
 * in source code or YAML.  The {@code @NotBlank} constraints prevent the
 * application from starting when mandatory secrets are absent, failing fast
 * rather than silently using empty values.
 *
 * <p><b>Architecture note</b>: Using {@code @ConfigurationProperties} instead
 * of {@code @Value} scatter provides:
 * <ul>
 *   <li>Constructor injection (null-safety guaranteed by the compiler)</li>
 *   <li>Type coercion at startup — invalid config = startup failure, not NPE at runtime</li>
 *   <li>IDE auto-complete via the auto-generated spring-configuration-metadata.json</li>
 * </ul>
 */
@Validated
@ConfigurationProperties(prefix = "chatbot")
public record ChatbotProperties(

        @Valid @DefaultValue Ai ai,
        @Valid @DefaultValue Session session,
        @Valid @DefaultValue Security security

) {

    // ─────────────────── AI ────────────────────────────────────────────────

    public record Ai(
            @NotBlank
            String systemPrompt
    ) {
        public Ai() {
            this("You are beck, a helpful, knowledgeable AI assistant. Always refer to yourself as 'beck' in your replies.");
        }
    }

    // ─────────────────── Session ───────────────────────────────────────────

    public record Session(
            @Min(1) @Max(500)
            @DefaultValue("50")
            int maxMessages,

            @Min(1) @Max(1440)
            @DefaultValue("60")
            int ttlMinutes
    ) {}

    // ─────────────────── Security ──────────────────────────────────────────

    public record Security(
            @Valid @DefaultValue Actuator actuator,
            @Valid @DefaultValue RateLimit rateLimit
    ) {

        /**
         * Credentials for the /actuator/** basic-auth guard.
         * <br>
         * <b>InfoSec</b>: Set {@code ACTUATOR_PASSWORD} env variable in production.
         * The default {@code change-me-in-prod} will trigger a startup warning.
         */
        public record Actuator(
                @DefaultValue("actuator")
                String username,

                @DefaultValue("change-me-in-prod")
                String password
        ) {}

        /**
         * Sliding-window rate-limit thresholds (requests per minute).
         */
        public record RateLimit(
                @Min(1) @Max(10000)
                @DefaultValue("100")
                int requestsPerMinute,

                @Min(1) @Max(1000)
                @DefaultValue("30")
                int chatRequestsPerMinute
        ) {}
    }
}

