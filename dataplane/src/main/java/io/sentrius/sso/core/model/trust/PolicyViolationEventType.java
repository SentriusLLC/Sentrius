package io.sentrius.sso.core.model.trust;

/**
 * Types of policy violation events that can be recorded.
 */
public enum PolicyViolationEventType {
    /**
     * Agent/user accessed an endpoint outside their policy and was approved
     */
    OUT_OF_POLICY_ACCESS_APPROVED,
    
    /**
     * Agent/user accessed an endpoint outside their policy and was denied
     */
    OUT_OF_POLICY_ACCESS_DENIED,
    
    /**
     * A ZTAT (Zero Trust Access Token) request was approved
     */
    ZTAT_REQUEST_APPROVED,
    
    /**
     * A ZTAT request was denied
     */
    ZTAT_REQUEST_DENIED,
    
    /**
     * An OPS JIT request was approved
     */
    OPS_JIT_APPROVED,
    
    /**
     * An OPS JIT request was denied
     */
    OPS_JIT_DENIED
}
