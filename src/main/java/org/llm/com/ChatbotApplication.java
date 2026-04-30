package org.llm.com;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Chatbot Service — Spring Boot 3.4.4 · Amazon Corretto 25 · Virtual Threads
 * <ul>
 *   <li>Spring AI 1.0.0 GA — Ollama (primary) + Ollama mistral (fallback)</li>
 *   <li>SpringDoc OpenAPI — Swagger UI at /chatbot/swagger-ui.html</li>
 *   <li>Actuator + Prometheus at /chatbot/actuator/*</li>
 * </ul>
 */
@Slf4j
@SpringBootApplication
public class ChatbotApplication {

    static void main(String[] args) {
        SpringApplication.run(ChatbotApplication.class, args);
        log.info("╔══════════════════════════════════════════════════════════════╗");
        log.info("║  🤖 beck Service — READY                                     ║");
        log.info("║  Swagger UI : http://localhost:8081/chatbot/swagger-ui.html  ║");
        log.info("║  Actuator   : http://localhost:8081/chatbot/actuator/health  ║");
        log.info("║  API Base   : http://localhost:8081/chatbot/api/v1           ║");
        log.info("╚══════════════════════════════════════════════════════════════╝");
    }
}
