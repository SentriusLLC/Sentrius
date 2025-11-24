package io.sentrius.agent.analysis.service;

import io.sentrius.agent.analysis.model.AgentConfigurationChange;
import io.sentrius.sso.core.dto.agents.AgentExecution;
import io.sentrius.sso.core.exceptions.ZtatException;
import io.sentrius.sso.core.services.agents.ZeroTrustClientService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AgentConfigurationApprovalServiceTest {

    @Mock
    private ZeroTrustClientService zeroTrustClientService;
    
    @Mock
    private RegisteredAnalyticsAgent analyticsAgent;

    private AgentConfigurationApprovalService approvalService;

    @BeforeEach
    void setUp() {
        lenient().when(analyticsAgent.getAgentName()).thenReturn("analytics-agent");
        lenient().when(analyticsAgent.getAgentExecution()).thenReturn(mock(AgentExecution.class));
        approvalService = new AgentConfigurationApprovalService(zeroTrustClientService, analyticsAgent);
    }

    @Test
    void testRequestChange_CreatesNewChange() {
        // Act
        AgentConfigurationChange change = approvalService.requestChange(
            AgentConfigurationChange.ChangeType.ENABLE_LLM_GUIDANCE,
            "llm.enabled",
            "false",
            "true",
            "admin",
            "Enable LLM guidance for better scheduling"
        );

        // Assert
        assertNotNull(change);
        assertNotNull(change.getChangeId());
        assertEquals(AgentConfigurationChange.ChangeStatus.PENDING_APPROVAL, change.getStatus());
        assertEquals("admin", change.getRequestedBy());
        assertEquals("llm.enabled", change.getConfigurationKey());
        assertEquals("true", change.getNewValue());
    }

    @Test
    void testGetPendingChanges_ReturnsRequestedChange() {
        // Arrange
        AgentConfigurationChange change = approvalService.requestChange(
            AgentConfigurationChange.ChangeType.ENABLE_LLM_GUIDANCE,
            "llm.enabled",
            "false",
            "true",
            "admin",
            "Test"
        );

        // Act
        var pending = approvalService.getPendingChanges();

        // Assert
        assertTrue(pending.containsKey(change.getChangeId()));
        assertEquals(change, pending.get(change.getChangeId()));
    }

    @Test
    void testApproveChange_RequiresDifferentApprover() throws ZtatException {
        // Arrange
        AgentConfigurationChange change = approvalService.requestChange(
            AgentConfigurationChange.ChangeType.ENABLE_LLM_GUIDANCE,
            "llm.enabled",
            "false",
            "true",
            "admin",
            "Test"
        );

        // Act & Assert
        assertThrows(IllegalArgumentException.class, () -> {
            approvalService.approveChange(change.getChangeId(), "admin", "valid-token");
        });
    }

    @Test
    void testRejectChange_UpdatesStatus() {
        // Arrange
        AgentConfigurationChange change = approvalService.requestChange(
            AgentConfigurationChange.ChangeType.ENABLE_LLM_GUIDANCE,
            "llm.enabled",
            "false",
            "true",
            "admin",
            "Test"
        );

        // Act
        AgentConfigurationChange rejected = approvalService.rejectChange(change.getChangeId(), "supervisor");

        // Assert
        assertEquals(AgentConfigurationChange.ChangeStatus.REJECTED, rejected.getStatus());
        assertEquals("supervisor", rejected.getApprovedBy());
        assertNotNull(rejected.getApprovedAt());
    }
}
