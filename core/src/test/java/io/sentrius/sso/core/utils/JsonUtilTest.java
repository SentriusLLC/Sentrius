package io.sentrius.sso.core.utils;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.node.ArrayNode;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class JsonUtilTest {

    @Test
    void mapperIsConfiguredWithJavaTimeModule() {
        assertNotNull(JsonUtil.MAPPER);
        assertTrue(JsonUtil.MAPPER.getRegisteredModuleIds().contains("jackson-datatype-jsr310"));
    }

    @Test
    void convertArrayNodeToListConvertsStringList() throws JsonProcessingException {
        // Create an ArrayNode with string values
        ArrayNode arrayNode = JsonUtil.MAPPER.createArrayNode();
        arrayNode.add("item1");
        arrayNode.add("item2");
        arrayNode.add("item3");

        TypeReference<List<String>> typeRef = new TypeReference<List<String>>() {};
        List<String> result = JsonUtil.convertArrayNodeToList(arrayNode, typeRef);

        assertNotNull(result);
        assertEquals(3, result.size());
        assertEquals("item1", result.get(0));
        assertEquals("item2", result.get(1));
        assertEquals("item3", result.get(2));
    }

    @Test
    void convertArrayNodeToListConvertsIntegerList() throws JsonProcessingException {
        // Create an ArrayNode with integer values
        ArrayNode arrayNode = JsonUtil.MAPPER.createArrayNode();
        arrayNode.add(1);
        arrayNode.add(2);
        arrayNode.add(3);

        TypeReference<List<Integer>> typeRef = new TypeReference<List<Integer>>() {};
        List<Integer> result = JsonUtil.convertArrayNodeToList(arrayNode, typeRef);

        assertNotNull(result);
        assertEquals(3, result.size());
        assertEquals(Integer.valueOf(1), result.get(0));
        assertEquals(Integer.valueOf(2), result.get(1));
        assertEquals(Integer.valueOf(3), result.get(2));
    }

    @Test
    void convertArrayNodeToListHandlesEmptyArray() throws JsonProcessingException {
        ArrayNode emptyArrayNode = JsonUtil.MAPPER.createArrayNode();

        TypeReference<List<String>> typeRef = new TypeReference<List<String>>() {};
        List<String> result = JsonUtil.convertArrayNodeToList(emptyArrayNode, typeRef);

        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    @Test
    void convertArrayNodeToListThrowsExceptionForInvalidJson() {
        // Create a malformed ArrayNode by manually creating invalid JSON
        ArrayNode arrayNode = JsonUtil.MAPPER.createArrayNode();
        arrayNode.add("valid");
        
        // Force invalid JSON by trying to parse with wrong type
        TypeReference<List<Integer>> typeRef = new TypeReference<List<Integer>>() {};
        
        assertThrows(JsonProcessingException.class, () -> {
            JsonUtil.convertArrayNodeToList(arrayNode, typeRef);
        });
    }

    @Test
    void mapperHandlesDateTimeSerialization() throws JsonProcessingException {
        // Test that the mapper can handle date/time objects without timestamps
        java.time.LocalDateTime now = java.time.LocalDateTime.now();
        String json = JsonUtil.MAPPER.writeValueAsString(now);
        
        assertNotNull(json);
        assertFalse(json.matches("\\d+")); // Should not be a timestamp (just numbers)
        assertTrue(json.contains("-")); // Should contain ISO format separators
    }
}