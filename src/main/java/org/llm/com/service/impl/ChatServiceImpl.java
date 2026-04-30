package org.llm.com.service.impl;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatLanguageModel;
import lombok.extern.slf4j.Slf4j;
import org.llm.com.dto.request.SendMessageRequest;
import org.llm.com.dto.response.ChatResponse;
import org.llm.com.exception.ChatbotException;
import org.llm.com.model.ChatSession;
import org.llm.com.model.MessageRole;
import org.llm.com.service.ChatService;
import org.llm.com.service.RagService;
import org.llm.com.service.SessionService;
import org.llm.com.service.UrlFetchService;
import org.llm.com.service.WeatherService;
import org.llm.com.service.WebSearchService;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * 3-tier AI chat service with automatic provider failover and full RAG context enrichment.
 *
 * <p>Tier 1: Spring AI  llama3.2 (Ollama local)   free, offline, fastest
 * <p>Tier 2: Spring AI  mistral  (Ollama local)   free, offline
 * <p>Tier 3: LangChain4j Groq cloud (free tier)   independent infra
 *
 * <h3>Context enrichment pipeline (before every AI call)</h3>
 * <ol>
 *   <li>Weather data from wttr.in (free, no key)</li>
 *   <li>URL page content via Jsoup (SSRF-protected)</li>
 *   <li>Real-time web search via Tavily (when real-time keywords detected)</li>
 *   <li>RAG retrieval from Spring AI SimpleVectorStore (session-scoped chunks)</li>
 *   <li>Caller-supplied document text (PDF/DOC from controller)</li>
 * </ol>
 *
 * <h3>Why Groq is not always called</h3>
 * <p>Groq (Tier 3) only activates when BOTH local Ollama models fail.
 * To force Tier 3: stop Ollama or set OLLAMA_BASE_URL to an unreachable host.
 */
@Slf4j
@Service
public class ChatServiceImpl implements ChatService {

    public static final String PROVIDER_SPRING_AI_PRIMARY  = "Spring AI / Ollama";
    public static final String PROVIDER_SPRING_AI_FALLBACK = "Spring AI / Ollama fallback";
    public static final String PROVIDER_LANGCHAIN4J_GROQ   = "LangChain4j / Groq";

    private final SessionService    sessionService;
    private final ChatClient        springAiPrimary;
    private final ChatClient        springAiFallback;
    private final ChatLanguageModel groqFallback;
    private final WeatherService    weatherService;
    private final UrlFetchService   urlFetchService;
    private final WebSearchService  webSearchService;
    private final String            primaryModel;
    private final String            fallbackModel;
    private final String            groqModel;
    private final String            systemPrompt;

    /** RagService is optional — not created when embedding model is unavailable. */
    @Autowired(required = false)
    private RagService ragService;

    public ChatServiceImpl(
            SessionService sessionService,
            @Qualifier("chatClient")      ChatClient springAiPrimary,
            @Qualifier("llamaChatClient") ChatClient springAiFallback,
            @Qualifier("groqChatModel")   ChatLanguageModel groqFallback,
            WeatherService weatherService,
            UrlFetchService urlFetchService,
            WebSearchService webSearchService,
            @Value("${spring.ai.ollama.chat.options.model:llama3.2}")  String primaryModel,
            @Value("${ollama.fallback.model:mistral}")                 String fallbackModel,
            @Value("${groq.model:llama-3.3-70b-versatile}")           String groqModel,
            @Value("${chatbot.ai.system-prompt}")                      String systemPrompt) {
        this.sessionService    = sessionService;
        this.springAiPrimary   = springAiPrimary;
        this.springAiFallback  = springAiFallback;
        this.groqFallback      = groqFallback;
        this.weatherService    = weatherService;
        this.urlFetchService   = urlFetchService;
        this.webSearchService  = webSearchService;
        this.primaryModel      = primaryModel;
        this.fallbackModel     = fallbackModel;
        this.groqModel         = groqModel;
        this.systemPrompt      = systemPrompt;
    }

    @Override
    public ChatResponse sendMessage(String sessionId, SendMessageRequest request, String additionalContext) {
        ChatSession session = sessionService.getSession(sessionId);
        log.info("Chat | session=[{}] identity=[{}]", sessionId, session.getIdentity());
        String enrichedMessage = enrichWithContext(sessionId, request.getContent(), additionalContext);
        session.addMessage(org.llm.com.model.ChatMessage.builder()
                .role(MessageRole.USER).content(request.getContent()).build());
        AiResult result = callWithFallback(session, enrichedMessage);
        session.addMessage(org.llm.com.model.ChatMessage.builder()
                .role(MessageRole.ASSISTANT).content(result.reply()).build());
        log.info("Chat | session=[{}] provider=[{}] model=[{}]",
                sessionId, result.provider(), result.model());
        return ChatResponse.builder()
                .sessionId(sessionId)
                .identity(session.getIdentity())
                .userMessage(request.getContent())
                .assistantMessage(result.reply())
                .model(result.model())
                .provider(result.provider())
                .timestamp(Instant.now())
                .totalMessages(session.getMessageCount())
                .build();
    }

    @Override
    public List<org.llm.com.model.ChatMessage> getHistory(String sessionId) {
        return List.copyOf(sessionService.getSession(sessionId).getMessages());
    }

    // Context enrichment pipeline

    /**
     * Builds an enriched prompt by prepending all available context:
     * weather, URL pages, real-time web search, RAG chunks, document text.
     * The original question is always placed last as [USER QUESTION].
     */
    private String enrichWithContext(String sessionId, String userMessage, String additionalContext) {
        List<String> parts = new ArrayList<>();

        // 1. Live weather data (wttr.in)
        weatherService.fetchIfWeatherQuery(userMessage)
                .ifPresent(weather -> parts.add("[LIVE WEATHER DATA]\n" + weather));

        // 2. URL content from message
        urlFetchService.extractUrls(userMessage).forEach(url ->
                urlFetchService.fetchContent(url).ifPresent(pageText ->
                        parts.add("[WEB PAGE CONTENT FROM: " + url + "]\n" + pageText)));

        // 3. Real-time web search (Tavily) — triggered by real-time keywords
        webSearchService.searchIfRealTime(userMessage)
                .ifPresent(results -> parts.add("[WEB SEARCH RESULTS]\n" + results));

        // 4. RAG retrieval — semantically similar chunks from ingested docs
        if (ragService != null) {
            ragService.retrieve(sessionId, userMessage)
                    .ifPresent(ragCtx -> parts.add("[RELEVANT DOCUMENT CONTEXT (RAG)]\n" + ragCtx));
        }

        // 5. Caller-supplied additional context (full document text from upload)
        if (additionalContext != null && !additionalContext.isBlank()) {
            parts.add(additionalContext);
        }

        if (parts.isEmpty()) return userMessage;

        parts.add("[USER QUESTION]\n" + userMessage);
        String enriched = String.join("\n\n", parts);
        log.debug("Enriched prompt: contextBlocks=[{}] totalChars=[{}]", parts.size() - 1, enriched.length());
        return enriched;
    }

    // 3-tier fallback

    private AiResult callWithFallback(ChatSession session, String enrichedMessage) {
        String id = session.getSessionId();
        try {
            return new AiResult(callSpringAi(springAiPrimary, id, enrichedMessage),
                    primaryModel, PROVIDER_SPRING_AI_PRIMARY);
        } catch (Exception ex) { log.warn("[Tier 1] {} failed: {}", primaryModel, rootCause(ex)); }
        try {
            return new AiResult(callSpringAi(springAiFallback, "fallback-" + id, enrichedMessage),
                    fallbackModel, PROVIDER_SPRING_AI_FALLBACK);
        } catch (Exception ex) { log.warn("[Tier 2] {} failed: {}", fallbackModel, rootCause(ex)); }
        log.warn("[Tier 3] Both Ollama tiers down -- escalating to LangChain4j/Groq session=[{}]", id);
        try {
            return new AiResult(callGroq(session, enrichedMessage), groqModel, PROVIDER_LANGCHAIN4J_GROQ);
        } catch (Exception ex) {
            log.error("[Tier 3] Groq also failed: {}", rootCause(ex));
            throw new ChatbotException("AI_CALL_FAILED",
                    "All AI providers are unavailable. Start Ollama or set GROQ_API_KEY.");
        }
    }

    // Provider callers

    private String callSpringAi(ChatClient client, String conversationId, String msg) {
        String content = client.prompt().user(msg)
                .advisors(a -> a.param(ChatMemory.CONVERSATION_ID, conversationId))
                .call().content();
        if (content == null || content.isBlank())
            throw new ChatbotException("EMPTY_RESPONSE", "Spring AI returned empty response.");
        return content;
    }

    private String callGroq(ChatSession session, String enrichedMessage) {
        List<ChatMessage> messages = new ArrayList<>();
        messages.add(SystemMessage.from(systemPrompt));
        for (org.llm.com.model.ChatMessage m : session.getMessages()) {
            messages.add(m.getRole() == MessageRole.USER
                    ? UserMessage.from(m.getContent()) : AiMessage.from(m.getContent()));
        }
        if (!messages.isEmpty() && messages.get(messages.size() - 1) instanceof UserMessage)
            messages.set(messages.size() - 1, UserMessage.from(enrichedMessage));
        String reply = groqFallback.generate(messages).content().text();
        if (reply == null || reply.isBlank())
            throw new ChatbotException("EMPTY_RESPONSE", "Groq returned empty response.");
        return reply;
    }

    private static String rootCause(Exception ex) {
        Throwable t = ex;
        while (t.getCause() != null) t = t.getCause();
        return t.getClass().getSimpleName() + ": " + t.getMessage();
    }

    private record AiResult(String reply, String model, String provider) {}
}
