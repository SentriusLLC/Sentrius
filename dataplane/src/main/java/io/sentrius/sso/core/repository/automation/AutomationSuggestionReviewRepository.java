package io.sentrius.sso.core.repository.automation;

import io.sentrius.sso.core.model.automation.AutomationSuggestionReview;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface AutomationSuggestionReviewRepository extends JpaRepository<AutomationSuggestionReview, Long> {
    
    /**
     * Find all reviews for a specific suggestion
     */
    List<AutomationSuggestionReview> findBySuggestionId(Long suggestionId);
    
    /**
     * Find all reviews by a specific user
     */
    List<AutomationSuggestionReview> findByReviewedById(Long userId);
    
    /**
     * Find reviews with a specific decision
     */
    List<AutomationSuggestionReview> findByDecision(String decision);
}
