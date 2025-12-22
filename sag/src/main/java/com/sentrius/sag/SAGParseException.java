package com.sentrius.sag;

public class SAGParseException extends Exception {
    public SAGParseException(String message) {
        super(message);
    }

    public SAGParseException(String message, Throwable cause) {
        super(message, cause);
    }
}
