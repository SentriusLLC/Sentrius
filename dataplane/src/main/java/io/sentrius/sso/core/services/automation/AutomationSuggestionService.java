package io.sentrius.sso.core.services.automation;

import io.sentrius.sso.core.model.automation.Automation;
import io.sentrius.sso.core.model.automation.AutomationSuggestion;
import io.sentrius.sso.core.model.automation.AutomationSuggestionReview;
import io.sentrius.sso.core.model.users.User;
import io.sentrius.sso.core.repository.automation.AutomationSuggestionRepository;
import io.sentrius.sso.core.repository.automation.AutomationSuggestionReviewRepository;
import io.sentrius.sso.core.repository.automation.ScriptRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.util.List;
import java.util.Optional;

/**
 * Service for managing automation suggestions and their lifecycle
 */
@Slf4j
@Service
@RequiredArgsConstructor
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public class AutomationSuggestionService {
    
    private final AutomationSuggestionRepository suggestionRepository;
    private final AutomationSuggestionReviewRepository reviewRepository;
    private final ScriptRepository scriptRepository;
    
    /**
     * Create a new automation suggestion
     */
    @Transactional
    public AutomationSuggestion createSuggestion(AutomationSuggestion suggestion) {
        log.info("Creating new automation suggestion: {}", suggestion.getDescription());
        return suggestionRepository.save(suggestion);
    }
    
    /**
     * Get a suggestion by ID
     */
    @Transactional(readOnly = true)
    public Optional<AutomationSuggestion> getSuggestionById(Long id) {
        return suggestionRepository.findById(id);
    }
    
    /**
     * Get all pending suggestions
     */
    @Transactional(readOnly = true)
    public List<AutomationSuggestion> getPendingSuggestions() {
        return suggestionRepository.findPendingSuggestionsOrderedByConfidence();
    }
    
    /**
     * Get suggestions for a specific user
     */
    @Transactional(readOnly = true)
    public List<AutomationSuggestion> getSuggestionsForUser(Long userId) {
        return suggestionRepository.findBySuggestedForUserId(userId);
    }
    
    /**
     * Get suggestions for a specific target system
     */
    @Transactional(readOnly = true)
    public List<AutomationSuggestion> getSuggestionsForSystem(String targetSystem) {
        return suggestionRepository.findByTargetSystem(targetSystem);
    }
    
    /**
     * Get high-confidence suggestions
     */
    @Transactional(readOnly = true)
    public List<AutomationSuggestion> getHighConfidenceSuggestions(double minConfidence) {
        return suggestionRepository.findByConfidenceScoreGreaterThanEqual(minConfidence);
    }
    
    /**
     * Review a suggestion
     */
    @Transactional
    public AutomationSuggestionReview reviewSuggestion(
            Long suggestionId, 
            User reviewer, 
            String decision, 
            String comments,
            String modifiedScript) {
        
        AutomationSuggestion suggestion = suggestionRepository.findById(suggestionId)
            .orElseThrow(() -> new IllegalArgumentException("Suggestion not found: " + suggestionId));
        
        AutomationSuggestionReview review = AutomationSuggestionReview.builder()
            .suggestion(suggestion)
            .reviewedBy(reviewer)
            .decision(decision)
            .reviewComments(comments)
            .modifiedScript(modifiedScript)
            .build();
        
        review = reviewRepository.save(review);
        
        // Update suggestion status based on decision
        if ("APPROVED".equals(decision)) {
            suggestion.setStatus("APPROVED");
        } else if ("REJECTED".equals(decision)) {
            suggestion.setStatus("REJECTED");
        }
        suggestion.setUpdatedAt(new Timestamp(System.currentTimeMillis()));
        suggestionRepository.save(suggestion);
        
        log.info("Suggestion {} reviewed by {} with decision: {}", suggestionId, reviewer.getUsername(), decision);
        
        return review;
    }
    
    /**
     * Convert an approved suggestion to an actual Automation
     */
    @Transactional
    public Automation convertToAutomation(Long suggestionId, User creator) {
        AutomationSuggestion suggestion = suggestionRepository.findById(suggestionId)
            .orElseThrow(() -> new IllegalArgumentException("Suggestion not found: " + suggestionId));
        
        if (!"APPROVED".equals(suggestion.getStatus())) {
            throw new IllegalStateException("Only approved suggestions can be converted to automation");
        }
        
        // Check if there's a modified script from review
        String scriptToUse = suggestion.getSuggestedScript();
        List<AutomationSuggestionReview> reviews = reviewRepository.findBySuggestionId(suggestionId);
        for (AutomationSuggestionReview review : reviews) {
            if ("APPROVED".equals(review.getDecision()) && review.getModifiedScript() != null) {
                scriptToUse = review.getModifiedScript();
                break;
            }
        }
        
        Automation automation = new Automation();
        automation.setUser(creator);
        automation.setType(suggestion.getScriptType());
        automation.setDisplayName("Auto: " + suggestion.getDescription());
        automation.setScript(scriptToUse);
        automation.setDescription("Automatically generated from suggestion #" + suggestionId + ": " + suggestion.getDescription());
        
        automation = scriptRepository.save(automation);
        
        // Update suggestion to mark it as converted
        suggestion.setStatus("CONVERTED");
        suggestion.setAutomation(automation);
        suggestion.setUpdatedAt(new Timestamp(System.currentTimeMillis()));
        suggestionRepository.save(suggestion);
        
        log.info("Converted suggestion {} to automation {}", suggestionId, automation.getId());
        
        return automation;
    }
    
    /**
     * Delete a suggestion
     */
    @Transactional
    public void deleteSuggestion(Long suggestionId) {
        suggestionRepository.deleteById(suggestionId);
        log.info("Deleted suggestion {}", suggestionId);
    }
    
    /**
     * Update suggestion status
     */
    @Transactional
    public void updateStatus(Long suggestionId, String newStatus) {
        AutomationSuggestion suggestion = suggestionRepository.findById(suggestionId)
            .orElseThrow(() -> new IllegalArgumentException("Suggestion not found: " + suggestionId));
        
        suggestion.setStatus(newStatus);
        suggestion.setUpdatedAt(new Timestamp(System.currentTimeMillis()));
        suggestionRepository.save(suggestion);
        
        log.info("Updated suggestion {} status to {}", suggestionId, newStatus);
    }
}
