# Batch PDF Ingestion via /chat Endpoint

## Overview
This document describes how the `/chat` endpoint in `ChatController` has been extended to support batch ingestion of all PDF files from a specified directory into the RAG vector store for a session.

## Usage
- **Endpoint:** `POST /api/v1/sessions/{sessionId}/chat`
- **Parameters:**
  - JSON body: your usual chat message (`SendMessageRequest`)
  - Optional query parameter: `pdfDirectory` (absolute path to directory containing PDFs)

**Example:**
```
POST /api/v1/sessions/{sessionId}/chat?pdfDirectory=/Users/syed.khasim/Documents/Mera/chatbot/ollamaRAG
Content-Type: application/json
{
  "content": "What are the main topics in these PDFs?"
}
```

## How it Works
- If `pdfDirectory` is provided and non-empty, the controller will:
  1. Call `BatchIngestService.ingestAllPdfsFromDirectory(sessionId, pdfDirectory)`
  2. This service scans the directory for `.pdf` files, parses each using `DocumentParserService`, and ingests the extracted text into the RAG vector store via `RagService`.
  3. After ingestion, the chat message is processed as usual.

## Error Handling
- If the directory does not exist or ingestion fails, a 400 error is returned with a descriptive message.
- All attachment and ingestion limitations (max file size, allowed types, max extracted text) are enforced as per the rest of the system.

## Example Code Snippet
```
// In ChatController.java
@PostMapping("/chat")
public ResponseEntity<ApiResponse<ChatResponse>> sendMessage(
    @PathVariable String sessionId,
    @Valid @RequestBody SendMessageRequest request,
    @RequestParam(value = "pdfDirectory", required = false) String pdfDirectory) {

    if (pdfDirectory != null && !pdfDirectory.isBlank()) {
        batchIngestService.ingestAllPdfsFromDirectory(sessionId, pdfDirectory);
    }
    ChatResponse response = chatService.sendMessage(sessionId, request);
    return ResponseEntity.ok(ApiResponse.ok("Message processed", response));
}
```

## Security & InfoSec
- All file validation (size, MIME type, text length) is performed in Java using Apache Tika.
- No Python or external scripts are used.
- Vector store is always in-memory (never external).

## Limitations
- Max file size: 10 MB
- Allowed types: PDF, DOC, DOCX, TXT (detected by Tika)
- Max extracted text: 8,000 chars
- Only files in the specified directory are ingested; subdirectories are not scanned.

# Batch Ingest Feature: LLM Model and Toolchain Details

## LLM Model Used

- **OllamaEmbeddingModel (nomic-embed-text)**
  - Used for embedding documents and enabling Retrieval-Augmented Generation (RAG).
  - Integrated via Spring AI and LangChain4j.
  - Embeddings are stored in a SimpleVectorStore (in-memory vector store).

- **AI Chat Model**
  - The main LLM used for generating chat responses is an Ollama-supported model (such as Llama 2, Mistral, or similar), configured in the backend.
  - The model name is returned in the frontend as `lastModel` (e.g., "Model llama2"), which is set from the backend response.

## Tools and Libraries Used

- **Spring AI**: For LLM orchestration, RAG, and vector store management.
- **LangChain4j**: For chaining LLM, embedding, and retrieval logic.
- **Ollama**: For running open-source LLMs and embedding models locally.
- **Apache Tika**: For secure server-side document parsing and MIME detection.
- **Spring Boot**: For REST API and service orchestration.
- **Swagger/OpenAPI**: For API documentation.
- **Lombok**: For boilerplate code reduction.
- **Jakarta Validation**: For request validation.
- **SLF4J**: For logging.

## Summary

- The backend uses Ollama-supported LLMs for chat and `OllamaEmbeddingModel (nomic-embed-text)` for embeddings.
- RAG is implemented using Spring AI, LangChain4j, and Ollama.
- Document parsing and validation are handled by Apache Tika.

For the exact LLM model used for chat generation, refer to the backend configuration (e.g., `application.yml` or `RagService/ChatService` setup).

---

For further details, see the implementation in `ChatController.java` and `BatchIngestService.java`.
