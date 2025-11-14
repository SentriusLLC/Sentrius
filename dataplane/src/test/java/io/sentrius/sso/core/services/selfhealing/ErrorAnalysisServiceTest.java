package io.sentrius.sso.core.services.selfhealing;

import io.sentrius.sso.core.model.ErrorOutput;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
class ErrorAnalysisServiceTest {

    @Mock
    private SelfHealingConfigService configService;

    @Mock
    private SelfHealingSessionService sessionService;

    @InjectMocks
    private ErrorAnalysisService errorAnalysisService;

    private ErrorOutput testError;

    @BeforeEach
    void setUp() {
        testError = ErrorOutput.builder()
                .id(1L)
                .errorType("NullPointerException")
                .errorLocation("api-service")
                .errorLogs("Error in pod api-service: NullPointerException at line 42")
                .build();
    }

    @Test
    void testExtractPodName_FromLocation() {
        String podName = errorAnalysisService.extractPodName(testError);
        assertEquals("api-service", podName);
    }

    @Test
    void testExtractPodName_FromLogs() {
        testError.setErrorLocation(null);
        testError.setErrorLogs("Error in pod sentrius-api-12345: Database connection failed");

        String podName = errorAnalysisService.extractPodName(testError);
        assertEquals("sentrius-api-12345", podName);
    }

    @Test
    void testIsLikelySecurityConcern_WithSecurityKeyword() {
        testError.setErrorLogs("Authentication failed: Unauthorized access attempt");

        boolean isConcern = errorAnalysisService.isLikelySecurityConcern(testError);

        assertTrue(isConcern);
    }

    @Test
    void testIsLikelySecurityConcern_NoSecurityKeyword() {
        testError.setErrorLogs("Database connection timeout");

        boolean isConcern = errorAnalysisService.isLikelySecurityConcern(testError);

        assertFalse(isConcern);
    }

    @Test
    void testIsLikelySecurityConcern_VulnerabilityKeyword() {
        testError.setErrorLogs("Potential SQL injection vulnerability detected");

        boolean isConcern = errorAnalysisService.isLikelySecurityConcern(testError);

        assertTrue(isConcern);
    }

    @Test
    void testExtractPodName_NoMatch() {
        testError.setErrorLocation(null);
        testError.setErrorLogs("Generic error message with no match");

        String podName = errorAnalysisService.extractPodName(testError);

        assertNull(podName);
    }
}
