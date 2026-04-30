# 🤖 Chatbot Service

An enterprise-grade AI chatbot REST API built with **Spring Boot 3.4.4**, **Spring AI 1.0.0**, and **Ollama** (local, free, open-source LLMs). Supports automatic primary → fallback model switching, per-session conversation memory, and production-ready security.

---

## 📋 Table of Contents

- [Tech Stack](#-tech-stack)
- [Architecture](#-architecture)
- [Project Structure](#-project-structure)
- [Prerequisites](#-prerequisites)
- [Getting Started](#-getting-started)
- [Configuration](#-configuration)
- [API Reference](#-api-reference)
- [AI Models](#-ai-models)
- [Security](#-security)
- [Observability](#-observability)
- [Running Tests](#-running-tests)
- [Design Decisions](#-design-decisions)
- [Environment Variables](#-environment-variables)

---

## 🛠 Tech Stack

| Layer | Technology |
|-------|-----------|
| Runtime | Amazon Corretto 25 (JDK 25) |
| Framework | Spring Boot 3.4.4 |
| AI Tier 1 (primary) | Spring AI 1.0.0 GA → Ollama `llama3.2` (local) |
| AI Tier 2 (fallback) | Spring AI 1.0.0 GA → Ollama `mistral` (local) |
| AI Tier 3 (cloud) | LangChain4j 0.36.2 → Groq `llama-3.3-70b` (cloud, free) |
| Session Cache | Caffeine (in-memory, TTL-based) |
| Security | Spring Security 6 (CSRF disabled, stateless JWT-ready) |
| API Docs | SpringDoc OpenAPI 3 / Swagger UI 2.8.3 |
| Observability | Spring Actuator + Micrometer + Prometheus |
| Build | Gradle 9 |
| Concurrency | JDK 25 Virtual Threads (JEP 444 + JEP 491) |
| Boilerplate | Lombok 1.18.46 |

---

## 🏗 Architecture

```
┌─────────────────────────────────────────────────────────────┐
│                REST Layer (Virtual Threads)                  │
│   SessionController              ChatController              │
└────────────────┬─────────────────────────┬──────────────────┘
                 │                         │
        ┌────────▼──────┐        ┌─────────▼──────────────────┐
        │ SessionService│        │  ChatService (3-tier)       │
        └────────┬──────┘        └────┬─────────┬──────────┬──┘
                 │                   │          │          │
        ┌────────▼──────┐       [Tier 1]   [Tier 2]   [Tier 3]
        │SessionRepository│   Spring AI  Spring AI  LangChain4j
        │  [Caffeine TTL] │   llama3.2   mistral     Groq cloud
        └───────────────┘   (Ollama)    (Ollama)   (free tier)
                               local       local      internet
```

### 3-Tier Fallback Strategy

Every chat request is attempted through each tier in order:

```
Request
    │
    ▼
[Tier 1] Spring AI → llama3.2 (Ollama local, fastest, free, offline)
    │ success → return reply with provider="Spring AI / Ollama"
    │ fail ↓
[Tier 2] Spring AI → mistral (Ollama local, same infra, different model)
    │ success → return reply with provider="Spring AI / Ollama fallback"
    │ fail ↓ (Ollama is completely down)
[Tier 3] LangChain4j → Groq cloud (different framework + different infra)
    │ success → return reply with provider="LangChain4j / Groq"
    │ fail ↓ (all providers exhausted)
  AI_CALL_FAILED (503)
```

**Why two different frameworks?**
- Tiers 1 & 2 both use Spring AI + Ollama — if Ollama crashes, both fail together
- Tier 3 uses LangChain4j with Groq cloud — completely independent code path AND infrastructure
- A Spring AI bug can't affect the LangChain4j fallback, and vice versa

**Why LangChain4j without its Spring Boot starter?**
The `langchain4j-spring-boot-starter` auto-configures a `ChatModel` bean that collides with Spring AI's. Using only `langchain4j-open-ai` gives the HTTP client without any auto-configuration — no `allow-bean-definition-overriding` needed.

### Virtual Threads (JEP 444 + JEP 491)

- `spring.threads.virtual.enabled=true` makes Tomcat use a virtual thread per request
- **JEP 491 (JDK 24+, included in Corretto 25)**: `synchronized` blocks no longer pin virtual threads to carrier threads — no `ReentrantLock` migrations needed
- Blocking Ollama/Groq HTTP calls park the virtual thread, freeing the carrier for other work

---

## 📁 Project Structure

```
src/main/java/org/llm/com/
├── ChatbotApplication.java          # Entry point
├── config/
│   ├── AppConfig.java               # @EnableAsync, @EnableScheduling, @EnableRetry
│   ├── ChatbotProperties.java       # Typed config (@ConfigurationProperties)
│   ├── SecurityConfig.java          # Spring Security filter chain
│   ├── SpringAiConfig.java          # @Primary ChatClient (llama3.2 via Ollama)
│   ├── LlamaConfig.java             # Fallback ChatClient (mistral via Ollama)
│   └── OpenApiConfig.java           # Swagger / OpenAPI metadata
├── controller/
│   ├── SessionController.java       # Session lifecycle endpoints
│   └── ChatController.java          # Chat + history endpoints
├── service/
│   ├── ChatService.java             # Interface
│   ├── SessionService.java          # Interface
│   └── impl/
│       ├── ChatServiceImpl.java     # Primary/fallback AI routing
│       └── SessionServiceImpl.java
├── repository/
│   └── SessionRepository.java       # Caffeine-backed session store
├── model/
│   ├── ChatSession.java
│   ├── ChatMessage.java
│   └── MessageRole.java             # USER | ASSISTANT
├── dto/
│   ├── request/
│   │   ├── CreateSessionRequest.java
│   │   └── SendMessageRequest.java
│   └── response/
│       ├── ApiResponse.java         # Generic envelope {success, message, data}
│       ├── ChatResponse.java
│       ├── SessionResponse.java     # Java record
│       └── MessageResponse.java     # Java record
└── exception/
    ├── ChatbotException.java
    ├── SessionNotFoundException.java
    └── GlobalExceptionHandler.java  # @RestControllerAdvice
```

---

## ✅ Prerequisites

| Tool | Version | Install |
|------|---------|---------|
| Amazon Corretto | 25 | [corretto.aws](https://aws.amazon.com/corretto/) |
| Ollama | 0.21+ | [ollama.com](https://ollama.com) |
| `llama3.2` model | latest | `ollama pull llama3.2` |
| `mistral` model | latest | `ollama pull mistral` |
| Groq API key *(optional)* | — | [console.groq.com](https://console.groq.com) (free) |

> **Note**: Groq is only used as Tier 3 cloud fallback when both Ollama models fail. The app works fully offline without a Groq key.

---

## 🚀 Getting Started

### 1. Install and start Ollama

```bash
# macOS
brew install ollama

# Start the Ollama server
ollama serve
```

### 2. Pull the models

```bash
ollama pull llama3.2   # Primary  (~2 GB)
ollama pull mistral    # Fallback (~4.4 GB)
```

### 3. Run the application

```bash
./gradlew bootRun
```

The app starts on **http://localhost:8081/chatbot**

```
╔══════════════════════════════════════════════════════════════╗
║  🤖 Chatbot Service — READY                                  ║
║  Swagger UI : http://localhost:8081/chatbot/swagger-ui.html  ║
║  Actuator   : http://localhost:8081/chatbot/actuator/health  ║
║  API Base   : http://localhost:8081/chatbot/api/v1           ║
╚══════════════════════════════════════════════════════════════╝
```

### 4. Quick smoke test

```bash
# 1 — Create a session
SESSION=$(curl -s -X POST http://localhost:8081/chatbot/api/v1/sessions \
  -H "Content-Type: application/json" \
  -d '{"identity":"khasim@email.com"}' | python3 -c "import sys,json; print(json.load(sys.stdin)['data']['sessionId'])")

echo "Session ID: $SESSION"

# 2 — Send a message
curl -s -X POST http://localhost:8081/chatbot/api/v1/sessions/$SESSION/chat \
  -H "Content-Type: application/json" \
  -d '{"content":"Explain virtual threads in Java 21 in 3 sentences."}' | python3 -m json.tool

# 3 — View conversation history
curl -s http://localhost:8081/chatbot/api/v1/sessions/$SESSION/history | python3 -m json.tool
```

---

## ⚙️ Configuration

All settings are in `src/main/resources/application.yml`.

### Key properties

| Property | Default | Description |
|----------|---------|-------------|
| `server.port` | `8081` | HTTP port |
| `server.servlet.context-path` | `/chatbot` | URL prefix |
| `spring.ai.ollama.base-url` | `http://localhost:11434` | Ollama endpoint |
| `spring.ai.ollama.chat.options.model` | `llama3.2` | Primary model |
| `ollama.fallback.model` | `mistral` | Fallback model |
| `chatbot.session.ttl-minutes` | `60` | Session idle timeout |
| `chatbot.session.max-sessions` | `10000` | Max cached sessions |
| `chatbot.security.actuator.username` | `name` | Actuator basic-auth user |
| `chatbot.security.actuator.password` | `khasim` | Actuator basic-auth password |

### Override via environment variables

```bash
OLLAMA_BASE_URL=http://remote-host:11434 \
OLLAMA_MODEL=llama3.3 \
OLLAMA_FALLBACK_MODEL=gemma3 \
./gradlew bootRun
```

---

## 📡 API Reference

Base URL: `http://localhost:8081/chatbot/api/v1`

All responses follow the envelope format:

```json
{
  "success": true,
  "message": "Session created",
  "data": { ... },
  "timestamp": "2026-04-27T07:00:00Z"
}
```

---

### Sessions

#### `POST /api/v1/sessions` — Create session

```bash
curl -X POST http://localhost:8081/chatbot/api/v1/sessions \
  -H "Content-Type: application/json" \
  -d '{"identity": "alice@example.com"}'
```

**Request body**
```json
{ "identity": "alice@example.com" }
```

**Response `201 Created`**
```json
{
  "success": true,
  "message": "Session created",
  "data": {
    "sessionId": "550e8400-e29b-41d4-a716-446655440000",
    "identity": "alice@example.com",
    "active": true,
    "messageCount": 0,
    "createdAt": "2026-04-27T07:00:00Z",
    "lastActiveAt": "2026-04-27T07:00:00Z"
  }
}
```

---

#### `GET /api/v1/sessions/{sessionId}` — Get session

```bash
curl http://localhost:8081/chatbot/api/v1/sessions/{sessionId}
```

---

#### `GET /api/v1/sessions?identity=alice@example.com` — Find by identity

```bash
curl "http://localhost:8081/chatbot/api/v1/sessions?identity=alice@example.com"
```

---

#### `GET /api/v1/sessions/active` — List all active sessions

```bash
curl http://localhost:8081/chatbot/api/v1/sessions/active
```

---

#### `DELETE /api/v1/sessions/{sessionId}` — Deactivate session

```bash
curl -X DELETE http://localhost:8081/chatbot/api/v1/sessions/{sessionId}
```

---

### Chat

#### `POST /api/v1/sessions/{sessionId}/chat` — Send message

```bash
curl -X POST http://localhost:8081/chatbot/api/v1/sessions/{sessionId}/chat \
  -H "Content-Type: application/json" \
  -d '{"content": "What are virtual threads?"}'
```

**Request body**
```json
{ "content": "What are virtual threads?" }
```

**Response `200 OK`**
```json
{
  "success": true,
  "message": "Message processed",
  "data": {
    "sessionId": "550e8400-...",
    "identity": "alice@example.com",
    "userMessage": "What are virtual threads?",
    "assistantMessage": "Virtual threads are lightweight JVM threads introduced in JDK 21 via JEP 444...",
    "model": "llama3.2",
    "timestamp": "2026-04-27T07:01:00Z",
    "totalMessages": 2
  }
}
```

> If `llama3.2` is unavailable, the request automatically retries against `mistral`. If both Ollama models are down, it falls back to LangChain4j / Groq cloud (set `GROQ_API_KEY`). The `provider` field in the response tells you which tier answered.

---

#### `GET /api/v1/sessions/{sessionId}/history` — Conversation history

```bash
curl http://localhost:8081/chatbot/api/v1/sessions/{sessionId}/history
```

**Response `200 OK`**
```json
{
  "success": true,
  "message": "History retrieved (2 messages)",
  "data": [
    { "role": "USER",      "content": "What are virtual threads?", "timestamp": "..." },
    { "role": "ASSISTANT", "content": "Virtual threads are...",    "timestamp": "..." }
  ]
}
```

---

### Error responses

| HTTP | Code | Meaning |
|------|------|---------|
| 400 | `VALIDATION_ERROR` | Invalid request body |
| 404 | `SESSION_NOT_FOUND` | Unknown or deactivated session ID |
| 503 | `OLLAMA_UNAVAILABLE` | Ollama not running — run `ollama serve` |
| 503 | `MODEL_NOT_FOUND` | Model not pulled — run `ollama pull <model>` |
| 503 | `AI_CALL_FAILED` | Both models failed |

---

## 🤖 AI Models & Fallback

### Tier 1 — Spring AI primary: `llama3.2` (Ollama local)

- **Size**: ~2 GB · **Speed**: Fast · **Cost**: Free
- `ollama pull llama3.2`

### Tier 2 — Spring AI fallback: `mistral` (Ollama local)

- **Size**: ~4.4 GB · **Speed**: Slightly slower, stronger reasoning · **Cost**: Free
- `ollama pull mistral`
- Activates automatically when llama3.2 fails

### Tier 3 — LangChain4j cloud: `llama-3.3-70b-versatile` (Groq)

- **Size**: cloud, no local storage · **Speed**: ~200 tokens/sec · **Cost**: Free (30 RPM)
- Requires `GROQ_API_KEY` env variable
- Get a free key at https://console.groq.com
- Activates only when **both** Ollama models are unavailable

### Switching models

```bash
OLLAMA_MODEL=llama3.3            ./gradlew bootRun   # change primary
OLLAMA_FALLBACK_MODEL=gemma3     ./gradlew bootRun   # change fallback
GROQ_MODEL=mixtral-8x7b-32768    ./gradlew bootRun   # change cloud model
GROQ_API_KEY=gsk_... ./gradlew bootRun               # enable cloud fallback
```

---

## 🔐 Security

### API endpoints — public (no auth required)

All `/api/v1/**` endpoints are unauthenticated. No token or header is needed.

### Swagger UI — public

- `http://localhost:8081/chatbot/swagger-ui.html`
- `http://localhost:8081/chatbot/v3/api-docs`

### Actuator — mixed

| Endpoint | Auth required |
|----------|--------------|
| `/actuator/health` | ❌ Public |
| `/actuator/info` | ❌ Public |
| `/actuator/metrics` | ❌ Public |
| `/actuator/prometheus` | ❌ Public |
| `/actuator/env` | ✅ Basic Auth (ACTUATOR role) |
| `/actuator/heapdump` | ✅ Basic Auth (ACTUATOR role) |
| `/actuator/threaddump` | ✅ Basic Auth (ACTUATOR role) |
| `/actuator/loggers` | ✅ Basic Auth (ACTUATOR role) |

**Default credentials** (override via env in production):
```yaml
chatbot.security.actuator.username: name
chatbot.security.actuator.password: khasim
```

### Security headers applied to all responses

- `X-Content-Type-Options: nosniff`
- `X-Frame-Options: DENY`
- `Strict-Transport-Security`
- `Content-Security-Policy`
- `Referrer-Policy: strict-origin-when-cross-origin`

---

## 📊 Observability

### Health check

```bash
curl http://localhost:8081/chatbot/actuator/health
```

### Prometheus metrics

```bash
curl http://localhost:8081/chatbot/actuator/prometheus
```

### Application info

```bash
curl http://localhost:8081/chatbot/actuator/info
```

### Log file

```
logs/chatbot.log
```

Log format: `yyyy-MM-dd HH:mm:ss.SSS [thread] LEVEL logger - message`

---

## 🧪 Running Tests

```bash
# All tests
./gradlew test

# Specific test class
./gradlew test --tests "org.llm.com.service.ChatServiceTest"

# With detailed output
./gradlew test --info
```

### Test coverage

| Class | Type | What it tests |
|-------|------|---------------|
| `ChatbotApplicationTest` | `@SpringBootTest` | Context loads without errors |
| `ChatServiceTest` | Unit (Mockito) | Primary/fallback routing, history |
| `SessionServiceTest` | Unit (Mockito) | Session CRUD, deactivation |
| `ChatControllerTest` | `@WebMvcTest` | HTTP layer, validation, 404 handling |

> No real Ollama calls are made in tests — `ChatClient` beans are mocked by name.

---

## 🏛 Design Decisions

### Constructor injection (no `@Autowired` / `@RequiredArgsConstructor`)

All Spring beans use explicit constructor injection. This makes dependencies visible, immutable, and trivially testable without a Spring context.

### Caffeine for session storage

Sessions are stored in a Caffeine `Cache<String, ChatSession>` with:
- **TTL**: `expireAfterAccess(60 min)` — resets on every chat call
- **Max size**: 10,000 sessions (LRU eviction)
- **No scheduled job** needed — Caffeine handles expiry internally

Replace with Redis for distributed deployments:
```groovy
implementation "org.springframework.boot:spring-boot-starter-data-redis"
```

### Two explicit `ChatClient` beans (no auto-config)

`SpringAiConfig` and `LlamaConfig` both build their `OllamaChatModel` directly from `OllamaApi`, bypassing Spring AI's `ChatClientAutoConfiguration`. This:
- Eliminates any bean name collision
- Removes the need for `allow-bean-definition-overriding: true`
- Makes the wiring explicit and auditable

### `@Primary` + `@Qualifier`

`SpringAiConfig` marks `chatClient` as `@Primary`. `ChatServiceImpl` injects both clients by `@Qualifier`:

```java
public ChatServiceImpl(
    SessionService sessionService,
    @Qualifier("chatClient")      ChatClient primaryClient,   // llama3.2
    @Qualifier("llamaChatClient") ChatClient fallbackClient,  // mistral
    ...
```

### JEP 491 — No `ReentrantLock` migrations

Prior to JDK 24, virtual threads pinned to their carrier thread inside `synchronized` blocks. JEP 491 (JDK 24+, included in Corretto 25) decouples monitors from carrier threads — all legacy `synchronized` code scales automatically. No `ReentrantLock` is used in this codebase; `synchronized` would work just as well.

---

## 🌍 Environment Variables

| Variable | Default | Description |
|----------|---------|-------------|
| `OLLAMA_BASE_URL` | `http://localhost:11434` | Ollama server URL |
| `OLLAMA_MODEL` | `llama3.2` | Primary model name (Tier 1) |
| `OLLAMA_FALLBACK_MODEL` | `mistral` | Fallback model name (Tier 2) |
| `GROQ_API_KEY` | *(unset)* | Groq API key for Tier 3 cloud fallback — get free at console.groq.com |
| `GROQ_MODEL` | `llama-3.3-70b-versatile` | Groq model name (Tier 3) |
| `ACTUATOR_PASSWORD` | *(see yml)* | Password for sensitive actuator endpoints |
| `PROFILE` | `local` | Spring active profile |

---

## 📦 Building a JAR

```bash
./gradlew bootJar
java -jar build/libs/chatbot-1.0-SNAPSHOT.jar
```

---

*Built with ☕ Spring Boot · 🦙 Ollama · ☁️ Virtual Threads*

