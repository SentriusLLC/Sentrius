package io.sentrius.sso.core.services.trust;

import io.sentrius.sso.core.dto.trust.AgentTrustScoreDTO;
import io.sentrius.sso.core.model.trust.AgentTrustScoreHistory;
import io.sentrius.sso.core.repository.trust.AgentTrustScoreHistoryRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class AgentTrustScoreServiceTest {
    
    @Mock
    private AgentTrustScoreHistoryRepository repository;
    
    @InjectMocks
    private AgentTrustScoreService service;
    
    private AgentTrustScoreHistory testScore;
    
    @BeforeEach
    void setUp() {
        testScore = AgentTrustScoreHistory.builder()
            .id(1L)
            .agentId("agent-123")
            .agentName("Test Agent")
            .trustScore(85)
            .identityScore(90.0)
            .provenanceScore(80.0)
            .runtimeScore(85.0)
            .behaviorScore(85.0)
            .evaluationResult("SUCCESS")
            .policyId("policy-1")
            .timestamp(LocalDateTime.now())
            .priorRuns(10)
            .incidentCount(0)
            .enclaveVerified(true)
            .evaluationNotes("Test evaluation")
            .build();
    }
    
    @Test
    void testRecordTrustScore() {
        when(repository.save(any(AgentTrustScoreHistory.class))).thenReturn(testScore);
        
        AgentTrustScoreHistory result = service.recordTrustScore(testScore);
        
        assertNotNull(result);
        assertEquals(testScore.getAgentId(), result.getAgentId());
        assertEquals(testScore.getTrustScore(), result.getTrustScore());
        verify(repository, times(1)).save(any(AgentTrustScoreHistory.class));
    }
    
    @Test
    void testGetTrustScoreHistory() {
        List<AgentTrustScoreHistory> scores = Arrays.asList(testScore);
        when(repository.findByAgentIdOrderByTimestampDesc("agent-123")).thenReturn(scores);
        
        List<AgentTrustScoreDTO> result = service.getTrustScoreHistory("agent-123");
        
        assertNotNull(result);
        assertEquals(1, result.size());
        assertEquals(testScore.getAgentId(), result.get(0).getAgentId());
        assertEquals(testScore.getTrustScore(), result.get(0).getTrustScore());
    }
    
    @Test
    void testGetLatestTrustScore() {
        when(repository.findTopByAgentIdOrderByTimestampDesc("agent-123"))
            .thenReturn(Optional.of(testScore));
        
        Optional<AgentTrustScoreDTO> result = service.getLatestTrustScore("agent-123");
        
        assertTrue(result.isPresent());
        assertEquals(testScore.getAgentId(), result.get().getAgentId());
        assertEquals(testScore.getTrustScore(), result.get().getTrustScore());
    }
    
    @Test
    void testGetLatestTrustScoreNotFound() {
        when(repository.findTopByAgentIdOrderByTimestampDesc("unknown-agent"))
            .thenReturn(Optional.empty());
        
        Optional<AgentTrustScoreDTO> result = service.getLatestTrustScore("unknown-agent");
        
        assertFalse(result.isPresent());
    }
    
    @Test
    void testGetAverageTrustScore() {
        LocalDateTime since = LocalDateTime.now().minusDays(7);
        when(repository.getAverageTrustScore("agent-123", since)).thenReturn(82.5);
        
        Double result = service.getAverageTrustScore("agent-123", since);
        
        assertNotNull(result);
        assertEquals(82.5, result);
    }
    
    @Test
    void testGetAllAgentsWithScores() {
        List<String> agentIds = Arrays.asList("agent-123", "agent-456");
        when(repository.findDistinctAgentIds()).thenReturn(agentIds);
        
        List<String> result = service.getAllAgentsWithScores();
        
        assertNotNull(result);
        assertEquals(2, result.size());
        assertTrue(result.contains("agent-123"));
        assertTrue(result.contains("agent-456"));
    }
}
