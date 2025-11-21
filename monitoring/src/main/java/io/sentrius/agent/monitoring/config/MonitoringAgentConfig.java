package io.sentrius.agent.monitoring.config;

import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Configuration for the Monitoring Agent
 */
@Configuration
public class MonitoringAgentConfig {
    
    @Bean
    public Tracer tracer() {
        return OpenTelemetrySdk.builder()
            .build()
            .getTracer("monitoring-agent");
    }
}
