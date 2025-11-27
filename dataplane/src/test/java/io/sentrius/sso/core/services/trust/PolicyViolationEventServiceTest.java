package io.sentrius.sso.core.services.trust;

import io.sentrius.sso.core.model.trust.PolicyViolationEvent;
import io.sentrius.sso.core.model.trust.PolicyViolationEventType;
import io.sentrius.sso.core.repository.trust.PolicyViolationEventRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class PolicyViolationEventServiceTest {
    
    @Mock
    private PolicyViolationEventRepository repository;
    
    @InjectMocks
    private PolicyViolationEventService service;
    
    private PolicyViolationEvent testApprovalEvent;
    private PolicyViolationEvent testDenialEvent;
    
    @BeforeEach
    void setUp() {
        testApprovalEvent = PolicyViolationEvent.builder()
            .id(1L)
            .entityId("agent-123")
            .entityName("Test Agent")
            .eventType(PolicyViolationEventType.ZTAT_REQUEST_APPROVED)
            .approved(true)
            .endpoint("/api/sensitive/data")
            .policyId("policy-1")
            .approverId("admin-user")
            .ztatRequestId(100L)
            .description("Test approval event")
            .timestamp(LocalDateTime.now())
            .build();
        
        testDenialEvent = PolicyViolationEvent.builder()
            .id(2L)
            .entityId("agent-123")
            .entityName("Test Agent")
            .eventType(PolicyViolationEventType.ZTAT_REQUEST_DENIED)
            .approved(false)
            .endpoint("/api/forbidden/action")
            .policyId("policy-1")
            .approverId("admin-user")
            .ztatRequestId(101L)
            .description("Test denial event")
            .timestamp(LocalDateTime.now())
            .build();
    }
    
    @Test
    void testRecordZtatApproval() {
        when(repository.save(any(PolicyViolationEvent.class))).thenReturn(testApprovalEvent);
        
        PolicyViolationEvent result = service.recordZtatApproval(
            "agent-123",
            "Test Agent",
            "/api/sensitive/data",
            "policy-1",
            "admin-user",
            100L,
            "Test approval event"
        );
        
        assertNotNull(result);
        assertEquals("agent-123", result.getEntityId());
        assertTrue(result.getApproved());
        assertEquals(PolicyViolationEventType.ZTAT_REQUEST_APPROVED, result.getEventType());
        verify(repository, times(1)).save(any(PolicyViolationEvent.class));
    }
    
    @Test
    void testRecordZtatDenial() {
        when(repository.save(any(PolicyViolationEvent.class))).thenReturn(testDenialEvent);
        
        PolicyViolationEvent result = service.recordZtatDenial(
            "agent-123",
            "Test Agent",
            "/api/forbidden/action",
            "policy-1",
            "admin-user",
            101L,
            "Test denial event"
        );
        
        assertNotNull(result);
        assertEquals("agent-123", result.getEntityId());
        assertFalse(result.getApproved());
        assertEquals(PolicyViolationEventType.ZTAT_REQUEST_DENIED, result.getEventType());
        verify(repository, times(1)).save(any(PolicyViolationEvent.class));
    }
    
    @Test
    void testRecordOpsJitApproval() {
        PolicyViolationEvent opsApprovalEvent = PolicyViolationEvent.builder()
            .id(3L)
            .entityId("agent-456")
            .entityName("OPS Agent")
            .eventType(PolicyViolationEventType.OPS_JIT_APPROVED)
            .approved(true)
            .endpoint("/ops/command")
            .policyId("ops-policy")
            .approverId("ops-admin")
            .ztatRequestId(200L)
            .description("OPS JIT approval")
            .timestamp(LocalDateTime.now())
            .build();
        
        when(repository.save(any(PolicyViolationEvent.class))).thenReturn(opsApprovalEvent);
        
        PolicyViolationEvent result = service.recordOpsJitApproval(
            "agent-456",
            "OPS Agent",
            "/ops/command",
            "ops-policy",
            "ops-admin",
            200L,
            "OPS JIT approval"
        );
        
        assertNotNull(result);
        assertEquals(PolicyViolationEventType.OPS_JIT_APPROVED, result.getEventType());
        assertTrue(result.getApproved());
    }
    
    @Test
    void testRecordOpsJitDenial() {
        PolicyViolationEvent opsDenialEvent = PolicyViolationEvent.builder()
            .id(4L)
            .entityId("agent-456")
            .entityName("OPS Agent")
            .eventType(PolicyViolationEventType.OPS_JIT_DENIED)
            .approved(false)
            .endpoint("/ops/forbidden")
            .policyId("ops-policy")
            .approverId("ops-admin")
            .ztatRequestId(201L)
            .description("OPS JIT denial")
            .timestamp(LocalDateTime.now())
            .build();
        
        when(repository.save(any(PolicyViolationEvent.class))).thenReturn(opsDenialEvent);
        
        PolicyViolationEvent result = service.recordOpsJitDenial(
            "agent-456",
            "OPS Agent",
            "/ops/forbidden",
            "ops-policy",
            "ops-admin",
            201L,
            "OPS JIT denial"
        );
        
        assertNotNull(result);
        assertEquals(PolicyViolationEventType.OPS_JIT_DENIED, result.getEventType());
        assertFalse(result.getApproved());
    }
    
    @Test
    void testGetIncidentCount() {
        when(repository.countDeniedViolations(eq("agent-123"), any(LocalDateTime.class)))
            .thenReturn(3L);
        
        int incidentCount = service.getIncidentCount("agent-123");
        
        assertEquals(3, incidentCount);
    }
    
    @Test
    void testGetApprovedViolationCount() {
        when(repository.countApprovedViolations(eq("agent-123"), any(LocalDateTime.class)))
            .thenReturn(5L);
        
        int approvedCount = service.getApprovedViolationCount("agent-123");
        
        assertEquals(5, approvedCount);
    }
    
    @Test
    void testGetTotalViolationCount() {
        when(repository.countAllViolations(eq("agent-123"), any(LocalDateTime.class)))
            .thenReturn(8L);
        
        int totalCount = service.getTotalViolationCount("agent-123");
        
        assertEquals(8, totalCount);
    }
    
    @Test
    void testGetViolationHistory() {
        List<PolicyViolationEvent> events = Arrays.asList(testDenialEvent, testApprovalEvent);
        when(repository.findByEntityIdOrderByTimestampDesc("agent-123")).thenReturn(events);
        
        List<PolicyViolationEvent> result = service.getViolationHistory("agent-123");
        
        assertNotNull(result);
        assertEquals(2, result.size());
    }
    
    @Test
    void testGetViolationHistoryWithTimeRange() {
        LocalDateTime start = LocalDateTime.now().minusDays(7);
        LocalDateTime end = LocalDateTime.now();
        List<PolicyViolationEvent> events = Arrays.asList(testDenialEvent);
        
        when(repository.findByEntityIdAndTimestampBetweenOrderByTimestampDesc(
            "agent-123", start, end)).thenReturn(events);
        
        List<PolicyViolationEvent> result = service.getViolationHistory("agent-123", start, end);
        
        assertNotNull(result);
        assertEquals(1, result.size());
    }
    
    @Test
    void testGetRecentViolations() {
        LocalDateTime since = LocalDateTime.now().minusHours(24);
        List<PolicyViolationEvent> events = Arrays.asList(testDenialEvent, testApprovalEvent);
        
        when(repository.findRecentViolations(since)).thenReturn(events);
        
        List<PolicyViolationEvent> result = service.getRecentViolations(since);
        
        assertNotNull(result);
        assertEquals(2, result.size());
    }
    
    @Test
    void testIncidentCountWithNoViolations() {
        when(repository.countDeniedViolations(eq("new-agent"), any(LocalDateTime.class)))
            .thenReturn(0L);
        
        int incidentCount = service.getIncidentCount("new-agent");
        
        assertEquals(0, incidentCount);
    }
}
