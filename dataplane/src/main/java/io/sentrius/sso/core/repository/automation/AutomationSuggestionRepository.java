package io.sentrius.sso.core.repository.automation;

import io.sentrius.sso.core.model.automation.AutomationSuggestion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface AutomationSuggestionRepository extends JpaRepository<AutomationSuggestion, Long> {
    
    /**
     * Find all suggestions with a specific status
     */
    List<AutomationSuggestion> findByStatus(String status);
    
    /**
     * Find suggestions for a specific user
     */
    List<AutomationSuggestion> findBySuggestedForUserId(Long userId);
    
    /**
     * Find suggestions for a specific target system
     */
    List<AutomationSuggestion> findByTargetSystem(String targetSystem);
    
    /**
     * Find suggestions with confidence score above a threshold
     */
    @Query("SELECT s FROM AutomationSuggestion s WHERE s.confidenceScore >= :minConfidence ORDER BY s.confidenceScore DESC")
    List<AutomationSuggestion> findByConfidenceScoreGreaterThanEqual(@Param("minConfidence") Double minConfidence);
    
    /**
     * Find pending suggestions ordered by confidence
     */
    @Query("SELECT s FROM AutomationSuggestion s WHERE s.status = 'PENDING' ORDER BY s.confidenceScore DESC, s.patternFrequency DESC")
    List<AutomationSuggestion> findPendingSuggestionsOrderedByConfidence();
}
