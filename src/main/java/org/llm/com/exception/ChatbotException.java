package org.llm.com.exception;
import lombok.Getter;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Base exception for all chatbot-specific failures.
 */
@ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
@Getter
public class ChatbotException extends RuntimeException {

    private final String errorCode;

    public ChatbotException(String message) {
        super(message);
        this.errorCode = "CHATBOT_ERROR";
    }

    public ChatbotException(String errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public ChatbotException(String message, Throwable cause) {
        super(message, cause);
        this.errorCode = "CHATBOT_ERROR";
    }

    public String getErrorCode() {
        return errorCode;
    }
}

