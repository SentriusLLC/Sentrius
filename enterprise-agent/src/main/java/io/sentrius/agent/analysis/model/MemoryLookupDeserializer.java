package io.sentrius.agent.analysis.model;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonNode;
import java.io.IOException;

/**
 * Custom deserializer for memoryLookup field that handles both legacy String format
 * and new structured MemoryLookupRequest object format.
 * 
 * Legacy format: "memoryLookup": "user name"
 * New format: "memoryLookup": {"query": "user name", "agentId": "my-agent", "markings": "PUBLIC", "limit": 10}
 */
public class MemoryLookupDeserializer extends JsonDeserializer<Object> {
    
    @Override
    public Object deserialize(JsonParser parser, DeserializationContext context) throws IOException {
        JsonNode node = parser.getCodec().readTree(parser);
        
        if (node.isTextual()) {
            // Legacy string format - return as string
            return node.asText();
        } else if (node.isObject()) {
            // New structured format - deserialize as MemoryLookupRequest
            return parser.getCodec().treeToValue(node, MemoryLookupRequest.class);
        } else if (node.isNull()) {
            return null;
        }
        
        // Fallback for unexpected types
        return node.asText();
    }
}
