package io.sentrius.sso.core.services.selfhealing;

import io.sentrius.sso.core.model.ErrorOutput;
import io.sentrius.sso.core.model.security.IntegrationSecurityToken;
import io.sentrius.sso.core.model.selfhealing.SelfHealingConfig.PatchingPolicy;
import io.sentrius.sso.core.model.selfhealing.SelfHealingSession;
import io.sentrius.sso.core.services.ErrorOutputService;
import io.sentrius.sso.core.services.security.IntegrationSecurityTokenService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@Disabled("Requires PostgreSQL with pgvector extension - not compatible with H2")
@ExtendWith(MockitoExtension.class)
class SelfHealingOrchestratorTest {

    @Mock
    private ErrorOutputService errorOutputService;

    @Mock
    private ErrorAnalysisService errorAnalysisService;

    @Mock
    private SelfHealingConfigService configService;

    @Mock
    private SelfHealingSessionService sessionService;

    @Mock
    private HealingWorkflowCoordinator workflowCoordinator;

    @Mock
    private IntegrationSecurityTokenService integrationTokenService;

    @InjectMocks
    private SelfHealingOrchestrator orchestrator;

    private ErrorOutput testError;
    private IntegrationSecurityToken githubToken;

    @BeforeEach
    void setUp() {
        testError = ErrorOutput.builder()
                .id(1L)
                .errorType("NullPointerException")
                .errorLocation("api-service")
                .errorLogs("Error in pod api-service: NullPointerException at line 42")
                .build();

        githubToken = IntegrationSecurityToken.builder()
                .id(1L)
                .name("GitHub Token")
                .connectionType("github")
                .connectionInfo("{\"token\":\"ghp_test123\"}")
                .build();

        // Set default values for the orchestrator
        ReflectionTestUtils.setField(orchestrator, "selfHealingEnabled", true);
        ReflectionTestUtils.setField(orchestrator, "githubConfigured", false);
        ReflectionTestUtils.setField(orchestrator, "offHoursStart", 22);
        ReflectionTestUtils.setField(orchestrator, "offHoursEnd", 6);
    }

    @Test
    void testScanForHealableErrors_DisabledWhenSelfHealingDisabled() {
        ReflectionTestUtils.setField(orchestrator, "selfHealingEnabled", false);

        orchestrator.scanForHealableErrors();

        verify(errorOutputService, never()).getErrorOutputs(anyInt(), anyInt());
    }

    @Test
    void testScanForHealableErrors_WorksWithGitHubTokensPresent() {
        // Setup: GitHub tokens exist in database, config flag is false (default)
        List<IntegrationSecurityToken> tokens = Collections.singletonList(githubToken);
        when(integrationTokenService.findByConnectionType("github")).thenReturn(tokens);
        
        List<ErrorOutput> errors = Collections.singletonList(testError);
        when(errorOutputService.getErrorOutputs(0, 50)).thenReturn(errors);
        when(errorAnalysisService.shouldTriggerHealing(testError)).thenReturn(true);
        when(errorAnalysisService.extractPodName(testError)).thenReturn("api-service");
        when(configService.getPatchingPolicyForPod("api-service")).thenReturn(PatchingPolicy.IMMEDIATE);
        when(errorAnalysisService.isLikelySecurityConcern(testError)).thenReturn(false);
        
        SelfHealingSession session = SelfHealingSession.builder()
                .id(1L)
                .errorOutput(testError)
                .build();
        when(errorAnalysisService.initiateHealing(testError)).thenReturn(session);

        orchestrator.scanForHealableErrors();

        // Verify the scan happened and healing was initiated
        verify(errorOutputService).getErrorOutputs(0, 50);
        verify(errorAnalysisService).shouldTriggerHealing(testError);
        verify(errorAnalysisService).initiateHealing(testError);
        verify(workflowCoordinator).executeHealingWorkflow(session);
    }

    @Test
    void testScanForHealableErrors_DisabledWhenNoGitHubTokens() {
        // Setup: No GitHub tokens in database
        when(integrationTokenService.findByConnectionType("github")).thenReturn(Collections.emptyList());

        orchestrator.scanForHealableErrors();

        // Verify scan was aborted due to no tokens
        verify(integrationTokenService).findByConnectionType("github");
        verify(errorOutputService, never()).getErrorOutputs(anyInt(), anyInt());
    }

    @Test
    void testScanForHealableErrors_WorksWithTokensEvenWhenConfigIsFalse() {
        // Setup: GitHub tokens exist but config flag is false (default)
        // The fix ensures tokens in DB take precedence over config flag
        ReflectionTestUtils.setField(orchestrator, "githubConfigured", false);
        List<IntegrationSecurityToken> tokens = Collections.singletonList(githubToken);
        when(integrationTokenService.findByConnectionType("github")).thenReturn(tokens);
        
        List<ErrorOutput> errors = Collections.singletonList(testError);
        when(errorOutputService.getErrorOutputs(0, 50)).thenReturn(errors);
        when(errorAnalysisService.shouldTriggerHealing(testError)).thenReturn(true);
        when(errorAnalysisService.extractPodName(testError)).thenReturn("api-service");
        when(configService.getPatchingPolicyForPod("api-service")).thenReturn(PatchingPolicy.IMMEDIATE);
        when(errorAnalysisService.isLikelySecurityConcern(testError)).thenReturn(false);
        
        SelfHealingSession session = SelfHealingSession.builder()
                .id(1L)
                .errorOutput(testError)
                .build();
        when(errorAnalysisService.initiateHealing(testError)).thenReturn(session);

        orchestrator.scanForHealableErrors();

        // With the fix, tokens in database enable GitHub integration regardless of config flag
        verify(errorOutputService).getErrorOutputs(0, 50);
        verify(errorAnalysisService).initiateHealing(testError);
    }

    @Test
    void testScanForHealableErrors_DisabledWhenIntegrationServiceNull() {
        // Setup: IntegrationSecurityTokenService is null
        ReflectionTestUtils.setField(orchestrator, "integrationTokenService", null);

        orchestrator.scanForHealableErrors();

        // Verify scan was aborted
        verify(errorOutputService, never()).getErrorOutputs(anyInt(), anyInt());
    }

    @Test
    void testProcessErrorForHealing_ImmediatePolicy() {
        when(errorAnalysisService.extractPodName(testError)).thenReturn("api-service");
        when(configService.getPatchingPolicyForPod("api-service")).thenReturn(PatchingPolicy.IMMEDIATE);
        when(errorAnalysisService.isLikelySecurityConcern(testError)).thenReturn(false);
        
        SelfHealingSession session = SelfHealingSession.builder()
                .id(1L)
                .errorOutput(testError)
                .build();
        when(errorAnalysisService.initiateHealing(testError)).thenReturn(session);

        orchestrator.processErrorForHealing(testError);

        verify(errorAnalysisService).initiateHealing(testError);
        verify(sessionService).updateSessionStatus(eq(1L), any());
        verify(workflowCoordinator).executeHealingWorkflow(session);
    }

    @Test
    void testProcessErrorForHealing_NeverPolicy() {
        when(errorAnalysisService.extractPodName(testError)).thenReturn("api-service");
        when(configService.getPatchingPolicyForPod("api-service")).thenReturn(PatchingPolicy.NEVER);

        orchestrator.processErrorForHealing(testError);

        verify(errorAnalysisService, never()).initiateHealing(any());
        verify(workflowCoordinator, never()).executeHealingWorkflow(any());
    }

    @Test
    void testProcessErrorForHealing_SecurityConcern() {
        when(errorAnalysisService.extractPodName(testError)).thenReturn("api-service");
        when(configService.getPatchingPolicyForPod("api-service")).thenReturn(PatchingPolicy.IMMEDIATE);
        when(errorAnalysisService.isLikelySecurityConcern(testError)).thenReturn(true);
        
        SelfHealingSession session = SelfHealingSession.builder()
                .id(1L)
                .errorOutput(testError)
                .build();
        when(errorAnalysisService.initiateHealing(testError)).thenReturn(session);

        orchestrator.processErrorForHealing(testError);

        verify(errorAnalysisService).initiateHealing(testError);
        verify(sessionService).recordSecurityAnalysis(eq(1L), eq(true), anyString());
        verify(workflowCoordinator, never()).executeHealingWorkflow(any());
    }

    @Test
    void testProcessQueuedErrors_WorksWithGitHubTokens() {
        // Setup: GitHub tokens exist, it's off-hours
        ReflectionTestUtils.setField(orchestrator, "offHoursStart", 0);
        ReflectionTestUtils.setField(orchestrator, "offHoursEnd", 23);
        
        List<IntegrationSecurityToken> tokens = Collections.singletonList(githubToken);
        when(integrationTokenService.findByConnectionType("github")).thenReturn(tokens);

        ErrorOutput queuedError = ErrorOutput.builder()
                .id(2L)
                .errorType("DatabaseException")
                .errorLocation("data-service")
                .healingStatus("QUEUED")
                .build();

        when(errorOutputService.getAllErrorOutputs()).thenReturn(Collections.singletonList(queuedError));
        when(errorAnalysisService.extractPodName(queuedError)).thenReturn("data-service");
        when(configService.getPatchingPolicyForPod("data-service")).thenReturn(PatchingPolicy.OFF_HOURS);
        when(errorAnalysisService.isLikelySecurityConcern(queuedError)).thenReturn(false);
        
        SelfHealingSession session = SelfHealingSession.builder()
                .id(2L)
                .errorOutput(queuedError)
                .build();
        when(errorAnalysisService.initiateHealing(queuedError)).thenReturn(session);

        orchestrator.processQueuedErrors();

        verify(errorAnalysisService).initiateHealing(queuedError);
    }

    @Test
    void testProcessQueuedErrors_DisabledWhenNoGitHubTokens() {
        // Setup: It's off-hours but no GitHub tokens
        ReflectionTestUtils.setField(orchestrator, "offHoursStart", 0);
        ReflectionTestUtils.setField(orchestrator, "offHoursEnd", 23);
        
        when(integrationTokenService.findByConnectionType("github")).thenReturn(Collections.emptyList());

        orchestrator.processQueuedErrors();

        verify(integrationTokenService).findByConnectionType("github");
        verify(errorOutputService, never()).getAllErrorOutputs();
    }
}
