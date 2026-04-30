package org.llm.com.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.llm.com.exception.ChatbotException;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.mock.web.MockMultipartFile;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
// ...existing code...

@Slf4j
@Service
@RequiredArgsConstructor
public class BatchIngestService {
    private final DocumentParserService documentParserService;
    private final RagService ragService;

    /**
     * Ingests all PDF files from the specified directory into the RAG vector store for the given session.
     *
     * @param sessionId    Session ID to associate with the ingested documents
     * @param directoryPath Absolute path to the directory containing PDF files
     */
    public void ingestAllPdfsFromDirectory(String sessionId, String directoryPath) {
        File dir = new File(directoryPath);
        if (!dir.exists() || !dir.isDirectory()) {
            throw new ChatbotException("DIRECTORY_NOT_FOUND", "Directory does not exist: " + directoryPath);
        }
        File[] pdfFiles = dir.listFiles((d, name) -> name.toLowerCase().endsWith(".pdf"));
        if (pdfFiles == null || pdfFiles.length == 0) {
            log.warn("No PDF files found in directory: {}", directoryPath);
            return;
        }
        for (File pdf : pdfFiles) {
            try (FileInputStream fis = new FileInputStream(pdf)) {
                MultipartFile multipartFile = new MockMultipartFile(
                        pdf.getName(),
                        pdf.getName(),
                        "application/pdf",
                        fis
                );
                String docText = documentParserService.extractText(multipartFile);
                ragService.ingestDocument(sessionId, pdf.getName(), docText);
                log.info("Ingested PDF for RAG: {}", pdf.getName());
            } catch (IOException | ChatbotException e) {
                log.error("Failed to ingest PDF: {} | {}", pdf.getName(), e.getMessage());
            }
        }
    }
}

