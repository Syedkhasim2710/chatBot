package org.llm.com.service;

import lombok.extern.slf4j.Slf4j;
import org.apache.tika.Tika;
import org.apache.tika.exception.TikaException;
import org.apache.tika.metadata.Metadata;
import org.llm.com.exception.ChatbotException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Set;

/**
 * Extracts plain text from uploaded documents using Apache Tika.
 *
 * <h3>Security (InfoSec)</h3>
 * <ul>
 *   <li><b>MIME re-detection</b>: Tika detects the real MIME type from file
 *       magic bytes — the {@code Content-Type} header provided by the browser
 *       is never trusted.</li>
 *   <li><b>Whitelist policy</b>: Only PDF, DOC, DOCX, and plain-text are
 *       accepted.  All other types are rejected with HTTP 400.</li>
 *   <li><b>Size cap</b>: File size is checked before parsing (default 10 MB)
 *       and extracted text is truncated at {@code max-extracted-chars}
 *       (default 8 000 chars) to bound LLM token usage.</li>
 *   <li><b>No disk writes</b>: Content is processed entirely in memory via
 *       {@link ByteArrayInputStream} — no temp files are created.</li>
 * </ul>
 *
 * <h3>Architecture</h3>
 * <ul>
 *   <li>A single {@link Tika} instance is reused (thread-safe, stateless detector).</li>
 *   <li>Parsing uses {@link Tika#parseToString(InputStream, Metadata, int)} which
 *       hard-limits output length without loading the full document into memory.</li>
 * </ul>
 */
@Slf4j
@Service
public class DocumentParserService {

    // [InfoSec] Server-side MIME whitelist — Tika-detected, not browser-supplied
    private static final Set<String> ALLOWED_MIME_TYPES = Set.of(
            "application/pdf",
            "application/msword",
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            "text/plain"
    );

    private static final long MAX_FILE_BYTES = 10L * 1024 * 1024; // 10 MB

    /** Shared, thread-safe Tika facade. */
    private final Tika tika = new Tika();

    @Value("${chatbot.upload.max-extracted-chars:8000}")
    private int maxExtractedChars;

    /**
     * Validates and extracts text from a multipart upload.
     *
     * @param file the uploaded file
     * @return plain-text content (truncated at {@code maxExtractedChars})
     * @throws ChatbotException with a user-friendly message for any validation or parse error
     */
    public String extractText(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new ChatbotException("INVALID_FILE", "No file provided or file is empty.");
        }

        // [InfoSec] Enforce size before reading into memory
        if (file.getSize() > MAX_FILE_BYTES) {
            throw new ChatbotException("FILE_TOO_LARGE",
                    "File exceeds the 10 MB limit. Please upload a smaller document.");
        }

        byte[] bytes;
        try {
            bytes = file.getBytes();
        } catch (IOException e) {
            throw new ChatbotException("FILE_READ_ERROR",
                    "Could not read uploaded file: " + e.getMessage());
        }

        // [InfoSec] Re-detect MIME from magic bytes — ignore browser-supplied type
        String detectedMime = tika.detect(bytes, file.getOriginalFilename());
        log.debug("Uploaded file=[{}] browser-type=[{}] tika-detected=[{}]",
                file.getOriginalFilename(), file.getContentType(), detectedMime);

        boolean allowed = ALLOWED_MIME_TYPES.stream().anyMatch(detectedMime::startsWith);
        if (!allowed) {
            throw new ChatbotException("UNSUPPORTED_FILE_TYPE",
                    "Unsupported file type '" + detectedMime + "'. " +
                    "Accepted formats: PDF, DOC, DOCX, plain text.");
        }

        try (InputStream is = new ByteArrayInputStream(bytes)) {
            // parseToString hard-stops at maxExtractedChars — no full doc load
            String text = tika.parseToString(is, new Metadata(), maxExtractedChars);
            log.info("Document parsed: file=[{}] mimeType=[{}] extractedChars=[{}]",
                    file.getOriginalFilename(), detectedMime, text.length());
            return text;
        } catch (TikaException | IOException e) {
            log.error("Tika parse error for file=[{}]: {}", file.getOriginalFilename(), e.getMessage());
            throw new ChatbotException("PARSE_ERROR",
                    "Failed to extract text from the document. Is the file corrupted?");
        }
    }
}

