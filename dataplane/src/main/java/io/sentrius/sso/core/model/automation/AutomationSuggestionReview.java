package io.sentrius.sso.core.model.automation;

import io.sentrius.sso.core.model.users.User;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.sql.Timestamp;

/**
 * Entity representing a human review of an automation suggestion.
 * Tracks who reviewed the suggestion, when, and what decision was made.
 */
@Entity
@Table(name = "automation_suggestion_reviews")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AutomationSuggestionReview {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    /**
     * The suggestion being reviewed
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "suggestion_id", nullable = false)
    private AutomationSuggestion suggestion;
    
    /**
     * User who performed the review
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reviewed_by_user_id", nullable = false)
    private User reviewedBy;
    
    /**
     * Review decision: APPROVED, REJECTED, NEEDS_MODIFICATION
     */
    @Column(name = "decision", nullable = false)
    private String decision;
    
    /**
     * Comments from the reviewer
     */
    @Column(name = "review_comments", columnDefinition = "TEXT")
    private String reviewComments;
    
    /**
     * When the review was performed
     */
    @Column(name = "reviewed_at", columnDefinition = "TIMESTAMP DEFAULT CURRENT_TIMESTAMP")
    private Timestamp reviewedAt;
    
    /**
     * Modified script if reviewer made changes
     */
    @Column(name = "modified_script", columnDefinition = "TEXT")
    private String modifiedScript;
    
    @PrePersist
    protected void onCreate() {
        reviewedAt = new Timestamp(System.currentTimeMillis());
    }
}
