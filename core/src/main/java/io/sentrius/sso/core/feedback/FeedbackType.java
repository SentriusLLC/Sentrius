package io.sentrius.sso.core.feedback;

/**
 * Types of feedback that can be provided for agent behavior.
 * Used in Reinforcement Learning from Human Feedback (RLHF) system.
 */
public enum FeedbackType {
    /**
     * Positive feedback - agent behavior should be reinforced
     */
    POSITIVE,
    
    /**
     * Negative feedback - agent behavior should be discouraged
     */
    NEGATIVE,
    
    /**
     * Neutral feedback - informational, no behavioral change
     */
    NEUTRAL,
    
    /**
     * Corrective feedback - provides specific guidance for improvement
     */
    CORRECTIVE
}
