package io.sentrius.sso.mcp.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.util.Map;

/**
 * Represents an MCP (Model Context Protocol) request message
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class MCPRequest {
    
    @JsonProperty("jsonrpc")
    private String jsonRpc = "2.0";
    
    @JsonProperty("id")
    private String id;
    
    @JsonProperty("method")
    private String method;
    
    @JsonProperty("params")
    private Map<String, Object> params;
    
    /**
     * Create a new MCP request
     */
    public static MCPRequest create(String id, String method, Map<String, Object> params) {
        return new MCPRequest("2.0", id, method, params);
    }
}