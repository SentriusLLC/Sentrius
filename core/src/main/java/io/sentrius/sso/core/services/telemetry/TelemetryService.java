package io.sentrius.sso.core.services.telemetry;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Scope;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Map;

@Slf4j
@Service
public class TelemetryService {

    private final Tracer tracer = GlobalOpenTelemetry.getTracer("io.sentrius.sso");

    /**
     * Start a new telemetry span, run logic inside it, and close it automatically.
     */
    public <T> T trace(String spanName, Map<String, String> attributes, TelemetryTask<T> task) {
        Span span = tracer.spanBuilder(spanName)
            .setSpanKind(SpanKind.INTERNAL)
            .startSpan();
        try (Scope scope = span.makeCurrent()) {
            if (attributes != null) {
                attributes.forEach(span::setAttribute);
            }
            return task.execute();
        } catch (Exception e) {
            span.recordException(e);
            span.setStatus(io.opentelemetry.api.trace.StatusCode.ERROR);
            log.error("Error during telemetry task", e);
            throw new RuntimeException(e);
        } finally {
            span.end();
        }
    }

    @FunctionalInterface
    public interface TelemetryTask<T> {
        T execute() throws Exception;
    }
}
