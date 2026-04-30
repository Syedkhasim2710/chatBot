# Project Run & Setup Instructions

This file provides step-by-step instructions to set up and run both the backend (Spring Boot) and frontend (Next.js) for your Chatbot project, including batch PDF ingestion for RAG.

---

## 1. Prerequisites & Setup

### a. Required Software
- **Java:** Amazon Corretto 25 (JDK 25)
- **Ollama:** 0.21+ ([ollama.com](https://ollama.com))
- **Node.js & npm:** (for frontend, [nodejs.org](https://nodejs.org/))
- **Git:** (to clone the repository)

### b. Clone the Repository
```bash
git clone <your-repo-url>
cd chatbot
```

### c. Pull Required LLM Models (Ollama)
```bash
ollama pull llama3.2
ollama pull mistral
```

### d. Install Backend Dependencies
```bash
./gradlew build --refresh-dependencies
```

### e. Install Frontend Dependencies
```bash
cd frontend
npm install
cd ..
```

---

## 2. Backend (Spring Boot)

### a. Start Ollama
```bash
ollama serve
```

### b. Run Backend
```bash
./gradlew bootRun
```
- The backend will be available at: `http://localhost:8081/chatbot`
- Swagger UI: `http://localhost:8081/chatbot/swagger-ui.html`

### c. Batch PDF Ingestion via /chat Endpoint
- To ingest all PDFs from a directory for a session, use:
```bash
curl -X POST "http://localhost:8081/chatbot/api/v1/sessions/{sessionId}/chat?pdfDirectory=/absolute/path/to/ollamaRAG" \
  -H "Content-Type: application/json" \
  -d '{"content": "Summarize all PDFs."}'
```
- Replace `{sessionId}` and the directory path as needed.

---

## 3. Frontend (Next.js)

### a. Run Frontend
```bash
cd frontend
npm run dev
```
- The frontend will be available at: `http://localhost:3000`
- It proxies API requests to the backend at `http://localhost:8081/chatbot/api/v1`

### b. Custom Backend URL (optional)
```bash
CHATBOT_BACKEND_URL=http://localhost:8081/chatbot/api/v1 npm run dev
```

---

## 4. Additional Notes
- All file validation and RAG ingestion is handled in Java (no Python required).
- Vector store is always in-memory (never external).
- For more details, see `README.md`, `BATCH_INGEST_FEATURE.md`, and `frontend/README.md`.
- You can use Swagger UI for API exploration and testing.

---

## 5. Troubleshooting
- Ensure Ollama is running and models are pulled before starting the backend.
- If you change the backend port or context path, update the frontend proxy config in `frontend/next.config.mjs` or use the `CHATBOT_BACKEND_URL` env variable.
- For API and error details, see Swagger UI or the markdown docs.
- If you encounter dependency issues, run `./gradlew build --refresh-dependencies` again.

---

## 6. Toolchain & Major Dependencies

### Backend (Java/Spring Boot)
- **Spring Boot 3.4.4** — REST API, dependency injection, configuration
- **Spring AI 1.0.0** — LLM orchestration, Ollama integration, RAG
- **LangChain4j 0.36.2** — Cloud fallback (Groq), in-memory vector store
- **Ollama** — Local LLM runner (llama3.2, mistral)
- **Apache Tika 2.9.2** — Secure document parsing (PDF, DOC, DOCX, TXT)
- **Caffeine** — In-memory session cache
- **Lombok** — Boilerplate reduction
- **Gradle 9** — Build tool

### Frontend (Next.js)
- **Next.js** — React-based frontend framework
- **Node.js & npm** — JavaScript runtime and package manager
- **Proxy** — API requests from frontend are proxied to backend (see `frontend/next.config.mjs`)

### Other Tools
- **Swagger UI** — API documentation and testing (`/swagger-ui.html`)
- **Prometheus/Micrometer** — Observability and metrics
- **SpringDoc** — OpenAPI/Swagger integration
- **JUnit** — Testing framework

---

Enjoy your full-stack AI chatbot with batch PDF RAG!
