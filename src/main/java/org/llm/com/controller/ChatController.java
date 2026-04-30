package org.llm.com.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.extern.slf4j.Slf4j;
import org.llm.com.dto.request.SendMessageRequest;
import org.llm.com.dto.response.ApiResponse;
import org.llm.com.dto.response.ChatResponse;
import org.llm.com.dto.response.MessageResponse;
import org.llm.com.service.ChatService;
import org.llm.com.service.DocumentParserService;
import org.llm.com.service.RagService;
import org.llm.com.service.BatchIngestService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import java.util.List;

@Slf4j
@Validated
@RestController
@RequestMapping("/api/v1/sessions/{sessionId}")
@Tag(name = "Chat", description = "Send messages to the AI and retrieve conversation history")
public class ChatController {

    private final ChatService chatService;
    private final DocumentParserService documentParserService;
    @Autowired(required = false)
    private BatchIngestService batchIngestService;
    @Autowired(required = false)
    private RagService ragService;

    public ChatController(ChatService chatService, DocumentParserService documentParserService, BatchIngestService batchIngestService) {
        this.chatService = chatService;
        this.documentParserService = documentParserService;
        this.batchIngestService = batchIngestService;
    }

    @Operation(summary = "Send a message to the AI", description = "Send a user message and get an AI reply. Context enrichment: URLs fetched, web search, RAG, weather data.")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "AI response returned"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Validation failed", content = @Content),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Session not found", content = @Content),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "500", description = "AI provider error", content = @Content)
    })
    @PostMapping("/chat")
    public ResponseEntity<ApiResponse<ChatResponse>> sendMessage(
            @Parameter(description = "Session UUID", required = true) @PathVariable String sessionId,
            @Valid @RequestBody SendMessageRequest request,
            @RequestParam(value = "pdfDirectory", required = false) String pdfDirectory) {
        log.info("POST chat | session=[{}]", sessionId);
        if (batchIngestService != null && pdfDirectory != null && !pdfDirectory.isBlank()) {
            try {
                batchIngestService.ingestAllPdfsFromDirectory(sessionId, pdfDirectory);
                log.info("Batch-ingested PDFs from directory: {}", pdfDirectory);
            } catch (Exception e) {
                log.error("Batch PDF ingest failed: {}", e.getMessage());
                return ResponseEntity.badRequest().body(ApiResponse.error("Batch PDF ingest failed: " + e.getMessage()));
            }
        }
        ChatResponse response = chatService.sendMessage(sessionId, request);
        return ResponseEntity.ok(ApiResponse.ok("Message processed", response));
    }

    @Operation(summary = "Send a message with an optional document attachment", description = "Accepts multipart/form-data: content (required), file (optional, PDF/DOC/DOCX/TXT, max 10MB). File is parsed and ingested for RAG.")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "AI response returned"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Validation failed / unsupported file / file too large", content = @Content),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Session not found", content = @Content),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "500", description = "AI provider error", content = @Content)
    })
    @PostMapping(value = "/chat/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<ChatResponse>> sendMessageWithFile(
            @Parameter(description = "Session UUID", required = true)
            @PathVariable String sessionId,
            @Parameter(description = "User text question", required = true)
            @RequestParam("content")
            @NotBlank(message = "content cannot be blank")
            @Size(min = 1, max = 8000, message = "content must be 1-8000 characters")
            String content,
            @Parameter(description = "Optional document (PDF, DOC, DOCX, TXT - max 10 MB)")
            @RequestParam(value = "file", required = false)
            MultipartFile file) {
        log.info("POST chat/upload | session=[{}] file=[{}]", sessionId, file != null ? file.getOriginalFilename() : "none");
        String additionalContext = null;
        try {
            if (file != null && !file.isEmpty()) {
                String docText = documentParserService.extractText(file);
                String filename = file.getOriginalFilename();
                additionalContext = "[DOCUMENT CONTENT: " + filename + "]\n" + docText;
                if (ragService != null) {
                    ragService.ingestDocument(sessionId, filename, docText);
                    log.debug("Document ingested into RAG vector store: session=[{}] file=[{}]", sessionId, filename);
                }
            }
        } catch (org.llm.com.exception.ChatbotException ex) {
            String message = "Attachment error: " + ex.getMessage() + " Limitations: Max file size 10 MB. Allowed types: PDF, DOC, DOCX, TXT. Max extracted text: 8,000 characters.";
            return ResponseEntity.badRequest().body(ApiResponse.error(message));
        }
        SendMessageRequest request = SendMessageRequest.builder().content(content).build();
        ChatResponse response = chatService.sendMessage(sessionId, request, additionalContext);
        return ResponseEntity.ok(ApiResponse.ok("Message processed", response));
    }

    @Operation(summary = "Get conversation history", description = "Returns all messages in chronological order for the session.")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "History returned"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Session not found", content = @Content)
    })
    @GetMapping("/history")
    public ResponseEntity<ApiResponse<List<MessageResponse>>> getHistory(@PathVariable String sessionId) {
        log.info("GET history | session=[{}]", sessionId);
        List<MessageResponse> messages = chatService.getHistory(sessionId).stream().map(MessageResponse::from).toList();
        return ResponseEntity.ok(ApiResponse.ok("History retrieved (" + messages.size() + " messages)", messages));
    }
}
