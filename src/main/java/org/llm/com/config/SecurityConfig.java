package org.llm.com.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.annotation.web.configurers.HeadersConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter;

/**
 * Spring Security configuration.
 * <p>
 * Public  — Swagger UI, OpenAPI JSON, health, info, metrics, Prometheus, all /api/v1/**
 * Basic Auth (ACTUATOR role) — env, heapdump, threaddump, loggers
 */
@Slf4j
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Value("${chatbot.security.actuator.username:actuator}")
    private String actuatorUsername;

    @Value("${chatbot.security.actuator.password:change-me-in-prod}")
    private String actuatorPassword;

    // ── Swagger / OpenAPI — always public ────────────────────────────────────
    private static final String[] SWAGGER_PATHS = {
            "/swagger-ui.html",
            "/swagger-ui/**",
            "/v3/api-docs",
            "/v3/api-docs/**",
            "/webjars/**"
    };

    // ── Safe actuator endpoints — public read-only ───────────────────────────
    private static final String[] PUBLIC_ACTUATOR_PATHS = {
            "/actuator/health",
            "/actuator/health/**",
            "/actuator/info",
            "/actuator/metrics",
            "/actuator/metrics/**",
            "/actuator/prometheus",
            "/actuator/scheduledtasks"
    };

    // ── Sensitive actuator endpoints — require ACTUATOR role ─────────────────
    private static final String[] SENSITIVE_ACTUATOR_PATHS = {
            "/actuator/env",
            "/actuator/env/**",
            "/actuator/heapdump",
            "/actuator/threaddump",
            "/actuator/loggers",
            "/actuator/loggers/**"
    };

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {

        http
            .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .csrf(AbstractHttpConfigurer::disable)
            .authorizeHttpRequests(auth -> auth
                .requestMatchers(SWAGGER_PATHS).permitAll()
                .requestMatchers(PUBLIC_ACTUATOR_PATHS).permitAll()
                .requestMatchers(SENSITIVE_ACTUATOR_PATHS).hasRole("ACTUATOR")
                .requestMatchers("/api/v1/**").permitAll()
                .anyRequest().authenticated()
            )
            .httpBasic(Customizer.withDefaults())
            .headers(headers -> headers
                .contentTypeOptions(Customizer.withDefaults())
                .frameOptions(HeadersConfigurer.FrameOptionsConfig::deny)
                .httpStrictTransportSecurity(hsts -> hsts
                    .includeSubDomains(true)
                    .maxAgeInSeconds(31_536_000)
                )
                .referrerPolicy(rp -> rp
                    .policy(ReferrerPolicyHeaderWriter.ReferrerPolicy.STRICT_ORIGIN_WHEN_CROSS_ORIGIN)
                )
                .contentSecurityPolicy(csp -> csp
                    .policyDirectives(
                        "default-src 'self'; " +
                        "script-src 'self' 'unsafe-inline'; " +
                        "style-src 'self' 'unsafe-inline'; " +
                        "img-src 'self' data:; " +
                        "frame-ancestors 'none';"
                    )
                )
            );

        log.info("[Security] FilterChain ready — actuator user='{}'", actuatorUsername);
        return http.build();
    }

    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(12);
    }

    @Bean
    UserDetailsService userDetailsService(PasswordEncoder encoder) {
        var user = User.builder()
                .username(actuatorUsername)
                .password(encoder.encode(actuatorPassword))
                .roles("ACTUATOR")
                .build();
        return new InMemoryUserDetailsManager(user);
    }
}
