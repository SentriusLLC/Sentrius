package io.sentrius.sso.core.services.metadata;

import io.sentrius.sso.core.model.metadata.TerminalBiometricMetrics;
import io.sentrius.sso.core.model.metadata.TerminalSessionMetadata;
import io.sentrius.sso.core.repository.TerminalBiometricMetricsRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Optional;

/**
 * Service for managing terminal biometric metrics
 * Biometric computation is handled in the analytics package where terminal logs are available.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TerminalBiometricMetricsService {
    
    private final TerminalBiometricMetricsRepository biometricMetricsRepository;
    
    /**
     * Save biometric metrics
     */
    public TerminalBiometricMetrics save(TerminalBiometricMetrics metrics) {
        return biometricMetricsRepository.save(metrics);
    }
    
    /**
     * Retrieve biometric metrics for a session
     */
    public Optional<TerminalBiometricMetrics> getMetricsForSession(TerminalSessionMetadata session) {
        return biometricMetricsRepository.findBySession(session);
    }
    
    /**
     * Retrieve biometric metrics by session ID
     */
    public Optional<TerminalBiometricMetrics> getMetricsBySessionId(Long sessionId) {
        return biometricMetricsRepository.findBySessionId(sessionId);
    }
}