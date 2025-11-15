package io.sentrius.sso.core.services.trust;

import io.sentrius.sso.core.dto.trust.AgentTrustScoreDTO;
import io.sentrius.sso.core.model.trust.AgentTrustScoreHistory;
import io.sentrius.sso.core.repository.trust.AgentTrustScoreHistoryRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@Slf4j
public class AgentTrustScoreService {
    
    private final AgentTrustScoreHistoryRepository repository;
    
    public AgentTrustScoreService(AgentTrustScoreHistoryRepository repository) {
        this.repository = repository;
    }
    
    @Transactional
    public AgentTrustScoreHistory recordTrustScore(AgentTrustScoreHistory score) {
        log.debug("Recording trust score for agent {}: score={}, result={}", 
            score.getAgentId(), score.getTrustScore(), score.getEvaluationResult());
        return repository.save(score);
    }
    
    public List<AgentTrustScoreDTO> getTrustScoreHistory(String agentId) {
        return repository.findByAgentIdOrderByTimestampDesc(agentId)
            .stream()
            .map(this::toDTO)
            .collect(Collectors.toList());
    }
    
    public Page<AgentTrustScoreDTO> getTrustScoreHistory(String agentId, Pageable pageable) {
        return repository.findByAgentIdOrderByTimestampDesc(agentId, pageable)
            .map(this::toDTO);
    }
    
    public List<AgentTrustScoreDTO> getTrustScoreHistoryInRange(
            String agentId, LocalDateTime start, LocalDateTime end) {
        return repository.findByAgentIdAndTimestampBetweenOrderByTimestampDesc(agentId, start, end)
            .stream()
            .map(this::toDTO)
            .collect(Collectors.toList());
    }
    
    public Optional<AgentTrustScoreDTO> getLatestTrustScore(String agentId) {
        return repository.findTopByAgentIdOrderByTimestampDesc(agentId)
            .map(this::toDTO);
    }
    
    public List<AgentTrustScoreDTO> getRecentScores(LocalDateTime since) {
        return repository.findRecentScores(since)
            .stream()
            .map(this::toDTO)
            .collect(Collectors.toList());
    }
    
    public List<String> getAllAgentsWithScores() {
        return repository.findDistinctAgentIds();
    }
    
    public Double getAverageTrustScore(String agentId, LocalDateTime since) {
        return repository.getAverageTrustScore(agentId, since);
    }
    
    private AgentTrustScoreDTO toDTO(AgentTrustScoreHistory entity) {
        return AgentTrustScoreDTO.builder()
            .id(entity.getId())
            .agentId(entity.getAgentId())
            .agentName(entity.getAgentName())
            .trustScore(entity.getTrustScore())
            .identityScore(entity.getIdentityScore())
            .provenanceScore(entity.getProvenanceScore())
            .runtimeScore(entity.getRuntimeScore())
            .behaviorScore(entity.getBehaviorScore())
            .evaluationResult(entity.getEvaluationResult())
            .policyId(entity.getPolicyId())
            .timestamp(entity.getTimestamp())
            .priorRuns(entity.getPriorRuns())
            .incidentCount(entity.getIncidentCount())
            .enclaveVerified(entity.getEnclaveVerified())
            .evaluationNotes(entity.getEvaluationNotes())
            .build();
    }
}
