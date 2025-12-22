package com.sentrius.sag.model;

public class ErrorStatement implements Statement {
    private final String errorCode;
    private final String message;

    public ErrorStatement(String errorCode, String message) {
        this.errorCode = errorCode;
        this.message = message;
    }

    public String getErrorCode() {
        return errorCode;
    }

    public String getMessage() {
        return message;
    }

    @Override
    public String toString() {
        return "ErrorStatement{errorCode='" + errorCode + "', message='" + message + "'}";
    }
}
