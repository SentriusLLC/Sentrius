package io.sentrius.agent.monitoring.service;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.sdk.trace.data.SpanData;
import io.opentelemetry.sdk.trace.data.StatusData;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * Service for querying and analyzing OpenTelemetry traces
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OtelTraceQueryService {
    
    private final Tracer tracer;
    private final RestTemplate restTemplate = new RestTemplate();
    
    @Value("${otel.backend.url:http://localhost:16686}")
    private String otelBackendUrl;
    
    // In-memory cache for recent trace data
    private final Map<String, List<SpanData>> traceCache = new ConcurrentHashMap<>();
    
    /**
     * Query traces for a specific service within a time range
     * 
     * @param serviceName The name of the service to query
     * @param startTime Start of the time range
     * @param endTime End of the time range
     * @return List of trace spans
     */
    public List<SpanData> queryTraces(String serviceName, Instant startTime, Instant endTime) {
        log.debug("Querying traces for service: {} between {} and {}", 
                  serviceName, startTime, endTime);
        
        // Try to query from OTel backend (Jaeger API)
        try {
            String queryUrl = String.format("%s/api/traces?service=%s&start=%d&end=%d",
                otelBackendUrl, serviceName, 
                startTime.toEpochMilli() * 1000, // Jaeger uses microseconds
                endTime.toEpochMilli() * 1000);
            
            log.debug("Querying OTel backend: {}", queryUrl);
            // In a real implementation, parse the Jaeger JSON response
            // For now, return from cache as fallback
            
        } catch (Exception e) {
            log.debug("Could not query OTel backend ({}), using cache", e.getMessage());
        }
        
        return traceCache.getOrDefault(serviceName, new ArrayList<>());
    }
    
    /**
     * Analyze error rates from traces
     * 
     * @param serviceName The service to analyze
     * @param windowMinutes Time window in minutes
     * @return Error rate as a percentage
     */
    public double calculateErrorRate(String serviceName, int windowMinutes) {
        Instant endTime = Instant.now();
        Instant startTime = endTime.minusSeconds(windowMinutes * 60L);
        
        List<SpanData> spans = queryTraces(serviceName, startTime, endTime);
        
        if (spans.isEmpty()) {
            return 0.0;
        }
        
        long errorCount = spans.stream()
            .filter(this::isErrorSpan)
            .count();
        
        double errorRate = (double) errorCount / spans.size() * 100.0;
        log.info("Service {} error rate: {:.2f}% over {} minutes", 
                 serviceName, errorRate, windowMinutes);
        
        return errorRate;
    }
    
    /**
     * Calculate average latency from traces
     * 
     * @param serviceName The service to analyze
     * @param windowMinutes Time window in minutes
     * @return Average latency in milliseconds
     */
    public double calculateAverageLatency(String serviceName, int windowMinutes) {
        Instant endTime = Instant.now();
        Instant startTime = endTime.minusSeconds(windowMinutes * 60L);
        
        List<SpanData> spans = queryTraces(serviceName, startTime, endTime);
        
        if (spans.isEmpty()) {
            return 0.0;
        }
        
        double avgLatency = spans.stream()
            .mapToLong(this::getSpanDuration)
            .average()
            .orElse(0.0);
        
        log.debug("Service {} average latency: {:.2f}ms over {} minutes", 
                  serviceName, avgLatency, windowMinutes);
        
        return avgLatency;
    }
    
    /**
     * Get throughput (requests per second) for a service
     * 
     * @param serviceName The service to analyze
     * @param windowMinutes Time window in minutes
     * @return Requests per second
     */
    public double calculateThroughput(String serviceName, int windowMinutes) {
        Instant endTime = Instant.now();
        Instant startTime = endTime.minusSeconds(windowMinutes * 60L);
        
        List<SpanData> spans = queryTraces(serviceName, startTime, endTime);
        
        if (spans.isEmpty()) {
            return 0.0;
        }
        
        double throughput = (double) spans.size() / (windowMinutes * 60.0);
        log.debug("Service {} throughput: {:.2f} req/s over {} minutes", 
                  serviceName, throughput, windowMinutes);
        
        return throughput;
    }
    
    private boolean isErrorSpan(SpanData span) {
        // Check span status for errors
        StatusData status = span.getStatus();
        if (status != null && status.getStatusCode() == StatusCode.ERROR) {
            return true;
        }
        
        // Also check for HTTP error status codes in attributes
        var attributes = span.getAttributes();
        if (attributes != null) {
            Object httpStatus = attributes.asMap().get(io.opentelemetry.semconv.SemanticAttributes.HTTP_STATUS_CODE);
            if (httpStatus instanceof Long) {
                long statusCode = (Long) httpStatus;
                return statusCode >= 400;
            }
        }
        
        return false;
    }
    
    private long getSpanDuration(SpanData span) {
        // Calculate span duration in milliseconds
        long startNanos = span.getStartEpochNanos();
        long endNanos = span.getEndEpochNanos();
        return TimeUnit.NANOSECONDS.toMillis(endNanos - startNanos);
    }
}
