package io.sentrius.sso.controllers.api;

import io.sentrius.sso.core.config.SystemOptions;
import io.sentrius.sso.core.controllers.BaseController;
import io.sentrius.sso.core.services.ErrorOutputService;
import io.sentrius.sso.core.services.UserService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;

import java.util.*;

@Slf4j
@RestController
@RequestMapping("/api/v1/telemetry")
public class TelemetryApiController extends BaseController {

    private final RestTemplate restTemplate = new RestTemplate();
    
    @Value("${jaeger.query.url:http://localhost:16686}")
    private String jaegerQueryUrl;

    protected TelemetryApiController(
        UserService userService, 
        SystemOptions systemOptions,
        ErrorOutputService errorOutputService
    ) {
        super(userService, systemOptions, errorOutputService);
    }

    @GetMapping("/traces")
    public ResponseEntity<?> getTraces(
            @RequestParam(required = false) String service,
            @RequestParam(required = false) String operation,
            @RequestParam(defaultValue = "1h") String lookback,
            @RequestParam(required = false) Long minDuration,
            @RequestParam(required = false) Long maxDuration,
            @RequestParam(required = false) String tags
    ) {
        try {
            String jaegerApiUrl = buildJaegerApiUrl(service, operation, lookback, minDuration, maxDuration, tags);
            log.info("Querying Jaeger at: {}", jaegerApiUrl);
            
            HttpHeaders headers = new HttpHeaders();
            headers.set("Accept", "application/json");
            HttpEntity<String> entity = new HttpEntity<>(headers);
            
            ResponseEntity<Map> response = restTemplate.exchange(
                jaegerApiUrl, 
                HttpMethod.GET, 
                entity, 
                Map.class
            );
            
            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                Map<String, Object> jaegerResponse = response.getBody();
                List<Map<String, Object>> processedTraces = processJaegerResponse(jaegerResponse);
                
                Map<String, Object> result = new HashMap<>();
                result.put("traces", processedTraces);
                result.put("status", "success");
                result.put("count", processedTraces.size());
                
                return ResponseEntity.ok(result);
            } else {
                return ResponseEntity.status(response.getStatusCode())
                    .body(Map.of("error", "Failed to query Jaeger", "status", "error"));
            }
            
        } catch (Exception e) {
            log.error("Error querying Jaeger traces", e);
            return ResponseEntity.internalServerError()
                .body(Map.of("error", "Internal server error: " + e.getMessage(), "status", "error"));
        }
    }

    @GetMapping("/services")
    public ResponseEntity<?> getServices() {
        try {
            String servicesUrl = jaegerQueryUrl + "/api/services";
            log.info("Fetching services from Jaeger at: {}", servicesUrl);
            
            HttpHeaders headers = new HttpHeaders();
            headers.set("Accept", "application/json");
            HttpEntity<String> entity = new HttpEntity<>(headers);
            
            ResponseEntity<Map> response = restTemplate.exchange(
                servicesUrl,
                HttpMethod.GET,
                entity,
                Map.class
            );
            
            return ResponseEntity.ok(response.getBody());
            
        } catch (Exception e) {
            log.error("Error fetching services from Jaeger", e);
            return ResponseEntity.internalServerError()
                .body(Map.of("error", "Failed to fetch services: " + e.getMessage(), "status", "error"));
        }
    }

    @GetMapping("/trace/{traceId}")
    public ResponseEntity<?> getTrace(@PathVariable String traceId) {
        try {
            String traceUrl = jaegerQueryUrl + "/api/traces/" + traceId;
            log.info("Fetching trace from Jaeger at: {}", traceUrl);
            
            HttpHeaders headers = new HttpHeaders();
            headers.set("Accept", "application/json");
            HttpEntity<String> entity = new HttpEntity<>(headers);
            
            ResponseEntity<Map> response = restTemplate.exchange(
                traceUrl,
                HttpMethod.GET,
                entity,
                Map.class
            );
            
            return ResponseEntity.ok(response.getBody());
            
        } catch (Exception e) {
            log.error("Error fetching trace from Jaeger", e);
            return ResponseEntity.internalServerError()
                .body(Map.of("error", "Failed to fetch trace: " + e.getMessage(), "status", "error"));
        }
    }

    private String buildJaegerApiUrl(String service, String operation, String lookback, 
                                   Long minDuration, Long maxDuration, String tags) {
        StringBuilder url = new StringBuilder(jaegerQueryUrl + "/api/traces?");
        
        if (service != null && !service.isEmpty()) {
            url.append("service=").append(service).append("&");
        }
        
        if (operation != null && !operation.isEmpty()) {
            url.append("operation=").append(operation).append("&");
        }
        
        url.append("lookback=").append(lookback).append("&");
        
        if (minDuration != null) {
            url.append("minDuration=").append(minDuration).append("us&");
        }
        
        if (maxDuration != null) {
            url.append("maxDuration=").append(maxDuration).append("us&");
        }
        
        if (tags != null && !tags.isEmpty()) {
            url.append("tags=").append(tags).append("&");
        }
        
        // Add limit to prevent too many results
        url.append("limit=100");
        
        return url.toString();
    }

    private List<Map<String, Object>> processJaegerResponse(Map<String, Object> jaegerResponse) {
        List<Map<String, Object>> processedTraces = new ArrayList<>();
        
        try {
            Object dataObj = jaegerResponse.get("data");
            if (dataObj instanceof List) {
                List<Map<String, Object>> traces = (List<Map<String, Object>>) dataObj;
                
                for (Map<String, Object> trace : traces) {
                    Map<String, Object> processedTrace = new HashMap<>();
                    processedTrace.put("traceID", trace.get("traceID"));
                    
                    // Calculate duration and other metrics
                    Object spansObj = trace.get("spans");
                    if (spansObj instanceof List) {
                        List<Map<String, Object>> spans = (List<Map<String, Object>>) spansObj;
                        processedTrace.put("spans", spans);
                        processedTrace.put("spanCount", spans.size());
                        
                        // Find root span for start time and total duration
                        Optional<Map<String, Object>> rootSpan = spans.stream()
                            .filter(span -> {
                                Object refs = span.get("references");
                                return refs == null || (refs instanceof List && ((List<?>) refs).isEmpty());
                            })
                            .findFirst();
                            
                        if (rootSpan.isPresent()) {
                            processedTrace.put("startTime", rootSpan.get().get("startTime"));
                            processedTrace.put("duration", rootSpan.get().get("duration"));
                        }
                    }
                    
                    processedTraces.add(processedTrace);
                }
            }
        } catch (Exception e) {
            log.warn("Error processing Jaeger response", e);
        }
        
        return processedTraces;
    }
}