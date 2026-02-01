package io.sentrius.sso.core.services.documents;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.sentrius.sso.core.config.SystemOptions;
import io.sentrius.sso.core.model.agents.AgentExecutionAudit;
import io.sentrius.sso.core.model.chat.AgentCommunication;
import io.sentrius.sso.core.model.documents.KnowledgeGraphNode;
import io.sentrius.sso.core.model.sessions.SessionLog;
import io.sentrius.sso.core.model.sessions.TerminalLogs;
import io.sentrius.sso.core.repository.AgentCommunicationRepository;
import io.sentrius.sso.core.repository.TerminalLogsRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Service for ingesting terminal sessions and agent executions into the knowledge graph.
 * This enables querying session history, command patterns, and agent interactions
 * through the same knowledge graph interface used for documents.
 *
 * Node types created:
 * - session: Terminal/SSH session with metadata and commands
 * - command: Individual commands executed (optionally with output)
 * - agent_execution: Agent execution audit with summary and communications
 * - agent_communication: Individual agent messages/responses
 *
 * Relationships created:
 * - session EXECUTED_BY user
 * - session CONNECTED_TO system
 * - session CONTAINS command
 * - command FOLLOWED_BY command (sequence)
 * - agent_execution TRIGGERED_BY user
 * - agent_execution CONTAINS agent_communication
 * - agent_execution ACCESSED system (if applicable)
 */
@Slf4j
@Service
public class KnowledgeGraphIngestionService {

    private final KnowledgeGraphService knowledgeGraphService;
    private final SystemOptions systemOptions;
    private final ObjectMapper objectMapper;

    @Autowired(required = false)
    private TerminalLogsRepository terminalLogsRepository;

    @Autowired(required = false)
    private AgentCommunicationRepository agentCommunicationRepository;

    // Pattern to extract commands from terminal output
    private static final Pattern COMMAND_PATTERN = Pattern.compile("(?:^|\\n)[$#>]\\s*(.+?)(?=\\n|$)");

    @Autowired
    public KnowledgeGraphIngestionService(
            KnowledgeGraphService knowledgeGraphService,
            SystemOptions systemOptions,
            ObjectMapper objectMapper) {
        this.knowledgeGraphService = knowledgeGraphService;
        this.systemOptions = systemOptions;
        this.objectMapper = objectMapper;
    }

    /**
     * Check if session ingestion is enabled
     */
    public boolean isSessionIngestionEnabled() {
        return systemOptions.knowledgeGraphSessionsEnabled != null
            && systemOptions.knowledgeGraphSessionsEnabled;
    }

    /**
     * Check if agent execution ingestion is enabled
     */
    public boolean isAgentIngestionEnabled() {
        return systemOptions.knowledgeGraphAgentExecutionsEnabled != null
            && systemOptions.knowledgeGraphAgentExecutionsEnabled;
    }

    /**
     * Ingest a completed terminal/SSH session into the knowledge graph.
     * Creates session node, command nodes, and relationships.
     *
     * @param session The session log to ingest
     * @param terminalLogs The terminal logs for this session
     * @return The created session node, or null if ingestion is disabled/failed
     */
    public KnowledgeGraphNode ingestSession(SessionLog session, List<TerminalLogs> terminalLogs) {
        if (!isSessionIngestionEnabled()) {
            log.debug("Session ingestion disabled, skipping session {}", session.getId());
            return null;
        }

        try {
            log.info("Ingesting session {} into knowledge graph", session.getId());

            // Get markings from system options
            String markings = systemOptions.knowledgeGraphSessionMarkings;
            if (markings == null || markings.trim().isEmpty()) {
                markings = "SESSION_DATA";
            }

            // Build session node
            String sessionNodeId = "session:" + session.getId();
            Map<String, Object> properties = new HashMap<>();
            properties.put("sessionTime", session.getSessionTm() != null ? session.getSessionTm().toString() : null);
            properties.put("ipAddress", session.getIpAddress());
            properties.put("closed", session.getClosed());
            properties.put("firstName", session.getFirstName());
            properties.put("lastName", session.getLastName());

            // Extract commands from terminal logs
            List<String> commands = extractCommandsFromLogs(terminalLogs);
            properties.put("commandCount", commands.size());

            // Store command list (limited)
            int maxCommands = systemOptions.knowledgeGraphMaxCommandsPerSession != null
                ? systemOptions.knowledgeGraphMaxCommandsPerSession : 100;
            properties.put("commands", commands.stream().limit(maxCommands).collect(Collectors.toList()));

            // Build hosts connected
            Set<String> hosts = terminalLogs.stream()
                .map(TerminalLogs::getHost)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
            properties.put("hostsConnected", new ArrayList<>(hosts));

            KnowledgeGraphNode sessionNode = KnowledgeGraphNode.builder()
                    .id(sessionNodeId)
                    .nodeType("session")
                    .name("Session by " + session.getUsername() + " at " + session.getSessionTm())
                    .description("Terminal session from " + session.getIpAddress() + " with " + commands.size() + " commands")
                    .entityId(session.getId())
                    .properties(properties)
                    .markings(markings)
                    .createdBy(session.getUsername())
                    .createdAt(session.getSessionTm() != null
                        ? LocalDateTime.ofInstant(session.getSessionTm().toInstant(), ZoneId.systemDefault())
                        : LocalDateTime.now())
                    .updatedAt(LocalDateTime.now())
                    .tags(Arrays.asList("session", "terminal", session.getClosed() ? "closed" : "active"))
                    .build();

            // Create session node
            KnowledgeGraphNode createdNode = knowledgeGraphService.createNode(sessionNode, session.getUsername());

            if (createdNode != null) {
                // Create relationship: session EXECUTED_BY user
                createUserRelationship(sessionNodeId, session.getUsername(), markings);

                // Create relationships to connected systems
                if (systemOptions.knowledgeGraphSessionSystemRelationships != null
                    && systemOptions.knowledgeGraphSessionSystemRelationships) {
                    for (String host : hosts) {
                        createSystemRelationship(sessionNodeId, host, markings);
                    }
                }

                // Optionally create individual command nodes
                if (systemOptions.knowledgeGraphIncludeCommandOutput != null
                    && systemOptions.knowledgeGraphIncludeCommandOutput) {
                    createCommandNodes(sessionNodeId, commands, terminalLogs, markings, session.getUsername());
                }

                log.info("Successfully ingested session {} with {} commands into knowledge graph",
                    session.getId(), commands.size());
            }

            return createdNode;

        } catch (Exception e) {
            log.error("Failed to ingest session {} into knowledge graph: {}", session.getId(), e.getMessage(), e);
            return null;
        }
    }

    /**
     * Ingest a completed agent execution into the knowledge graph.
     * Creates execution node, communication nodes, and relationships.
     *
     * @param execution The agent execution audit to ingest
     * @return The created execution node, or null if ingestion is disabled/failed
     */
    public KnowledgeGraphNode ingestAgentExecution(AgentExecutionAudit execution) {
        if (!isAgentIngestionEnabled()) {
            log.debug("Agent execution ingestion disabled, skipping execution {}", execution.getExecutionId());
            return null;
        }

        try {
            log.info("Ingesting agent execution {} into knowledge graph", execution.getExecutionId());

            // Get markings from system options
            String markings = systemOptions.knowledgeGraphAgentMarkings;
            if (markings == null || markings.trim().isEmpty()) {
                markings = "AGENT_DATA";
            }

            // Build execution node
            String executionNodeId = "agent_execution:" + execution.getExecutionId();
            Map<String, Object> properties = new HashMap<>();
            properties.put("agentId", execution.getAgentId());
            properties.put("agentType", execution.getAgentType());
            properties.put("status", execution.getStatus());
            properties.put("startedAt", execution.getStartTime() != null ? execution.getStartTime().toString() : null);
            properties.put("completedAt", execution.getEndTime() != null ? execution.getEndTime().toString() : null);
            properties.put("podName", execution.getPodLogs() != null ? "has_logs" : "no_logs");
            properties.put("executedBy", execution.getExecutedBy());

            // Include summary if available
            if (execution.getSummary() != null && !execution.getSummary().isEmpty()) {
                properties.put("summary", execution.getSummary());
            }

            // Extract key actions/verbs from execution
            if (execution.getResourceLinks() != null) {
                properties.put("extractedResources", execution.getResourceLinks());
            }

            String description = String.format("Agent %s execution (%s) - %s",
                execution.getAgentType(),
                execution.getStatus(),
                execution.getSummary() != null ? truncate(execution.getSummary(), 200) : "No summary");

            KnowledgeGraphNode executionNode = KnowledgeGraphNode.builder()
                    .id(executionNodeId)
                    .nodeType("agent_execution")
                    .name(execution.getAgentType() + " execution " + execution.getExecutionId().substring(0, 8))
                    .description(description)
                    .properties(properties)
                    .markings(markings)
                    .createdBy(execution.getExecutedBy())
                    .createdAt(execution.getStartTime() != null
                        ? LocalDateTime.ofInstant(execution.getStartTime(), ZoneId.systemDefault())
                        : LocalDateTime.now())
                    .updatedAt(LocalDateTime.now())
                    .tags(Arrays.asList("agent", execution.getAgentType(), execution.getStatus()))
                    .build();

            // Create execution node
            KnowledgeGraphNode createdNode = knowledgeGraphService.createNode(executionNode, execution.getExecutedBy());

            if (createdNode != null) {
                // Create relationship: execution TRIGGERED_BY user
                if (execution.getExecutedBy() != null) {
                    createUserRelationship(executionNodeId, execution.getExecutedBy(), markings);
                }

                // Create relationship to agent
                if (execution.getAgentId() != null) {
                    createAgentRelationship(executionNodeId, execution.getAgentId(), markings);
                }

                // Create communication nodes if enabled
                if (systemOptions.knowledgeGraphAgentCommunicationRelationships != null
                    && systemOptions.knowledgeGraphAgentCommunicationRelationships
                    && agentCommunicationRepository != null) {
                    createCommunicationNodes(executionNodeId, execution, markings);
                }

                log.info("Successfully ingested agent execution {} into knowledge graph", execution.getExecutionId());
            }

            return createdNode;

        } catch (Exception e) {
            log.error("Failed to ingest agent execution {} into knowledge graph: {}",
                execution.getExecutionId(), e.getMessage(), e);
            return null;
        }
    }

    /**
     * Extract commands from terminal log output
     */
    private List<String> extractCommandsFromLogs(List<TerminalLogs> logs) {
        List<String> commands = new ArrayList<>();

        for (TerminalLogs log : logs) {
            if (log.getOutput() != null) {
                // Simple extraction: look for lines starting with common prompts
                String[] lines = log.getOutput().split("\n");
                for (String line : lines) {
                    line = line.trim();
                    // Match lines that look like commands (after $ or # or >)
                    if (line.matches("^[$#>]\\s*.+") || line.matches("^\\[.*\\][$#>]\\s*.+")) {
                        String cmd = line.replaceFirst("^.*[$#>]\\s*", "").trim();
                        if (!cmd.isEmpty() && cmd.length() < 500) { // Sanity check
                            commands.add(cmd);
                        }
                    }
                }
            }
        }

        return commands;
    }

    /**
     * Create a relationship between session/execution and user
     */
    private void createUserRelationship(String nodeId, String username, String markings) {
        try {
            // Create or reference user node
            String userNodeId = "user:" + sanitizeId(username);

            // Create user node if it doesn't exist (with minimal info)
            KnowledgeGraphNode userNode = KnowledgeGraphNode.builder()
                    .id(userNodeId)
                    .nodeType("user")
                    .name(username)
                    .description("User: " + username)
                    .markings("PUBLIC") // User nodes are generally public
                    .build();
            knowledgeGraphService.createNode(userNode, username);

            // Create relationship
            knowledgeGraphService.createRelationship(nodeId, userNodeId, "EXECUTED_BY", 1.0, username);

        } catch (Exception e) {
            log.warn("Failed to create user relationship for {}: {}", nodeId, e.getMessage());
        }
    }

    /**
     * Create a relationship between session and connected system
     */
    private void createSystemRelationship(String sessionNodeId, String host, String markings) {
        try {
            // Create or reference system node
            String systemNodeId = "system:" + sanitizeId(host);

            KnowledgeGraphNode systemNode = KnowledgeGraphNode.builder()
                    .id(systemNodeId)
                    .nodeType("system")
                    .name(host)
                    .description("Connected system: " + host)
                    .markings(markings) // Systems inherit session markings
                    .build();
            knowledgeGraphService.createNode(systemNode, "system");

            // Create relationship
            knowledgeGraphService.createRelationship(sessionNodeId, systemNodeId, "CONNECTED_TO", 1.0, "system");

        } catch (Exception e) {
            log.warn("Failed to create system relationship for {}: {}", sessionNodeId, e.getMessage());
        }
    }

    /**
     * Create a relationship between execution and agent
     */
    private void createAgentRelationship(String executionNodeId, String agentId, String markings) {
        try {
            String agentNodeId = "agent:" + sanitizeId(agentId);

            KnowledgeGraphNode agentNode = KnowledgeGraphNode.builder()
                    .id(agentNodeId)
                    .nodeType("agent")
                    .name("Agent " + agentId)
                    .description("AI Agent: " + agentId)
                    .markings(markings)
                    .build();
            knowledgeGraphService.createNode(agentNode, "system");

            knowledgeGraphService.createRelationship(executionNodeId, agentNodeId, "PERFORMED_BY", 1.0, "system");

        } catch (Exception e) {
            log.warn("Failed to create agent relationship for {}: {}", executionNodeId, e.getMessage());
        }
    }

    /**
     * Create individual command nodes with output (verbose mode)
     */
    private void createCommandNodes(String sessionNodeId, List<String> commands,
            List<TerminalLogs> logs, String markings, String username) {

        String previousCommandId = null;
        int commandIndex = 0;

        for (String command : commands) {
            try {
                String commandNodeId = sessionNodeId + ":cmd:" + commandIndex;

                Map<String, Object> properties = new HashMap<>();
                properties.put("command", command);
                properties.put("index", commandIndex);
                properties.put("sessionId", sessionNodeId);

                KnowledgeGraphNode commandNode = KnowledgeGraphNode.builder()
                        .id(commandNodeId)
                        .nodeType("command")
                        .name(truncate(command, 100))
                        .description("Command: " + command)
                        .properties(properties)
                        .markings(markings)
                        .createdBy(username)
                        .build();

                knowledgeGraphService.createNode(commandNode, username);

                // Create CONTAINS relationship from session
                knowledgeGraphService.createRelationship(sessionNodeId, commandNodeId, "CONTAINS", 1.0, username);

                // Create FOLLOWED_BY relationship for command sequence
                if (previousCommandId != null) {
                    knowledgeGraphService.createRelationship(previousCommandId, commandNodeId, "FOLLOWED_BY", 1.0, username);
                }

                previousCommandId = commandNodeId;
                commandIndex++;

            } catch (Exception e) {
                log.warn("Failed to create command node for command {}: {}", commandIndex, e.getMessage());
            }
        }
    }

    /**
     * Create communication nodes for agent execution
     */
    private void createCommunicationNodes(String executionNodeId, AgentExecutionAudit execution, String markings) {
        try {
            // Fetch communications for this execution using UUID
            UUID executionUuid;
            try {
                executionUuid = UUID.fromString(execution.getExecutionId());
            } catch (IllegalArgumentException e) {
                log.debug("Execution ID {} is not a valid UUID, skipping communication nodes", execution.getExecutionId());
                return;
            }

            List<AgentCommunication> communications = agentCommunicationRepository
                .findByCommunicationId(executionUuid);

            if (communications == null || communications.isEmpty()) {
                return;
            }

            // Sort by created time
            communications.sort((a, b) -> {
                if (a.getCreatedAt() == null) return -1;
                if (b.getCreatedAt() == null) return 1;
                return a.getCreatedAt().compareTo(b.getCreatedAt());
            });

            int commIndex = 0;
            for (AgentCommunication comm : communications) {
                try {
                    String commNodeId = executionNodeId + ":comm:" + commIndex;

                    Map<String, Object> properties = new HashMap<>();
                    properties.put("messageType", comm.getMessageType());
                    properties.put("sourceAgent", comm.getSourceAgent());
                    properties.put("targetAgent", comm.getTargetAgent());
                    properties.put("createdAt", comm.getCreatedAt() != null ? comm.getCreatedAt().toString() : null);

                    // Truncate payload for storage
                    String payload = comm.getPayload();
                    if (payload != null && payload.length() > 2000) {
                        payload = payload.substring(0, 2000) + "... [truncated]";
                    }
                    properties.put("payload", payload);

                    String description = String.format("%s message: %s",
                        comm.getMessageType(),
                        truncate(payload != null ? payload : "", 100));

                    KnowledgeGraphNode commNode = KnowledgeGraphNode.builder()
                            .id(commNodeId)
                            .nodeType("agent_communication")
                            .name(comm.getMessageType() + " message " + commIndex)
                            .description(description)
                            .properties(properties)
                            .markings(markings)
                            .createdBy(execution.getExecutedBy())
                            .build();

                    knowledgeGraphService.createNode(commNode, execution.getExecutedBy());

                    // Create CONTAINS relationship
                    knowledgeGraphService.createRelationship(executionNodeId, commNodeId, "CONTAINS", 1.0,
                        execution.getExecutedBy());

                    commIndex++;

                } catch (Exception e) {
                    log.warn("Failed to create communication node {}: {}", commIndex, e.getMessage());
                }
            }

            log.debug("Created {} communication nodes for execution {}", commIndex, execution.getExecutionId());

        } catch (Exception e) {
            log.warn("Failed to create communication nodes for execution {}: {}",
                execution.getExecutionId(), e.getMessage());
        }
    }

    /**
     * Sanitize a string for use as a node ID
     */
    private String sanitizeId(String input) {
        if (input == null) return "unknown";
        return input.toLowerCase()
            .replaceAll("[^a-z0-9-]", "-")
            .replaceAll("-+", "-")
            .replaceAll("^-|-$", "");
    }

    /**
     * Truncate a string to a maximum length
     */
    private String truncate(String input, int maxLength) {
        if (input == null) return "";
        if (input.length() <= maxLength) return input;
        return input.substring(0, maxLength - 3) + "...";
    }
}
