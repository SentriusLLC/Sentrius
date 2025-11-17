package io.sentrius.sentrius.analysis.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.fasterxml.jackson.core.JsonProcessingException;
import io.sentrius.agent.analysis.model.LLMResponse;
import io.sentrius.sso.core.utils.JsonUtil;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class LLMResponseTest {

    @Test
    void testLLMResponseWithMemoryLookup() {
        // Given
        Map<String, Object> args = new HashMap<>();
        args.put("arg1", "value1");
        
        // When
        LLMResponse response = LLMResponse.builder()
            .previousOperation("getPlan")
            .nextOperation("executeOperation")
            .memoryLookup("search for user preferences")
            .summaryForLLM("Summary of operations")
            .responseForUser("Task completed successfully")
            .arguments(args)
            .build();
        
        // Then
        assertEquals("getPlan", response.getPreviousOperation());
        assertEquals("executeOperation", response.getNextOperation());
        assertEquals("search for user preferences", response.getMemoryLookup());
        assertEquals("Summary of operations", response.getSummaryForLLM());
        assertEquals("Task completed successfully", response.getResponseForUser());
        assertNotNull(response.getArguments());
        assertEquals("value1", response.getArguments().get("arg1"));
    }

    @Test
    void testLLMResponseWithoutMemoryLookup() {
        // Given & When
        LLMResponse response = LLMResponse.builder()
            .previousOperation("getPlan")
            .nextOperation("executeOperation")
            .summaryForLLM("Summary of operations")
            .responseForUser("Task completed successfully")
            .build();
        
        // Then
        assertEquals("getPlan", response.getPreviousOperation());
        assertEquals("executeOperation", response.getNextOperation());
        assertNull(response.getMemoryLookup());
        assertEquals("Summary of operations", response.getSummaryForLLM());
        assertEquals("Task completed successfully", response.getResponseForUser());
    }

    @Test
    void testLLMResponseJsonSerialization() throws JsonProcessingException {
        // Given
        Map<String, Object> args = new HashMap<>();
        args.put("query", "test query");
        
        LLMResponse response = LLMResponse.builder()
            .previousOperation("lookupData")
            .nextOperation("processData")
            .memoryLookup("find previous user interactions")
            .summaryForLLM("Looking up and processing data")
            .responseForUser("Processing your request")
            .arguments(args)
            .build();
        
        // When
        String json = JsonUtil.MAPPER.writeValueAsString(response);
        LLMResponse deserialized = JsonUtil.MAPPER.readValue(json, LLMResponse.class);
        
        // Then
        assertNotNull(deserialized);
        assertEquals(response.getPreviousOperation(), deserialized.getPreviousOperation());
        assertEquals(response.getNextOperation(), deserialized.getNextOperation());
        assertEquals(response.getMemoryLookup(), deserialized.getMemoryLookup());
        assertEquals(response.getSummaryForLLM(), deserialized.getSummaryForLLM());
        assertEquals(response.getResponseForUser(), deserialized.getResponseForUser());
    }

    @Test
    void testLLMResponseJsonDeserializationWithMemoryLookup() throws JsonProcessingException {
        // Given
        String json = "{"
            + "\"previousOperation\":\"getStatus\","
            + "\"nextOperation\":\"updateStatus\","
            + "\"memoryLookup\":\"search for system configuration\","
            + "\"summaryForLLM\":\"Status check and update\","
            + "\"responseForUser\":\"Status updated\","
            + "\"arguments\":{\"status\":\"active\"}"
            + "}";
        
        // When
        LLMResponse response = JsonUtil.MAPPER.readValue(json, LLMResponse.class);
        
        // Then
        assertNotNull(response);
        assertEquals("getStatus", response.getPreviousOperation());
        assertEquals("updateStatus", response.getNextOperation());
        assertEquals("search for system configuration", response.getMemoryLookup());
        assertEquals("Status check and update", response.getSummaryForLLM());
        assertEquals("Status updated", response.getResponseForUser());
        assertNotNull(response.getArguments());
    }

    @Test
    void testLLMResponseJsonDeserializationWithoutMemoryLookup() throws JsonProcessingException {
        // Given
        String json = "{"
            + "\"previousOperation\":\"getStatus\","
            + "\"nextOperation\":\"updateStatus\","
            + "\"summaryForLLM\":\"Status check and update\","
            + "\"responseForUser\":\"Status updated\""
            + "}";
        
        // When
        LLMResponse response = JsonUtil.MAPPER.readValue(json, LLMResponse.class);
        
        // Then
        assertNotNull(response);
        assertEquals("getStatus", response.getPreviousOperation());
        assertEquals("updateStatus", response.getNextOperation());
        assertNull(response.getMemoryLookup());
        assertEquals("Status check and update", response.getSummaryForLLM());
        assertEquals("Status updated", response.getResponseForUser());
    }

    @Test
    void testLLMResponseWithEmptyMemoryLookup() {
        // Given & When
        LLMResponse response = LLMResponse.builder()
            .previousOperation("operation1")
            .nextOperation("operation2")
            .memoryLookup("")
            .summaryForLLM("Summary")
            .responseForUser("Response")
            .build();
        
        // Then
        assertEquals("", response.getMemoryLookup());
        assertNotNull(response.getMemoryLookup());
    }
}
