package org.llm.com.service;

import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.DocumentSplitter;
import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.document.splitter.DocumentSplitters;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.output.Response;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import dev.langchain4j.store.embedding.EmbeddingStore;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * RAG (Retrieval-Augmented Generation) service — full pipeline using LangChain4j.
 *
 * <h3>RAG Pipeline Stages</h3>
 * <ol>
 *   <li><b>Ingestion</b> — Document text (from DocumentParserService / Apache Tika)
 *       is loaded and tagged with {@code sessionId} metadata.</li>
 *   <li><b>Chunking</b> — {@code DocumentSplitters.recursive(chunkSize, overlap)}
 *       splits text into overlapping segments. Java equivalent of LangChain's
 *       {@code RecursiveCharacterTextSplitter}.</li>
 *   <li><b>Embedding</b> — Each segment is converted to a dense float vector using
 *       {@code OllamaEmbeddingModel} (free local model {@code nomic-embed-text}).
 *       Java equivalent of OpenAI {@code text-embedding-3-small}.</li>
 *   <li><b>Storage</b> — Vectors stored in {@code InMemoryEmbeddingStore}
 *       (LangChain4j). Java equivalent of Chroma / Pinecone (swap with
 *       {@code ChromaEmbeddingStore}, {@code QdrantEmbeddingStore}, etc.).</li>
 *   <li><b>Retrieval</b> — Query embedded, cosine similarity search returns
 *       top-k segments filtered by {@code sessionId}. Java equivalent of
 *       LlamaIndex {@code VectorStoreRetriever}.</li>
 * </ol>
 *
 * <h3>Graceful degradation</h3>
 * <p>If {@code ollama pull nomic-embed-text} has not been run, the embedding
 * call fails — {@code RagService} catches it, logs a warning, and returns
 * {@code Optional.empty()}.  The chat request continues without RAG context.
 *
 * <p>This service is only created when {@code chatbot.rag.enabled=true} (default)
 * AND the {@code lc4jEmbeddingStore} and {@code lc4jEmbeddingModel} beans exist.
 */
@Slf4j
@Service
@ConditionalOnProperty(name = "chatbot.rag.enabled", havingValue = "true", matchIfMissing = true)
@ConditionalOnBean(name = {"lc4jEmbeddingStore", "lc4jEmbeddingModel"})
public class RagService {

    private static final int CHUNK_SIZE       = 512;  // chars per chunk
    private static final int CHUNK_OVERLAP    = 64;   // char overlap between chunks
    private static final int MAX_CONTEXT_CHARS = 4000;

    private final EmbeddingStore<TextSegment> embeddingStore;
    private final EmbeddingModel              embeddingModel;
    private final DocumentSplitter            splitter;

    @Value("${chatbot.rag.top-k:4}")
    private int topK;

    public RagService(
            @Qualifier("lc4jEmbeddingStore") EmbeddingStore<TextSegment> embeddingStore,
            @Qualifier("lc4jEmbeddingModel")  EmbeddingModel embeddingModel) {
        this.embeddingStore = embeddingStore;
        this.embeddingModel = embeddingModel;
        this.splitter       = DocumentSplitters.recursive(CHUNK_SIZE, CHUNK_OVERLAP);
    }

    /**
     * Chunks, embeds, and stores document text for the given session.
     * Future queries from this session can retrieve semantically relevant chunks.
     *
     * @param sessionId   session that owns this document
     * @param sourceTitle file name or URL (stored as metadata)
     * @param text        full extracted document text
     */
    public void ingestDocument(String sessionId, String sourceTitle, String text) {
        if (text == null || text.isBlank()) return;
        try {
            Metadata metadata = Metadata.metadata("sessionId", sessionId);
            metadata.put("sourceTitle", sourceTitle);
            Document doc = Document.from(text, metadata);
            List<TextSegment> segments = splitter.split(doc);

            Response<List<Embedding>> embResponse = embeddingModel.embedAll(segments);
            List<Embedding> embeddings = embResponse.content();

            embeddingStore.addAll(embeddings, segments);
            log.info("RAG ingest OK: session=[{}] source=[{}] segments=[{}]",
                    sessionId, sourceTitle, segments.size());
        } catch (Exception ex) {
            log.warn("RAG ingest failed (ollama pull nomic-embed-text?): session=[{}] err={}",
                    sessionId, ex.getMessage());
        }
    }

    /**
     * Retrieves the top-k semantically similar document chunks for the query,
     * filtered to the specified session.
     *
     * @param sessionId session to search within
     * @param query     the user's question
     * @return formatted context block, or empty if no results / embedding fails
     */
    public Optional<String> retrieve(String sessionId, String query) {
        try {
            Response<Embedding> qEmbResp = embeddingModel.embed(query);
            Embedding queryEmbedding = qEmbResp.content();

            List<EmbeddingMatch<TextSegment>> matches =
                    embeddingStore.findRelevant(queryEmbedding, topK > 0 ? topK : 4);

            // Filter by sessionId
            List<EmbeddingMatch<TextSegment>> sessionMatches = matches.stream()
                    .filter(m -> sessionId.equals(m.embedded().metadata().getString("sessionId")))
                    .toList();

            if (sessionMatches.isEmpty()) {
                log.debug("RAG retrieve: no matches for session=[{}]", sessionId);
                return Optional.empty();
            }

            String context = sessionMatches.stream()
                    .map(m -> m.embedded().text())
                    .collect(Collectors.joining("\n---\n"));

            if (context.length() > MAX_CONTEXT_CHARS) {
                context = context.substring(0, MAX_CONTEXT_CHARS) + "\n... [RAG context truncated]";
            }

            log.info("RAG retrieve OK: session=[{}] matches=[{}] chars=[{}]",
                    sessionId, sessionMatches.size(), context.length());
            return Optional.of(context);

        } catch (Exception ex) {
            log.warn("RAG retrieve failed: session=[{}] err={}", sessionId, ex.getMessage());
            return Optional.empty();
        }
    }
}

