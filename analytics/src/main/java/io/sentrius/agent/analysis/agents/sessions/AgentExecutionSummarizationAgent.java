package io.sentrius.agent.analysis.agents.sessions;

import com.fasterxml.jackson.databind.JsonNode;
import io.sentrius.agent.analysis.service.AgentExecutionSummarizerService;
import io.sentrius.sso.core.model.agents.AgentExecutionAudit;
import io.sentrius.sso.core.model.chat.AgentCommunication;
import io.sentrius.sso.core.repository.AgentExecutionAuditRepository;
import io.sentrius.sso.core.services.agents.AgentExecutionAuditService;
import io.sentrius.sso.core.services.agents.AgentService;
import io.sentrius.sso.core.services.agents.ZeroTrustClientService;
import io.sentrius.sso.core.services.security.IntegrationSecurityTokenService;
import io.sentrius.sso.core.utils.JsonUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Analytics agent that processes agent execution audits and generates summaries using LLM.
 * Runs on a scheduled task to analyze completed executions without summaries.
 *
 * This agent automatically summarizes:
 * - Chat helper executions
 * - Coding agent executions
 * - MCP agent executions
 * - Custom agent executions
 *
 * It analyzes pod logs, communications, and execution context to generate
 * human-readable summaries with extracted resource links.
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "agents.agent-execution-analytics.enabled", havingValue = "true", matchIfMissing = true)
public class AgentExecutionSummarizationAgent {

    // Executions stuck in RUNNING for more than 1 hour will be auto-closed
    private static final Duration EXECUTION_EXPIRY_TIMEOUT = Duration.ofHours(1);

    private final AgentExecutionAuditRepository auditRepository;
    private final AgentExecutionAuditService auditService;
    private final AgentExecutionSummarizerService summarizerService;
    private final AgentService agentService;
    private final IntegrationSecurityTokenService integrationSecurityTokenService;
    private final ZeroTrustClientService zeroTrustClientService;

    @Autowired
    public AgentExecutionSummarizationAgent(
            AgentExecutionAuditRepository auditRepository,
            AgentExecutionAuditService auditService,
            AgentExecutionSummarizerService summarizerService,
            AgentService agentService,
            IntegrationSecurityTokenService integrationSecurityTokenService,
            ZeroTrustClientService zeroTrustClientService) {
        this.auditRepository = auditRepository;
        this.auditService = auditService;
        this.summarizerService = summarizerService;
        this.agentService = agentService;
        this.integrationSecurityTokenService = integrationSecurityTokenService;
        this.zeroTrustClientService = zeroTrustClientService;
    }

    /**
     * Process agent executions without summaries every 2 minutes
     */
    @Scheduled(fixedDelay = 120000) // 2 minutes
    @Transactional
    public void processAgentExecutions() {
        // Check if summarization is enabled via API
        if (!isSummarizationEnabled()) {
            log.debug("Agent execution summarization disabled in system options");
            return;
        }

        // Check if LLM integration is available
        if (!isLLMAvailable()) {
            log.debug("LLM integration not available, skipping agent execution summarization");
            return;
        }

        log.debug("Checking for agent executions without summaries...");

        // Find completed executions without summaries
        List<String> executionIds = auditRepository.findCompletedExecutionsWithoutSummaries();

        // Skip audit creation if there's nothing to process
        if (executionIds.isEmpty()) {
            log.debug("No agent executions to summarize");
            return;
        }

        // Only create audit when we have actual work to do
        String taskExecutionId = UUID.randomUUID().toString();
        createTaskAudit(taskExecutionId, "agent-execution-summarizer");

        String taskStatus = "COMPLETED";
        log.info("Processing {} agent executions without summaries...", executionIds.size());

        try {

            int processed = 0;
            int failed = 0;

            for (String executionId : executionIds) {
                try {
                    processExecution(executionId);
                    processed++;
                } catch (Exception e) {
                    log.error("Error processing agent execution {}: {}", executionId, e.getMessage(), e);
                    failed++;
                }
            }

            log.info("Finished processing agent executions: {} processed, {} failed", processed, failed);

            if (failed > 0) {
                taskStatus = "COMPLETED_WITH_ERRORS";
            }
        } catch (Exception e) {
            log.error("Error in processAgentExecutions", e);
            taskStatus = "ERROR";
        } finally {
            closeTaskAudit(taskExecutionId, taskStatus);
        }
    }

    /**
     * Expire stuck agent executions every 5 minutes.
     * This handles cases where agents crash or connections are lost without proper cleanup.
     * Executions stuck in RUNNING status for more than EXECUTION_EXPIRY_TIMEOUT will be closed.
     */
    @Scheduled(fixedDelay = 300000) // 5 minutes
    @Transactional
    public void expireStuckExecutions() {
        log.debug("Checking for stuck agent executions...");

        // Find executions in RUNNING status
        List<AgentExecutionAudit> runningExecutions = auditRepository.findByStatusOrderByStartTimeDesc("RUNNING");

        Instant cutoffTime = Instant.now().minus(EXECUTION_EXPIRY_TIMEOUT);

        // Filter to only stuck executions
        List<AgentExecutionAudit> stuckExecutions = runningExecutions.stream()
            .filter(audit -> audit.getStartTime().isBefore(cutoffTime))
            .toList();

        // Skip audit creation if there's nothing to expire
        if (stuckExecutions.isEmpty()) {
            log.debug("No stuck executions found");
            return;
        }

        // Only create audit when we have actual work to do
        String taskExecutionId = UUID.randomUUID().toString();
        createTaskAudit(taskExecutionId, "agent-execution-expiry");

        String taskStatus = "COMPLETED";
        log.info("Found {} stuck agent executions to expire", stuckExecutions.size());

        try {
            int expired = 0;

            for (AgentExecutionAudit audit : stuckExecutions) {
                log.warn("Expiring stuck agent execution: {} (started: {}, age: {} minutes)",
                    audit.getExecutionId(),
                    audit.getStartTime(),
                    Duration.between(audit.getStartTime(), Instant.now()).toMinutes());

                try {
                    auditService.closeAudit(audit.getExecutionId(), "EXPIRED");
                    expired++;
                } catch (Exception e) {
                    log.error("Failed to expire stuck execution: {}", audit.getExecutionId(), e);
                    taskStatus = "COMPLETED_WITH_ERRORS";
                }
            }

            log.info("Expired {} stuck agent executions", expired);
        } catch (Exception e) {
            log.error("Error in expireStuckExecutions", e);
            taskStatus = "ERROR";
        } finally {
            closeTaskAudit(taskExecutionId, taskStatus);
        }
    }

    /**
     * Automatically consolidate duplicate agent execution audits every hour.
     * Merges audits with the same agentType, agentId, status, and executedBy into a single record.
     */
    @Scheduled(cron = "0 0 * * * *") // Every hour at minute 0
    @Transactional
    public void consolidateDuplicateAudits() {
        log.debug("Running scheduled duplicate consolidation...");

        try {
            long consolidatedCount = auditService.consolidateDuplicates();

            if (consolidatedCount > 0) {
                log.info("Scheduled consolidation: merged {} duplicate agent execution audits", consolidatedCount);
            } else {
                log.debug("Scheduled consolidation: no duplicates found");
            }
        } catch (Exception e) {
            log.error("Error during scheduled duplicate consolidation", e);
        }
    }

    /**
     * Process a single agent execution - analyze logs and communications to generate summary
     */
    private void processExecution(String executionId) {
        log.info("Processing agent execution: {}", executionId);

        // Get execution audit record
        AgentExecutionAudit audit = auditRepository.findByExecutionId(executionId).orElse(null);
        if (audit == null) {
            log.warn("Agent execution audit not found: {}", executionId);
            return;
        }

        // Skip if already has summary
        if (audit.getSummary() != null && !audit.getSummary().trim().isEmpty()) {
            log.debug("Agent execution {} already has summary, skipping", executionId);
            return;
        }

        // Build comprehensive context from logs and communications
        String contextLogs = buildExecutionContext(audit);

        if (contextLogs.isEmpty()) {
            log.warn("No logs or communications found for execution: {}", executionId);
            // Create a minimal summary
            updateAuditWithMinimalSummary(audit);
            return;
        }

        log.info("Analyzing execution {} with {} characters of context",
            executionId, contextLogs.length());

        // Use the summarizer service to analyze and generate summary
        Map<String, Object> summaryResult = summarizerService.summarizeExecution(
            audit.getExecutionId(),
            audit.getAgentId(),
            audit.getAgentType(),
            contextLogs
        );

        // Update audit record with summary
        updateAuditWithSummary(audit, summaryResult);

        log.info("Successfully processed agent execution {}: status={}, summary length={}",
            executionId,
            summaryResult.get("status"),
            summaryResult.get("summary") != null ? summaryResult.get("summary").toString().length() : 0);
    }

    /**
     * Build comprehensive execution context from pod logs and communications
     */
    private String buildExecutionContext(AgentExecutionAudit audit) {
        StringBuilder context = new StringBuilder();

        // Add execution metadata
        context.append("=== Agent Execution Context ===\n");
        context.append("Execution ID: ").append(audit.getExecutionId()).append("\n");
        context.append("Agent Type: ").append(audit.getAgentType()).append("\n");
        context.append("Agent ID: ").append(audit.getAgentId()).append("\n");
        context.append("Executed By: ").append(audit.getExecutedBy()).append("\n");
        context.append("Status: ").append(audit.getStatus()).append("\n");
        context.append("Duration: ").append(audit.getDurationMs()).append("ms\n\n");

        // Add communications if available
        try {
            List<AgentCommunication> communications = agentService.getCommunications(
                UUID.fromString(audit.getExecutionId())
            );

            if (communications != null && !communications.isEmpty()) {
                context.append("=== Agent Communications (").append(communications.size()).append(" messages) ===\n");
                int messageCount = 0;
                for (AgentCommunication comm : communications) {
                    if (messageCount++ >= 50) { // Limit to first 50 messages
                        context.append("... (").append(communications.size() - 50).append(" more messages)\n");
                        break;
                    }
                    context.append("[").append(comm.getCreatedAt()).append("] ");
                    context.append(comm.getMessageType()).append(" - ");
                    context.append(comm.getSourceAgent()).append(" -> ").append(comm.getTargetAgent()).append(": ");

                    // Truncate very long messages
                    String payload = comm.getPayload();
                    if (payload != null) {
                        if (payload.length() > 500) {
                            context.append(payload, 0, 500).append("...\n");
                        } else {
                            context.append(payload).append("\n");
                        }
                    }
                }
                context.append("\n");
            }
        } catch (Exception e) {
            log.debug("Could not fetch communications for execution {}: {}",
                audit.getExecutionId(), e.getMessage());
        }

        // Add pod logs if available
        if (audit.getPodLogs() != null && !audit.getPodLogs().isEmpty()) {
            context.append("=== Pod Logs ===\n");
            // Truncate pod logs if too long (keep last 10000 characters)
            String podLogs = audit.getPodLogs();
            if (podLogs.length() > 10000) {
                context.append("...[truncated]...\n");
                context.append(podLogs.substring(podLogs.length() - 10000));
            } else {
                context.append(podLogs);
            }
            context.append("\n");
        }

        return context.toString();
    }

    /**
     * Update audit record with generated summary
     */
    private void updateAuditWithSummary(AgentExecutionAudit audit, Map<String, Object> summaryResult) {
        String status = (String) summaryResult.get("status");
        String summary = (String) summaryResult.get("summary");
        String resourceLinks = (String) summaryResult.get("resourceLinks");
        Integer exitCode = (Integer) summaryResult.get("exitCode");

        // Update the audit record
        if (status != null && !status.isEmpty()) {
            audit.setStatus(status);
        }

        if (summary != null && !summary.isEmpty()) {
            audit.setSummary(summary);
        }

        if (resourceLinks != null) {
            audit.setResourceLinks(resourceLinks);
        }

        if (exitCode != null) {
            audit.setExitCode(exitCode);
        }

        auditRepository.save(audit);
    }

    /**
     * Update audit with minimal summary when no logs are available
     */
    private void updateAuditWithMinimalSummary(AgentExecutionAudit audit) {
        String summary = String.format(
            "%s agent execution completed. No detailed logs available.",
            audit.getAgentType()
        );

        audit.setSummary(summary);
        auditRepository.save(audit);
    }

    /**
     * Check if LLM integration is available
     */
    private boolean isLLMAvailable() {
        try {
            var token = integrationSecurityTokenService.findByConnectionType("openai")
                .stream().findFirst().orElse(null);
            return token != null;
        } catch (Exception e) {
            log.debug("Error checking LLM availability", e);
            return false;
        }
    }

    /**
     * Check if agent execution summarization is enabled via system options API.
     * Fetches the enableAgentExecutionSummarization setting from the main Sentrius API.
     */
    private boolean isSummarizationEnabled() {
        try {
            String response = zeroTrustClientService.callAuthenticatedGetOnApi(
                "/api/v1/system/settings/enableAgentExecutionSummarization"
            );

            if (response != null && !response.isEmpty()) {
                JsonNode node = JsonUtil.MAPPER.readTree(response);
                // SystemOption returns { name, value, description, group, ... }
                if (node.has("value")) {
                    String value = node.get("value").asText();
                    return "true".equalsIgnoreCase(value);
                }
            }
            // Default to true if we can't fetch the setting
            return true;
        } catch (io.sentrius.sso.core.exceptions.ZtatException e) {
            log.debug("ZTAT error fetching summarization setting, defaulting to enabled: {}", e.getMessage());
            return true;
        } catch (Exception e) {
            log.debug("Could not fetch summarization enabled setting from API, defaulting to enabled: {}", e.getMessage());
            return true;
        }
    }

    /**
     * Create an audit record for a scheduled task execution
     */
    private void createTaskAudit(String taskExecutionId, String agentType) {
        try {
            auditService.createAudit(
                "analytics-agent",
                taskExecutionId,
                agentType,
                "system"
            );
            log.debug("Created audit for {} task: {}", agentType, taskExecutionId);
        } catch (Exception e) {
            log.debug("Could not create audit for {} task: {}", agentType, e.getMessage());
        }
    }

    /**
     * Close an audit record for a scheduled task execution
     */
    private void closeTaskAudit(String taskExecutionId, String status) {
        try {
            auditService.closeAudit(taskExecutionId, status);
            log.debug("Closed audit for task {} with status: {}", taskExecutionId, status);
        } catch (Exception e) {
            log.debug("Could not close audit for task: {}", e.getMessage());
        }
    }
}

