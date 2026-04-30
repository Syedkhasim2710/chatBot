package org.llm.com.config;

import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.ollama.OllamaEmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.inmemory.InMemoryEmbeddingStore;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

/**
 * RAG (Retrieval-Augmented Generation) infrastructure using LangChain4j.
 *
 * <p>Only created when {@code chatbot.rag.enabled=true} (the default).
 * Set {@code chatbot.rag.enabled=false} in {@code application-test.yml} to
 * skip embedding calls in unit/integration tests.
 *
 * <h3>Java RAG Toolchain (Java equivalents of Python tools)</h3>
 * <table border="1">
 *   <tr><th>Stage</th><th>Python / Cloud Tool</th><th>Java / This Project</th></tr>
 *   <tr><td>Orchestration</td><td>LangChain</td><td>LangChain4j 0.36.2</td></tr>
 *   <tr><td>Retrieval</td><td>LlamaIndex VectorStoreIndex</td><td>LangChain4j EmbeddingStoreRetriever</td></tr>
 *   <tr><td>Embedding</td><td>OpenAI text-embedding-3-small</td><td>OllamaEmbeddingModel (nomic-embed-text)</td></tr>
 *   <tr><td>HF Transformers</td><td>sentence-transformers/all-mpnet</td><td>Ollama (any GGUF model)</td></tr>
 *   <tr><td>Ingestion</td><td>Unstructured.io</td><td>Apache Tika 2.9.2 (DocumentParserService)</td></tr>
 *   <tr><td>Chunking</td><td>LangChain RecursiveTextSplitter</td><td>LangChain4j DocumentSplitters.recursive()</td></tr>
 *   <tr><td>Vector store</td><td>Pinecone / Chroma / Weaviate / Qdrant</td><td>InMemoryEmbeddingStore (dev) / swap easily</td></tr>
 *   <tr><td>Web search</td><td>Tavily / SerpAPI</td><td>Tavily REST API (WebSearchService)</td></tr>
 *   <tr><td>Evaluation</td><td>Ragas</td><td>LLM-as-judge (planned)</td></tr>
 * </table>
 *
 * <h3>One-time Ollama setup</h3>
 * <pre>
 * ollama pull nomic-embed-text   # ~274 MB, fastest embed model
 * </pre>
 */
@Slf4j
@Configuration
@ConditionalOnProperty(name = "chatbot.rag.enabled", havingValue = "true", matchIfMissing = true)
public class VectorStoreConfig {

    /**
     * LangChain4j in-memory vector store (thread-safe, cosine similarity).
     * For production, replace with Chroma / Qdrant / Pgvector — same interface.
     */
    @Bean("lc4jEmbeddingStore")
    public EmbeddingStore<dev.langchain4j.data.segment.TextSegment> embeddingStore() {
        log.info("RAG: Initialising InMemoryEmbeddingStore (LangChain4j)");
        return new InMemoryEmbeddingStore<>();
    }

    /**
     * OllamaEmbeddingModel — free, local, no API key required.
     * Java equivalent of OpenAI {@code text-embedding-3-small}.
     * Requires {@code ollama pull nomic-embed-text} run once.
     */
    @Bean("lc4jEmbeddingModel")
    public EmbeddingModel embeddingModel(
            @Value("${spring.ai.ollama.base-url:http://localhost:11434}") String baseUrl,
            @Value("${spring.ai.ollama.embedding.model:nomic-embed-text}") String model) {
        log.info("RAG: Configuring OllamaEmbeddingModel baseUrl=[{}] model=[{}]", baseUrl, model);
        return OllamaEmbeddingModel.builder()
                .baseUrl(baseUrl)
                .modelName(model)
                .timeout(Duration.ofSeconds(60))
                .build();
    }
}

