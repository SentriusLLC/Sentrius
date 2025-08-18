package io.sentrius.sso.controllers.api;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.*;

/**
 * Mock Jaeger API for testing telemetry UI when actual Jaeger is not available.
 * This controller provides sample trace data for demonstration purposes.
 */
@RestController
@RequestMapping("/mock/jaeger/api")
public class MockJaegerApiController {

    @GetMapping("/services")
    public ResponseEntity<?> getMockServices() {
        Map<String, Object> response = new HashMap<>();
        List<String> services = Arrays.asList(
            "sentrius-api",
            "sentrius-dataplane", 
            "sentrius-agent-proxy",
            "sentrius-integration-proxy"
        );
        response.put("data", services);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/traces")
    public ResponseEntity<?> getMockTraces() {
        Map<String, Object> response = new HashMap<>();
        List<Map<String, Object>> traces = new ArrayList<>();
        
        // Create sample trace 1
        Map<String, Object> trace1 = createSampleTrace(
            "1234567890abcdef",
            "sentrius-api",
            Arrays.asList("HTTP GET /sso/v1/dashboard", "Database Query", "Cache Lookup"),
            150000 // 150ms
        );
        
        // Create sample trace 2
        Map<String, Object> trace2 = createSampleTrace(
            "fedcba0987654321",
            "sentrius-api", 
            Arrays.asList("HTTP POST /api/v1/users", "User Validation", "Database Insert", "Send Notification"),
            320000 // 320ms
        );
        
        traces.add(trace1);
        traces.add(trace2);
        
        response.put("data", traces);
        return ResponseEntity.ok(response);
    }

    private Map<String, Object> createSampleTrace(String traceId, String serviceName, List<String> operations, long totalDuration) {
        Map<String, Object> trace = new HashMap<>();
        trace.put("traceID", traceId);
        
        List<Map<String, Object>> spans = new ArrayList<>();
        long startTime = Instant.now().toEpochMilli() * 1000; // Convert to microseconds
        long currentTime = startTime;
        
        for (int i = 0; i < operations.size(); i++) {
            Map<String, Object> span = new HashMap<>();
            span.put("spanID", String.format("%016x", i + 1));
            span.put("operationName", operations.get(i));
            span.put("startTime", currentTime);
            
            long duration = totalDuration / operations.size();
            span.put("duration", duration);
            
            // Create process info
            Map<String, Object> process = new HashMap<>();
            process.put("serviceName", serviceName);
            span.put("process", process);
            
            // Add references for child spans
            if (i > 0) {
                List<Map<String, Object>> references = new ArrayList<>();
                Map<String, Object> ref = new HashMap<>();
                ref.put("refType", "CHILD_OF");
                ref.put("spanID", String.format("%016x", i)); // Reference parent
                references.add(ref);
                span.put("references", references);
            } else {
                span.put("references", new ArrayList<>());
            }
            
            spans.add(span);
            currentTime += duration;
        }
        
        trace.put("spans", spans);
        return trace;
    }
}