package io.sentrius.sso.core.services.agents;

import io.sentrius.sso.core.model.agents.AgentExecutionAudit;
import io.sentrius.sso.core.repository.AgentExecutionAuditRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AgentExecutionAuditServiceTest {

    @Mock
    private AgentExecutionAuditRepository repository;

    @InjectMocks
    private AgentExecutionAuditService service;

    private AgentExecutionAudit testAudit;

    @BeforeEach
    void setUp() {
        testAudit = new AgentExecutionAudit();
        testAudit.setId(UUID.randomUUID());
        testAudit.setAgentId("test-agent-pod-123");
        testAudit.setExecutionId("exec-uuid-456");
        testAudit.setAgentType("chat-helper");
        testAudit.setExecutedBy("test@example.com");
        testAudit.setStatus("RUNNING");
        testAudit.setStartTime(Instant.now());
    }

    @Test
    void testCreateAudit() {
        // Arrange
        when(repository.save(any(AgentExecutionAudit.class))).thenReturn(testAudit);

        // Act
        AgentExecutionAudit result = service.createAudit(
            "test-agent-pod-123",
            "exec-uuid-456",
            "chat-helper",
            "test@example.com"
        );

        // Assert
        assertNotNull(result);
        assertEquals("test-agent-pod-123", result.getAgentId());
        assertEquals("exec-uuid-456", result.getExecutionId());
        assertEquals("chat-helper", result.getAgentType());
        assertEquals("test@example.com", result.getExecutedBy());
        assertEquals("RUNNING", result.getStatus());
        verify(repository, times(1)).save(any(AgentExecutionAudit.class));
    }

    @Test
    void testUpdateAuditCompletion() {
        // Arrange
        String summary = "Agent executed successfully and processed user requests.";
        String resourceLinks = "[{\"type\":\"issue\",\"url\":\"https://github.com/test/repo/issues/1\",\"label\":\"Issue #1\"}]";
        
        when(repository.findByExecutionId("exec-uuid-456")).thenReturn(Optional.of(testAudit));
        when(repository.save(any(AgentExecutionAudit.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // Act
        AgentExecutionAudit result = service.updateAuditCompletion(
            "exec-uuid-456",
            "COMPLETED",
            summary,
            resourceLinks,
            0
        );

        // Assert
        assertNotNull(result);
        assertEquals("COMPLETED", result.getStatus());
        assertEquals(summary, result.getSummary());
        assertEquals(resourceLinks, result.getResourceLinks());
        assertEquals(0, result.getExitCode());
        assertNotNull(result.getEndTime());
        assertNotNull(result.getDurationMs());
        verify(repository, times(1)).findByExecutionId("exec-uuid-456");
        verify(repository, times(1)).save(any(AgentExecutionAudit.class));
    }

    @Test
    void testUpdateAuditCompletionNotFound() {
        // Arrange
        when(repository.findByExecutionId("nonexistent")).thenReturn(Optional.empty());

        // Act
        AgentExecutionAudit result = service.updateAuditCompletion(
            "nonexistent",
            "COMPLETED",
            "summary",
            null,
            0
        );

        // Assert
        assertNull(result);
        verify(repository, times(1)).findByExecutionId("nonexistent");
        verify(repository, never()).save(any(AgentExecutionAudit.class));
    }

    @Test
    void testUpdatePodLogs() {
        // Arrange
        String podLogs = "Agent started\nProcessing...\nCompleted successfully";
        when(repository.findByExecutionId("exec-uuid-456")).thenReturn(Optional.of(testAudit));
        when(repository.save(any(AgentExecutionAudit.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // Act
        service.updatePodLogs("exec-uuid-456", podLogs);

        // Assert
        verify(repository, times(1)).findByExecutionId("exec-uuid-456");
        verify(repository, times(1)).save(any(AgentExecutionAudit.class));
    }

    @Test
    void testGetAllAudits() {
        // Arrange
        List<AgentExecutionAudit> audits = Arrays.asList(testAudit);
        when(repository.findAllOrderByStartTimeDesc()).thenReturn(audits);

        // Act
        List<AgentExecutionAudit> result = service.getAllAudits();

        // Assert
        assertNotNull(result);
        assertEquals(1, result.size());
        assertEquals(testAudit, result.get(0));
        verify(repository, times(1)).findAllOrderByStartTimeDesc();
    }

    @Test
    void testGetAuditByExecutionId() {
        // Arrange
        when(repository.findByExecutionId("exec-uuid-456")).thenReturn(Optional.of(testAudit));

        // Act
        Optional<AgentExecutionAudit> result = service.getAuditByExecutionId("exec-uuid-456");

        // Assert
        assertTrue(result.isPresent());
        assertEquals(testAudit, result.get());
        verify(repository, times(1)).findByExecutionId("exec-uuid-456");
    }

    @Test
    void testGetAuditsByAgentType() {
        // Arrange
        List<AgentExecutionAudit> audits = Arrays.asList(testAudit);
        when(repository.findByAgentTypeOrderByStartTimeDesc("chat-helper")).thenReturn(audits);

        // Act
        List<AgentExecutionAudit> result = service.getAuditsByAgentType("chat-helper");

        // Assert
        assertNotNull(result);
        assertEquals(1, result.size());
        assertEquals(testAudit, result.get(0));
        verify(repository, times(1)).findByAgentTypeOrderByStartTimeDesc("chat-helper");
    }

    @Test
    void testGetAuditsByStatus() {
        // Arrange
        List<AgentExecutionAudit> audits = Arrays.asList(testAudit);
        when(repository.findByStatusOrderByStartTimeDesc("RUNNING")).thenReturn(audits);

        // Act
        List<AgentExecutionAudit> result = service.getAuditsByStatus("RUNNING");

        // Assert
        assertNotNull(result);
        assertEquals(1, result.size());
        assertEquals("RUNNING", result.get(0).getStatus());
        verify(repository, times(1)).findByStatusOrderByStartTimeDesc("RUNNING");
    }

    @Test
    void testCalculateDuration() {
        // Arrange
        Instant start = Instant.parse("2026-01-22T10:00:00Z");
        Instant end = Instant.parse("2026-01-22T10:05:30Z");
        
        testAudit.setStartTime(start);
        testAudit.setEndTime(end);

        // Act
        testAudit.calculateDuration();

        // Assert
        assertNotNull(testAudit.getDurationMs());
        assertEquals(330000L, testAudit.getDurationMs()); // 5 minutes 30 seconds = 330000 ms
    }
}
