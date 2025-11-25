package io.sentrius.sso.core.services.feedback;

import io.sentrius.sso.core.dto.feedback.AgentFeedbackDTO;
import io.sentrius.sso.core.dto.feedback.FeedbackSubmissionDTO;
import io.sentrius.sso.core.feedback.FeedbackType;
import io.sentrius.sso.core.model.feedback.AgentFeedback;
import io.sentrius.sso.core.repository.feedback.AgentFeedbackRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Service for managing agent feedback (CRUD operations).
 * Provides interface for submitting, retrieving, and managing feedback.
 */
@Service
@Slf4j
public class AgentFeedbackService {
    
    private final AgentFeedbackRepository feedbackRepository;
    
    public AgentFeedbackService(AgentFeedbackRepository feedbackRepository) {
        this.feedbackRepository = feedbackRepository;
    }
    
    @Transactional
    public AgentFeedbackDTO submitFeedback(FeedbackSubmissionDTO submission, String providedBy) {
        log.info("Submitting feedback for agent {}: type={}", 
            submission.getAgentId(), submission.getFeedbackType());
        
        AgentFeedback feedback = AgentFeedback.builder()
            .agentId(submission.getAgentId())
            .feedbackType(submission.getFeedbackType())
            .feedbackText(submission.getFeedbackText())
            .context(submission.getContext())
            .actionId(submission.getActionId())
            .behaviorCategory(submission.getBehaviorCategory())
            .providedBy(providedBy)
            .timestamp(LocalDateTime.now())
            .processed(false)
            .build();
        
        AgentFeedback saved = feedbackRepository.save(feedback);
        log.info("Feedback submitted successfully: id={}, agentId={}", 
            saved.getId(), saved.getAgentId());
        
        return toDTO(saved);
    }
    
    public List<AgentFeedbackDTO> getFeedbackForAgent(String agentId) {
        return feedbackRepository.findByAgentIdOrderByTimestampDesc(agentId)
            .stream()
            .map(this::toDTO)
            .collect(Collectors.toList());
    }
    
    public Page<AgentFeedbackDTO> getFeedbackForAgent(String agentId, Pageable pageable) {
        return feedbackRepository.findByAgentIdOrderByTimestampDesc(agentId, pageable)
            .map(this::toDTO);
    }
    
    public List<AgentFeedbackDTO> getFeedbackInRange(
            String agentId, LocalDateTime start, LocalDateTime end) {
        return feedbackRepository.findByAgentIdAndTimestampBetweenOrderByTimestampDesc(agentId, start, end)
            .stream()
            .map(this::toDTO)
            .collect(Collectors.toList());
    }
    
    public List<AgentFeedbackDTO> getFeedbackByType(String agentId, FeedbackType type) {
        return feedbackRepository.findByAgentIdAndFeedbackTypeOrderByTimestampDesc(agentId, type)
            .stream()
            .map(this::toDTO)
            .collect(Collectors.toList());
    }
    
    public List<AgentFeedbackDTO> getUnprocessedFeedback() {
        return feedbackRepository.findByProcessedOrderByTimestampAsc(false)
            .stream()
            .map(this::toDTO)
            .collect(Collectors.toList());
    }
    
    public List<AgentFeedbackDTO> getRecentFeedback(LocalDateTime since) {
        return feedbackRepository.findRecentFeedback(since)
            .stream()
            .map(this::toDTO)
            .collect(Collectors.toList());
    }
    
    public List<String> getAllAgentsWithFeedback() {
        return feedbackRepository.findDistinctAgentIds();
    }
    
    public Double getAverageReinforcementWeight(String agentId, LocalDateTime since) {
        return feedbackRepository.getAverageReinforcementWeight(agentId, since);
    }
    
    public Long countFeedbackByType(String agentId, FeedbackType type, LocalDateTime since) {
        return feedbackRepository.countByAgentIdAndTypeAndSince(agentId, type, since);
    }
    
    public List<AgentFeedbackDTO> getFeedbackByCategory(String agentId, String category) {
        return feedbackRepository.findByAgentIdAndBehaviorCategory(agentId, category)
            .stream()
            .map(this::toDTO)
            .collect(Collectors.toList());
    }
    
    @Transactional
    public void markAsProcessed(Long feedbackId, Integer trustImpact) {
        Optional<AgentFeedback> feedbackOpt = feedbackRepository.findById(feedbackId);
        if (feedbackOpt.isPresent()) {
            AgentFeedback feedback = feedbackOpt.get();
            feedback.setProcessed(true);
            feedback.setTrustImpact(trustImpact);
            feedbackRepository.save(feedback);
            log.debug("Marked feedback {} as processed with trust impact {}", 
                feedbackId, trustImpact);
        }
    }
    
    @Transactional
    public boolean deleteFeedback(Long feedbackId, String requestingUser) {
        Optional<AgentFeedback> feedbackOpt = feedbackRepository.findById(feedbackId);
        if (feedbackOpt.isPresent()) {
            AgentFeedback feedback = feedbackOpt.get();
            log.info("Deleting feedback {}: agentId={}, type={}, requestedBy={}", 
                feedbackId, feedback.getAgentId(), feedback.getFeedbackType(), requestingUser);
            feedbackRepository.delete(feedback);
            return true;
        }
        return false;
    }
    
    private AgentFeedbackDTO toDTO(AgentFeedback entity) {
        return AgentFeedbackDTO.builder()
            .id(entity.getId())
            .agentId(entity.getAgentId())
            .agentName(entity.getAgentName())
            .feedbackType(entity.getFeedbackType())
            .feedbackText(entity.getFeedbackText())
            .context(entity.getContext())
            .actionId(entity.getActionId())
            .trustImpact(entity.getTrustImpact())
            .providedBy(entity.getProvidedBy())
            .timestamp(entity.getTimestamp())
            .processed(entity.getProcessed())
            .behaviorCategory(entity.getBehaviorCategory())
            .reinforcementWeight(entity.getReinforcementWeight())
            .build();
    }
}
