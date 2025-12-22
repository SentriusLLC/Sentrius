package io.sentrius.sso.core.services.documents.retrieval;

/**
 * Exception thrown when document retrieval fails
 */
public class DocumentRetrievalException extends Exception {

    public DocumentRetrievalException(String message) {
        super(message);
    }

    public DocumentRetrievalException(String message, Throwable cause) {
        super(message, cause);
    }
}
