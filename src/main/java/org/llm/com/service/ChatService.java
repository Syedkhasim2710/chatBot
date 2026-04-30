package org.llm.com.service;

import org.llm.com.dto.request.SendMessageRequest;
import org.llm.com.dto.response.ChatResponse;
import org.llm.com.model.ChatMessage;

import java.util.List;

/**
 * Contract for AI-backed chat operations within a session.
 *
 * <p>Decoupled from the underlying AI provider — callers work with this
 * interface; the implementation selects the appropriate tier automatically
 * (Spring AI → Ollama Tier 1/2, or LangChain4j → Groq Tier 3).
 *
 * <h3>Context enrichment</h3>
 * <p>Callers may supply an {@code additionalContext} string that is prepended
 * to the prompt before the AI call.  This is used for:
 * <ul>
 *   <li>Extracted document text (PDF / DOC / DOCX upload)</li>
 *   <li>Fetched web-page content (URL pasted in message)</li>
 * </ul>
 * URL detection and weather injection also happen automatically inside the
 * implementation regardless of whether additional context is supplied.
 */
public interface ChatService {

    /**
     * Process a user message with optional extra context and return the AI response.
     *
     * @param sessionId         target session
     * @param request           user message
     * @param additionalContext pre-built context block (e.g. document text, web content),
     *                          or {@code null} if none
     * @return full chat exchange response
     */
    ChatResponse sendMessage(String sessionId, SendMessageRequest request, String additionalContext);

    /**
     * Process a user message without caller-supplied context.
     * URL detection and weather injection still happen automatically.
     *
     * @param sessionId target session
     * @param request   user message
     * @return full chat exchange response
     */
    default ChatResponse sendMessage(String sessionId, SendMessageRequest request) {
        return sendMessage(sessionId, request, null);
    }

    /**
     * Return the full ordered message history for the session.
     *
     * @param sessionId target session
     */
    List<ChatMessage> getHistory(String sessionId);
}
