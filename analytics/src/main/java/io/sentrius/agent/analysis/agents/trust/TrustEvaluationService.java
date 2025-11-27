package io.sentrius.agent.analysis.agents.trust;

import io.sentrius.agent.analysis.service.LLMGuidedSchedulerService;
import io.sentrius.sso.core.model.AgentHeartbeat;
import io.sentrius.sso.core.model.sessions.SessionLog;
import io.sentrius.sso.core.model.trust.AgentTrustScoreHistory;
import io.sentrius.sso.core.model.users.User;
import io.sentrius.sso.core.model.security.enums.IdentityType;
import io.sentrius.sso.core.repository.AgentCommunicationRepository;
import io.sentrius.sso.core.repository.AgentHeartbeatRepository;
import io.sentrius.sso.core.repository.SessionLogRepository;
import io.sentrius.sso.core.services.ATPLPolicyService;
import io.sentrius.sso.core.services.UserService;
import io.sentrius.sso.core.services.trust.AgentTrustScoreService;
import io.sentrius.sso.core.services.trust.PolicyViolationEventService;
import io.sentrius.sso.core.trust.*;
import io.sentrius.sso.provenance.ProvenanceEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Service
@Slf4j
@ConditionalOnProperty(name = "sentrius.trust.evaluation.enabled", havingValue = "true", matchIfMissing = true)
public class TrustEvaluationService {
    
    private final AgentHeartbeatRepository heartbeatRepository;
    private final AgentCommunicationRepository communicationRepository;
    private final SessionLogRepository sessionLogRepository;
    private final AgentTrustScoreService trustScoreService;
    private final ATPLPolicyService atplPolicyService;
    private final UserService userService;
    private final LLMGuidedSchedulerService llmScheduler;
    private final io.sentrius.sso.core.services.feedback.RLHFFeedbackService rlhfFeedbackService;
    private final PolicyViolationEventService policyViolationEventService;
    
    private final Map<String, List<ProvenanceEvent>> provenanceCache = new ConcurrentHashMap<>();
    private final Map<String, Integer> incidentTracker = new ConcurrentHashMap<>();
    
    @Autowired
    public TrustEvaluationService(
            AgentHeartbeatRepository heartbeatRepository,
            AgentCommunicationRepository communicationRepository,
            SessionLogRepository sessionLogRepository,
            AgentTrustScoreService trustScoreService,
            ATPLPolicyService atplPolicyService,
            UserService userService,
            @Autowired(required = false) LLMGuidedSchedulerService llmScheduler,
            @Autowired(required = false) io.sentrius.sso.core.services.feedback.RLHFFeedbackService rlhfFeedbackService,
            @Autowired(required = false) PolicyViolationEventService policyViolationEventService) {
        this.heartbeatRepository = heartbeatRepository;
        this.communicationRepository = communicationRepository;
        this.sessionLogRepository = sessionLogRepository;
        this.trustScoreService = trustScoreService;
        this.atplPolicyService = atplPolicyService;
        this.userService = userService;
        this.llmScheduler = llmScheduler;
        this.rlhfFeedbackService = rlhfFeedbackService;
        this.policyViolationEventService = policyViolationEventService;
    }
    
    @Scheduled(fixedRate = 300000, initialDelay = 60000)
    public void evaluateAllAgentsAndUsers() {
        // Check with LLM if evaluation should run now
        if (llmScheduler != null && llmScheduler.isEnabled()) {
            llmScheduler.shouldRunTrustEvaluation().thenAccept(shouldRun -> {
                if (shouldRun) {
                    log.info("LLM recommended running trust evaluation");
                    performEvaluation();
                } else {
                    log.info("LLM recommended skipping trust evaluation this cycle");
                }
            }).exceptionally(ex -> {
                log.error("Error consulting LLM for trust evaluation guidance, running anyway", ex);
                performEvaluation();
                return null;
            });
        } else {
            // No LLM guidance available, run as normal
            performEvaluation();
        }
    }
    
    private void performEvaluation() {
        log.info("Starting scheduled trust evaluation for all agents and users");
        
        // Evaluate agents (NON_PERSON_ENTITY)
        List<AgentHeartbeat> activeAgents = heartbeatRepository.findAll().stream()
            .filter(hb -> hb.getLastHeartbeat() != null && 
                         hb.getLastHeartbeat().isAfter(LocalDateTime.now().minusMinutes(30)))
            .collect(Collectors.toList());
        
        log.info("Found {} active agents to evaluate", activeAgents.size());
        
        for (AgentHeartbeat heartbeat : activeAgents) {
            try {
                evaluateEntity(heartbeat.getAgentId(), heartbeat.getAgentName(), IdentityType.NON_PERSON_ENTITY);
            } catch (Exception e) {
                log.error("Error evaluating agent {}: {}", heartbeat.getAgentId(), e.getMessage(), e);
            }
        }
        
        // Evaluate human users (USER)
        List<User> humanUsers = userService.getAllUsers("USER").stream()
            .map(dto -> userService.getUserByUserid(dto.getId().toString()))
            .filter(Objects::nonNull)
            .collect(Collectors.toList());
        
        log.info("Found {} human users to evaluate", humanUsers.size());
        
        for (User user : humanUsers) {
            try {
                evaluateEntity(user.getUserId(), user.getUsername(), IdentityType.USER);
            } catch (Exception e) {
                log.error("Error evaluating user {}: {}", user.getUserId(), e.getMessage(), e);
            }
        }
    }
    
    /**
     * Evaluate trust score for a specific entity (agent or user).
     * @deprecated Use evaluateEntity instead
     */
    @Deprecated
    public AgentTrustScoreHistory evaluateAgent(String agentId, String agentName) {
        return evaluateEntity(agentId, agentName, IdentityType.NON_PERSON_ENTITY);
    }
    
    public AgentTrustScoreHistory evaluateEntity(String entityId, String entityName, IdentityType identityType) {
        log.debug("Evaluating trust score for entity: {} ({}) type: {}", entityName, entityId, identityType);
        
        User user = userService.getUserByUserid(entityId);
        if (user == null) {
            log.warn("No user found for entity ID: {}", entityId);
            return null;
        }
        
        Optional<ATPLPolicy> policyOpt = atplPolicyService.getPolicy(user);
        if (policyOpt.isEmpty()) {
            log.debug("No ATPL policy found for entity: {}", entityId);
            return null;
        }
        
        ATPLPolicy policy = policyOpt.get();
        AgentContext context = buildEntityContext(entityId, entityName, identityType);
        
        TrustScoreCalculator calculator = new TrustScoreCalculator();
        int trustScore = calculator.calculate(context, policy);
        
        TrustScoreResult result = determineResult(trustScore, policy.getTrustScore());
        
        AgentTrustScoreHistory history = AgentTrustScoreHistory.builder()
            .agentId(entityId)
            .agentName(entityName)
            .trustScore(trustScore)
            .identityScore(context.evaluateIdentity())
            .provenanceScore(context.evaluateProvenance())
            .runtimeScore(context.evaluateRuntime())
            .behaviorScore(context.evaluateBehavior())
            .feedbackScore(context.evaluateFeedback())
            .evaluationResult(result.name())
            .policyId(policy.getPolicyId())
            .timestamp(LocalDateTime.now())
            .priorRuns(context.getPriorRuns())
            .incidentCount(context.getIncidentCount())
            .enclaveVerified(context.isEnclaveVerified())
            .evaluationNotes(generateEvaluationNotes(context, trustScore, result, identityType))
            .build();
        
        AgentTrustScoreHistory saved = trustScoreService.recordTrustScore(history);
        log.info("Trust score evaluated for {} {}: score={}, result={}", 
            identityType == IdentityType.USER ? "user" : "agent", entityName, trustScore, result);
        
        return saved;
    }
    
    private AgentContext buildEntityContext(String entityId, String entityName, IdentityType identityType) {
        if (identityType == IdentityType.USER) {
            return buildHumanUserContext(entityId, entityName);
        } else {
            return buildAgentContext(entityId, entityName);
        }
    }
    
    private AgentContext buildHumanUserContext(String userId, String username) {
        // For human users, we track sessions instead of heartbeats
        List<SessionLog> userSessions = sessionLogRepository.findByUsername(username);
        
        int priorRuns = calculatePriorSessions(userSessions);
        int incidentCount = getIncidentCount(userId);
        
        // Human users are verified through Keycloak authentication
        List<ProvenanceEvent> events = provenanceCache.getOrDefault(userId, Collections.emptyList());
        String identityIssuer = "keycloak"; // Always verified for authenticated users
        
        // Enclave verification doesn't apply to human users, but we can consider
        // if they're accessing from a secure/verified location
        boolean enclaveVerified = false; // Could be enhanced with IP/location verification
        
        return AgentContext.builder()
            .agentId(userId)
            .tags(extractUserTags(username))
            .identityIssuer(identityIssuer)
            .enclaveVerified(enclaveVerified)
            .priorRuns(priorRuns)
            .incidentCount(incidentCount)
            .build();
    }
    
    private int calculatePriorSessions(List<SessionLog> sessions) {
        // Count sessions in the last 30 days
        LocalDateTime thirtyDaysAgo = LocalDateTime.now().minusDays(30);
        Timestamp thirtyDaysAgoTimestamp = Timestamp.valueOf(thirtyDaysAgo);
        
        return (int) sessions.stream()
            .filter(session -> session.getSessionTm() != null && 
                             session.getSessionTm().after(thirtyDaysAgoTimestamp))
            .count();
    }
    
    private Set<String> extractUserTags(String username) {
        Set<String> tags = new HashSet<>();
        if (username != null) {
            tags.add("human-user");
            // Could add role-based tags here if needed
        }
        return tags;
    }
    
    private AgentContext buildAgentContext(String agentId, String agentName) {
        Optional<AgentHeartbeat> heartbeatOpt = heartbeatRepository.findByAgentId(agentId);
        int priorRuns = calculatePriorRuns(agentId);
        int incidentCount = getIncidentCount(agentId);
        
        boolean enclaveVerified = heartbeatOpt
            .map(hb -> hb.getStatus() != null && hb.getStatus().contains("verified"))
            .orElse(false);
        
        List<ProvenanceEvent> events = provenanceCache.getOrDefault(agentId, Collections.emptyList());
        String identityIssuer = events.isEmpty() ? null : "keycloak";
        
        // Calculate RLHF feedback score if service is available
        Double feedbackScore = null;
        if (rlhfFeedbackService != null) {
            feedbackScore = rlhfFeedbackService.calculateFeedbackScore(agentId);
            log.debug("RLHF feedback score for agent {}: {}", agentId, feedbackScore);
        }
        
        return AgentContext.builder()
            .agentId(agentId)
            .tags(extractTags(agentName))
            .identityIssuer(identityIssuer)
            .enclaveVerified(enclaveVerified)
            .priorRuns(priorRuns)
            .incidentCount(incidentCount)
            .feedbackScore(feedbackScore)
            .build();
    }
    
    /**
     * Get the incident count for an entity from the persistent store if available,
     * otherwise fall back to the in-memory tracker.
     */
    private int getIncidentCount(String entityId) {
        // First try to get from persistent store (policy violation events)
        if (policyViolationEventService != null) {
            return policyViolationEventService.getIncidentCount(entityId);
        }
        // Fall back to in-memory tracker
        return incidentTracker.getOrDefault(entityId, 0);
    }
    
    private int calculatePriorRuns(String agentId) {
        LocalDateTime thirtyDaysAgo = LocalDateTime.now().minusDays(30);
        return (int) heartbeatRepository.findAll().stream()
            .filter(hb -> hb.getAgentId().equals(agentId))
            .filter(hb -> hb.getLastHeartbeat() != null && hb.getLastHeartbeat().isAfter(thirtyDaysAgo))
            .count();
    }
    
    private Set<String> extractTags(String agentName) {
        Set<String> tags = new HashSet<>();
        if (agentName != null) {
            if (agentName.contains("analytics")) tags.add("analytics");
            if (agentName.contains("ai")) tags.add("ai");
            if (agentName.contains("chat")) tags.add("chat");
            if (agentName.contains("monitor")) tags.add("monitor");
        }
        return tags;
    }
    
    private TrustScoreResult determineResult(int trustScore, TrustScore config) {
        if (trustScore >= config.getMinimum()) {
            return TrustScoreResult.SUCCESS;
        } else if (trustScore >= config.getMarginalThreshold()) {
            return TrustScoreResult.MARGINAL;
        } else {
            return TrustScoreResult.FAILURE;
        }
    }
    
    private String generateEvaluationNotes(AgentContext context, int score, TrustScoreResult result, IdentityType identityType) {
        StringBuilder notes = new StringBuilder();
        notes.append("Trust evaluation completed for ");
        notes.append(identityType == IdentityType.USER ? "human user" : "agent");
        notes.append(". ");
        notes.append("Identity: ").append(context.getIdentityIssuer() != null ? "verified" : "unverified").append(". ");
        if (identityType != IdentityType.USER) {
            notes.append("Enclave: ").append(context.isEnclaveVerified() ? "verified" : "not verified").append(". ");
        }
        notes.append("Prior ").append(identityType == IdentityType.USER ? "sessions" : "runs").append(": ").append(context.getPriorRuns()).append(". ");
        notes.append("Incidents: ").append(context.getIncidentCount()).append(".");
        return notes.toString();
    }
    
    public void cacheProvenanceEvent(ProvenanceEvent event) {
        if (event.getActor() != null) {
            provenanceCache.computeIfAbsent(event.getActor(), k -> new ArrayList<>()).add(event);
            
            List<ProvenanceEvent> events = provenanceCache.get(event.getActor());
            if (events.size() > 100) {
                events.remove(0);
            }
        }
    }
    
    /**
     * Record an incident for an entity. This is now primarily for legacy/manual incident tracking.
     * For policy violations, use the PolicyViolationEventService directly.
     */
    public void recordIncident(String agentId) {
        incidentTracker.merge(agentId, 1, Integer::sum);
        log.warn("Incident recorded for agent: {}. Total in-memory incidents: {}", 
            agentId, incidentTracker.get(agentId));
    }
    
    /**
     * Record a policy violation incident that will affect trust scores.
     * This persists the violation to the database for accurate trust score calculation.
     */
    public void recordPolicyViolation(String entityId, String entityName, String endpoint, boolean approved, String approverId) {
        if (policyViolationEventService != null) {
            if (approved) {
                policyViolationEventService.recordZtatApproval(
                    entityId, entityName, endpoint, null, approverId, null,
                    "Policy violation recorded via TrustEvaluationService"
                );
            } else {
                policyViolationEventService.recordZtatDenial(
                    entityId, entityName, endpoint, null, approverId, null,
                    "Policy violation recorded via TrustEvaluationService"
                );
            }
            log.info("Policy violation recorded for entity {}: endpoint={}, approved={}", 
                entityId, endpoint, approved);
        } else {
            // Fall back to in-memory tracking if persistent service is not available
            if (!approved) {
                recordIncident(entityId);
            }
        }
    }
    
    /**
     * Clear incidents for an entity. Note: this only clears the in-memory tracker.
     * Persistent policy violations cannot be cleared (they are part of the audit trail).
     */
    public void clearIncidents(String agentId) {
        incidentTracker.put(agentId, 0);
        log.info("In-memory incidents cleared for agent: {}", agentId);
    }
    
    /**
     * Get the total incident count for an entity (from both persistent and in-memory stores).
     */
    public int getTotalIncidentCount(String entityId) {
        return getIncidentCount(entityId);
    }
}
