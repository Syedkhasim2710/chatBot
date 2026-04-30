package org.llm.com.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.llm.com.dto.request.CreateSessionRequest;
import org.llm.com.dto.response.ApiResponse;
import org.llm.com.dto.response.SessionResponse;
import org.llm.com.service.SessionService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * REST controller for chat session lifecycle management.
 *
 * <p>Base path: {@code /api/v1/sessions}
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/sessions")
@Tag(name = "Sessions", description = "Create and manage chat sessions")
public class SessionController {

    private final SessionService sessionService;

    public SessionController(SessionService sessionService) {
        this.sessionService = sessionService;
    }

    // ─────────────────────── POST /api/v1/sessions ──────────────────────────

    @Operation(summary = "Create a new session", description = "Pass your name or email as identity.")
    @PostMapping
    public ResponseEntity<ApiResponse<SessionResponse>> create(@Valid @RequestBody CreateSessionRequest req) {
        log.info("POST /sessions identity=[{}]", req.getIdentity());
        var session = sessionService.createSession(req);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok("Session created", SessionResponse.from(session)));
    }

    // ─────────────────────── GET /api/v1/sessions/active ───────────────────────────

    @Operation(summary = "Get all active sessions")
    @GetMapping("/active")
    public ResponseEntity<ApiResponse<List<SessionResponse>>> activeSessions() {
        var list = sessionService.getActiveSessions().stream().map(SessionResponse::from).toList();
        return ResponseEntity.ok(ApiResponse.ok(list));
    }

    // ─────────────────────── GET /api/v1/sessions ──────────────────────

    @Operation(summary = "Get all sessions for an identity (name or email)")
    @GetMapping
    public ResponseEntity<ApiResponse<List<SessionResponse>>> byIdentity(
            @RequestParam String identity) {
        var list = sessionService.getSessionsByIdentity(identity).stream().map(SessionResponse::from).toList();
        return ResponseEntity.ok(ApiResponse.ok(list));
    }

    // ─────────────────────── GET /api/v1/sessions/{id} ──────────────────────

    @Operation(summary = "Get session by ID")
    @GetMapping("/{sessionId}")
    public ResponseEntity<ApiResponse<SessionResponse>> getById(@PathVariable String sessionId) {
        return ResponseEntity.ok(ApiResponse.ok(SessionResponse.from(sessionService.getSession(sessionId))));
    }

    // ─────────────────────── DELETE /api/v1/sessions/{id} ───────────────────

    @Operation(summary = "Deactivate a session")
    @DeleteMapping("/{sessionId}")
    public ResponseEntity<ApiResponse<Void>> deactivate(@PathVariable String sessionId) {
        sessionService.deactivateSession(sessionId);
        return ResponseEntity.ok(ApiResponse.ok("Session deactivated", null));
    }
}
