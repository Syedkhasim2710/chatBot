package org.llm.com.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Thrown when a requested session does not exist or has been deleted.
 */
@ResponseStatus(HttpStatus.NOT_FOUND)
public class SessionNotFoundException extends ChatbotException {

    public SessionNotFoundException(String sessionId) {
        super("SESSION_NOT_FOUND",
              String.format("Chat session [%s] not found or has been deleted.", sessionId));
    }
}

