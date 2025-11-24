package io.sentrius.agent.analysis.service;

import io.sentrius.sso.core.model.LLMResponse;
import io.sentrius.sso.core.services.openai.OpenAITwoPartyMonitorService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class LLMGuidedSchedulerServiceTest {

    @Mock
    private OpenAITwoPartyMonitorService llmService;

    private LLMGuidedSchedulerService schedulerService;

    @BeforeEach
    void setUp() {
        schedulerService = new LLMGuidedSchedulerService(llmService);
    }

    @Test
    void testShouldRunTrustEvaluation_WhenLLMRecommendsYes() throws Exception {
        // Arrange
        when(llmService.isEnabled()).thenReturn(true);
        LLMResponse response = LLMResponse.builder()
            .score(0.7) // Above threshold of 0.5
            .response("Should run")
            .build();
        when(llmService.analyzeTerminalLogs(any())).thenReturn(CompletableFuture.completedFuture(response));

        // Act
        Boolean result = schedulerService.shouldRunTrustEvaluation().get();

        // Assert
        assertTrue(result);
        verify(llmService).analyzeTerminalLogs(any());
    }

    @Test
    void testShouldRunTrustEvaluation_WhenLLMNotEnabled() throws Exception {
        // Arrange
        when(llmService.isEnabled()).thenReturn(false);

        // Act
        Boolean result = schedulerService.shouldRunTrustEvaluation().get();

        // Assert
        assertTrue(result); // Should default to running when LLM not available
        verify(llmService, never()).analyzeTerminalLogs(any());
    }

    @Test
    void testIsEnabled_WhenLLMEnabled() {
        // Arrange
        when(llmService.isEnabled()).thenReturn(true);

        // Act
        boolean result = schedulerService.isEnabled();

        // Assert
        assertTrue(result);
    }
}
