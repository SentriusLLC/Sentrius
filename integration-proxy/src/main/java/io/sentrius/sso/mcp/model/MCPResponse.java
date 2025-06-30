package io.sentrius.sso.mcp.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

/**
 * Represents an MCP (Model Context Protocol) response message
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class MCPResponse {
    
    @JsonProperty("jsonrpc")
    private String jsonRpc = "2.0";
    
    @JsonProperty("id")
    private String id;
    
    @JsonProperty("result")
    private Object result;
    
    @JsonProperty("error")
    private MCPError error;
    
    /**
     * Create a successful MCP response
     */
    public static MCPResponse success(String id, Object result) {
        return new MCPResponse("2.0", id, result, null);
    }
    
    /**
     * Create an error MCP response
     */
    public static MCPResponse error(String id, MCPError error) {
        return new MCPResponse("2.0", id, null, error);
    }
}