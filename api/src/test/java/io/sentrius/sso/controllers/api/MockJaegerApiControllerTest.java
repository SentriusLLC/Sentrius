package io.sentrius.sso.controllers.api;

import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class MockJaegerApiControllerTest {

    private MockJaegerApiController mockController = new MockJaegerApiController();

    @Test
    void testGetMockServices() {
        ResponseEntity<?> response = mockController.getMockServices();
        
        assertEquals(200, response.getStatusCode().value());
        assertNotNull(response.getBody());
        
        Map<String, Object> body = (Map<String, Object>) response.getBody();
        assertTrue(body.containsKey("data"));
        
        List<String> services = (List<String>) body.get("data");
        assertEquals(4, services.size());
        assertTrue(services.contains("sentrius-api"));
        assertTrue(services.contains("sentrius-dataplane"));
    }

    @Test
    void testGetMockTraces() {
        ResponseEntity<?> response = mockController.getMockTraces();
        
        assertEquals(200, response.getStatusCode().value());
        assertNotNull(response.getBody());
        
        Map<String, Object> body = (Map<String, Object>) response.getBody();
        assertTrue(body.containsKey("data"));
        
        List<Map<String, Object>> traces = (List<Map<String, Object>>) body.get("data");
        assertEquals(2, traces.size());
        
        // Verify trace structure
        Map<String, Object> trace = traces.get(0);
        assertTrue(trace.containsKey("traceID"));
        assertTrue(trace.containsKey("spans"));
        
        List<Map<String, Object>> spans = (List<Map<String, Object>>) trace.get("spans");
        assertTrue(spans.size() > 0);
        
        // Verify span structure
        Map<String, Object> span = spans.get(0);
        assertTrue(span.containsKey("spanID"));
        assertTrue(span.containsKey("operationName"));
        assertTrue(span.containsKey("startTime"));
        assertTrue(span.containsKey("duration"));
        assertTrue(span.containsKey("process"));
    }
}