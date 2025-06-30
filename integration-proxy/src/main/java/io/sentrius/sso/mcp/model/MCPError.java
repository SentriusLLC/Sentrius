package io.sentrius.sso.mcp.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

/**
 * Represents an MCP error
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class MCPError {
    
    @JsonProperty("code")
    private int code;
    
    @JsonProperty("message")
    private String message;
    
    @JsonProperty("data")
    private Object data;
    
    // Standard MCP error codes
    public static final int PARSE_ERROR = -32700;
    public static final int INVALID_REQUEST = -32600;
    public static final int METHOD_NOT_FOUND = -32601;
    public static final int INVALID_PARAMS = -32602;
    public static final int INTERNAL_ERROR = -32603;
    public static final int UNAUTHORIZED = -32001;
    public static final int FORBIDDEN = -32002;
    
    public static MCPError parseError(String message) {
        return new MCPError(PARSE_ERROR, message, null);
    }
    
    public static MCPError invalidRequest(String message) {
        return new MCPError(INVALID_REQUEST, message, null);
    }
    
    public static MCPError methodNotFound(String method) {
        return new MCPError(METHOD_NOT_FOUND, "Method not found: " + method, null);
    }
    
    public static MCPError invalidParams(String message) {
        return new MCPError(INVALID_PARAMS, message, null);
    }
    
    public static MCPError internalError(String message) {
        return new MCPError(INTERNAL_ERROR, message, null);
    }
    
    public static MCPError unauthorized(String message) {
        return new MCPError(UNAUTHORIZED, message != null ? message : "Unauthorized", null);
    }
    
    public static MCPError forbidden(String message) {
        return new MCPError(FORBIDDEN, message != null ? message : "Forbidden", null);
    }
}