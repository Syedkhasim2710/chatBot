package org.llm.com.service;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Real-time web search via the <a href="https://tavily.com">Tavily Search API</a>
 * (purpose-built for AI agents; 1 000 free searches/month).
 *
 * <h3>When is search triggered?</h3>
 * <p>The service auto-detects queries that likely require live data using a
 * keyword regex (news, current, latest, stock price, today, tomorrow, …).
 * It is <em>never</em> called for normal factual or historical questions.
 *
 * <h3>Java RAG ecosystem note (Java equivalent of Python tools)</h3>
 * <ul>
 *   <li>Tavily is the standard web-search tool for LLM agents; this service is
 *       the Java equivalent of the {@code TavilySearchResults} LangChain tool.</li>
 *   <li>SerpAPI / DuckDuckGo are alternatives — swap the API call; same interface.</li>
 *   <li>Set {@code TAVILY_API_KEY} env var (free tier: <a href="https://app.tavily.com">app.tavily.com</a>).</li>
 * </ul>
 *
 * <h3>Architecture</h3>
 * <pre>
 * User message
 *   │
 *   ├── detectsRealTimeQuery()  →  YES
 *   │     └── POST https://api.tavily.com/search
 *   │           └── results formatted as [WEB SEARCH RESULTS]
 *   └── NO → skip (non-real-time query)
 * </pre>
 */
@Slf4j
@Service
public class WebSearchService {

    // Triggered on real-time / current-event patterns
    private static final Pattern REALTIME_PATTERN = Pattern.compile(
            "(?i)\\b(current|latest|today|now|recent|breaking|live|news|" +
            "stock price|market|weather forecast|tomorrow|this week|this month|" +
            "right now|as of|in 2025|in 2026|just announced|update|trending|" +
            "new release|launched|cryptocurrency|bitcoin|exchange rate|score)\\b"
    );

    private static final String TAVILY_API_URL = "https://api.tavily.com";

    @Value("${tavily.api-key:}")
    private String apiKey;

    @Value("${tavily.max-results:5}")
    private int maxResults;

    @Value("${tavily.enabled:true}")
    private boolean enabled;

    private final RestClient restClient;

    public WebSearchService() {
        this.restClient = RestClient.builder()
                .baseUrl(TAVILY_API_URL)
                .defaultHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                .build();
    }

    /**
     * Performs a web search if the query contains real-time keywords AND a
     * Tavily API key is configured.
     *
     * @param query the user's message
     * @return formatted search results block, or empty if search not triggered / failed
     */
    public Optional<String> searchIfRealTime(String query) {
        if (!enabled) return Optional.empty();
        if (apiKey == null || apiKey.isBlank()) {
            log.debug("WebSearch: TAVILY_API_KEY not set — skipping search");
            return Optional.empty();
        }
        if (!isRealTimeQuery(query)) {
            log.debug("WebSearch: query does not match real-time pattern — skipping");
            return Optional.empty();
        }

        log.info("WebSearch: real-time query detected — searching Tavily for: [{}]",
                query.length() > 80 ? query.substring(0, 80) + "..." : query);
        try {
            TavilyResponse response = restClient.post()
                    .uri("/search")
                    .body(Map.of(
                            "api_key",      apiKey,
                            "query",        query,
                            "max_results",  maxResults,
                            "search_depth", "basic",
                            "include_answer", true
                    ))
                    .retrieve()
                    .body(TavilyResponse.class);

            if (response == null || response.results() == null || response.results().isEmpty()) {
                log.warn("WebSearch: Tavily returned no results for query");
                return Optional.empty();
            }

            String formatted = formatResults(response);
            log.info("WebSearch: {} results returned ({} chars)", response.results().size(), formatted.length());
            return Optional.of(formatted);

        } catch (Exception ex) {
            log.warn("WebSearch: Tavily request failed — {}", ex.getMessage());
            return Optional.empty();
        }
    }

    // ── Package-private for testing ───────────────────────────────────────────

    boolean isRealTimeQuery(String query) {
        return REALTIME_PATTERN.matcher(query).find();
    }

    // ── Internal ──────────────────────────────────────────────────────────────

    private String formatResults(TavilyResponse response) {
        StringBuilder sb = new StringBuilder();

        // If Tavily provides a direct answer, include it first
        if (response.answer() != null && !response.answer().isBlank()) {
            sb.append("Direct Answer: ").append(response.answer()).append("\n\n");
        }

        sb.append("Sources:\n");
        for (int i = 0; i < response.results().size(); i++) {
            TavilyResult r = response.results().get(i);
            sb.append(i + 1).append(". ").append(r.title()).append("\n");
            sb.append("   URL: ").append(r.url()).append("\n");
            if (r.content() != null && !r.content().isBlank()) {
                String snippet = r.content().length() > 300
                        ? r.content().substring(0, 300) + "..."
                        : r.content();
                sb.append("   ").append(snippet).append("\n");
            }
            sb.append("\n");
        }
        return sb.toString().trim();
    }

    // ── Response POJOs ────────────────────────────────────────────────────────

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record TavilyResponse(
            @JsonProperty("query")   String query,
            @JsonProperty("answer")  String answer,
            @JsonProperty("results") List<TavilyResult> results) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record TavilyResult(
            @JsonProperty("title")   String title,
            @JsonProperty("url")     String url,
            @JsonProperty("content") String content,
            @JsonProperty("score")   double score) {}
}

