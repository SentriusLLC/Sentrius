package io.sentrius.agent.analysis.agents.automation;

import com.fasterxml.jackson.databind.JsonNode;
import io.sentrius.sso.core.dto.ztat.TokenDTO;
import io.sentrius.sso.core.model.automation.AutomationSuggestion;
import io.sentrius.sso.core.model.metadata.TerminalCommand;
import io.sentrius.sso.core.model.metadata.TerminalSessionMetadata;
import io.sentrius.sso.core.model.sessions.RdpSessionSummary;
import io.sentrius.sso.core.model.sessions.SshSessionSummary;
import io.sentrius.sso.core.repository.RdpSessionSummaryRepository;
import io.sentrius.sso.core.repository.SshSessionSummaryRepository;
import io.sentrius.sso.core.repository.automation.AutomationSuggestionRepository;
import io.sentrius.sso.core.services.agents.LLMService;
import io.sentrius.sso.core.services.metadata.TerminalCommandService;
import io.sentrius.sso.core.services.metadata.TerminalSessionMetadataService;
import io.sentrius.sso.core.services.security.IntegrationSecurityTokenService;
import io.sentrius.sso.core.utils.JsonUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Analytics agent that reviews terminal sessions and RDP sessions to identify
 * repetitive patterns and suggest automation opportunities.
 * 
 * This agent:
 * 1. Periodically analyzes closed sessions
 * 2. Detects repetitive command patterns
 * 3. Uses LLM to generate automation scripts (Python or Bash)
 * 4. Stores suggestions for human review
 * 5. Integrates with agent execution infrastructure for deployment
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "agents.automation-suggestion.enabled", havingValue = "true", matchIfMissing = false)
public class AutomationSuggestionAnalyzer {
    
    private final TerminalSessionMetadataService sessionMetadataService;
    private final TerminalCommandService commandService;
    private final SshSessionSummaryRepository sshSummaryRepository;
    private final RdpSessionSummaryRepository rdpSummaryRepository;
    private final AutomationSuggestionRepository suggestionRepository;
    private final IntegrationSecurityTokenService integrationSecurityTokenService;
    private final LLMService llmService;
    
    // Minimum number of sessions showing similar pattern before suggesting automation
    private static final int MIN_PATTERN_FREQUENCY = 3;
    
    // Minimum number of commands in a pattern to be considered for automation
    private static final int MIN_COMMAND_SEQUENCE_LENGTH = 2;
    
    /**
     * Main scheduled task that analyzes sessions and generates automation suggestions
     * Runs every 6 hours
     */
    @Scheduled(fixedDelay = 21600000) // 6 hours
    @Transactional
    public void analyzeSessionsForAutomation() {
        if (!isLLMAvailable()) {
            log.debug("LLM integration not available, skipping automation suggestion analysis");
            return;
        }
        
        log.info("Starting automation suggestion analysis...");
        
        try {
            // Analyze terminal/SSH sessions
            analyzeTerminalSessions();
            
            // Analyze RDP sessions
            analyzeRdpSessions();
            
            log.info("Automation suggestion analysis completed");
        } catch (Exception e) {
            log.error("Error during automation suggestion analysis", e);
        }
    }
    
    /**
     * Analyze terminal sessions for automation opportunities
     */
    private void analyzeTerminalSessions() {
        log.info("Analyzing terminal sessions for automation patterns...");
        
        // Get processed sessions (those that have been analyzed by SessionAnalyticsAgent)
        List<TerminalSessionMetadata> processedSessions = sessionMetadataService.getSessionsByState("PROCESSED");
        
        if (processedSessions.isEmpty()) {
            log.info("No processed terminal sessions to analyze");
            return;
        }
        
        log.info("Found {} processed terminal sessions", processedSessions.size());
        
        // Group sessions by user and target system
        Map<String, List<TerminalSessionMetadata>> sessionsByUserAndTarget = groupSessionsByUserAndTarget(processedSessions);
        
        // Analyze each group for patterns
        for (Map.Entry<String, List<TerminalSessionMetadata>> entry : sessionsByUserAndTarget.entrySet()) {
            String key = entry.getKey();
            List<TerminalSessionMetadata> sessions = entry.getValue();
            
            if (sessions.size() < MIN_PATTERN_FREQUENCY) {
                continue; // Not enough sessions to establish a pattern
            }
            
            log.info("Analyzing {} sessions for key: {}", sessions.size(), key);
            analyzeSessionGroupForPatterns(sessions, key);
        }
    }
    
    /**
     * Analyze RDP sessions for automation opportunities
     */
    private void analyzeRdpSessions() {
        log.info("Analyzing RDP sessions for automation patterns...");
        
        // Get RDP session summaries
        List<RdpSessionSummary> rdpSummaries = rdpSummaryRepository.findAll();
        
        if (rdpSummaries.isEmpty()) {
            log.info("No RDP sessions to analyze");
            return;
        }
        
        log.info("Found {} RDP session summaries", rdpSummaries.size());
        
        // Group by user and target
        Map<String, List<RdpSessionSummary>> sessionsByUserAndTarget = groupRdpSessionsByUserAndTarget(rdpSummaries);
        
        // Analyze each group
        for (Map.Entry<String, List<RdpSessionSummary>> entry : sessionsByUserAndTarget.entrySet()) {
            String key = entry.getKey();
            List<RdpSessionSummary> sessions = entry.getValue();
            
            if (sessions.size() < MIN_PATTERN_FREQUENCY) {
                continue;
            }
            
            log.info("Analyzing {} RDP sessions for key: {}", sessions.size(), key);
            analyzeRdpSessionGroupForPatterns(sessions, key);
        }
    }
    
    /**
     * Group terminal sessions by user and target system
     */
    private Map<String, List<TerminalSessionMetadata>> groupSessionsByUserAndTarget(
            List<TerminalSessionMetadata> sessions) {
        
        return sessions.stream()
            .collect(Collectors.groupingBy(session -> {
                String user = session.getUser() != null ? session.getUser().getUsername() : "unknown";
                String target = session.getHostSystem() != null ? session.getHostSystem().getHost() : "unknown";
                return user + "@" + target;
            }));
    }
    
    /**
     * Group RDP sessions by user and target
     */
    private Map<String, List<RdpSessionSummary>> groupRdpSessionsByUserAndTarget(
            List<RdpSessionSummary> sessions) {
        
        return sessions.stream()
            .collect(Collectors.groupingBy(session -> {
                String user = session.getUserIdentifier() != null ? session.getUserIdentifier() : "unknown";
                String target = session.getTargetIdentifier() != null ? session.getTargetIdentifier() : "unknown";
                return user + "@" + target;
            }));
    }
    
    /**
     * Analyze a group of terminal sessions to find repetitive command patterns
     */
    private void analyzeSessionGroupForPatterns(List<TerminalSessionMetadata> sessions, String userTargetKey) {
        try {
            // Extract command sequences from each session
            List<List<String>> commandSequences = new ArrayList<>();
            List<Long> sessionIds = new ArrayList<>();
            
            for (TerminalSessionMetadata session : sessions) {
                List<TerminalCommand> commands = commandService.getCommandsBySessionId(session.getId());
                if (commands != null && !commands.isEmpty()) {
                    List<String> decodedCommands = commands.stream()
                        .map(cmd -> decodeCommand(cmd.getCommand()))
                        .filter(cmd -> !cmd.isEmpty())
                        .collect(Collectors.toList());
                    
                    if (decodedCommands.size() >= MIN_COMMAND_SEQUENCE_LENGTH) {
                        commandSequences.add(decodedCommands);
                        sessionIds.add(session.getId());
                    }
                }
            }
            
            if (commandSequences.isEmpty()) {
                log.debug("No command sequences found for {}", userTargetKey);
                return;
            }
            
            // Find common patterns
            List<String> commonPattern = findCommonCommandPattern(commandSequences);
            
            if (commonPattern.isEmpty() || commonPattern.size() < MIN_COMMAND_SEQUENCE_LENGTH) {
                log.debug("No significant common pattern found for {}", userTargetKey);
                return;
            }
            
            log.info("Found common pattern with {} commands for {}", commonPattern.size(), userTargetKey);
            
            // Generate automation suggestion using LLM
            generateAutomationSuggestion(
                commonPattern,
                sessionIds,
                userTargetKey,
                commandSequences.size(),
                "terminal"
            );
            
        } catch (Exception e) {
            log.error("Error analyzing session group for patterns: {}", userTargetKey, e);
        }
    }
    
    /**
     * Analyze RDP sessions for patterns
     */
    private void analyzeRdpSessionGroupForPatterns(List<RdpSessionSummary> sessions, String userTargetKey) {
        try {
            // Extract session summaries
            StringBuilder combinedSummary = new StringBuilder();
            List<Long> sessionIds = new ArrayList<>();
            
            for (RdpSessionSummary session : sessions) {
                if (session.getSummary() != null && !session.getSummary().isEmpty()) {
                    combinedSummary.append("Session ").append(session.getId()).append(":\n");
                    combinedSummary.append(session.getSummary()).append("\n\n");
                    sessionIds.add(session.getId());
                }
            }
            
            if (combinedSummary.length() == 0) {
                log.debug("No RDP session summaries available for {}", userTargetKey);
                return;
            }
            
            // Use LLM to analyze RDP sessions and suggest GUI automation
            generateRdpAutomationSuggestion(
                combinedSummary.toString(),
                sessionIds,
                userTargetKey,
                sessions.size()
            );
            
        } catch (Exception e) {
            log.error("Error analyzing RDP session group: {}", userTargetKey, e);
        }
    }
    
    /**
     * Find common command pattern across multiple sessions
     * Returns the longest common subsequence of commands
     */
    private List<String> findCommonCommandPattern(List<List<String>> commandSequences) {
        if (commandSequences.isEmpty()) {
            return Collections.emptyList();
        }
        
        // Find commands that appear in at least MIN_PATTERN_FREQUENCY sessions
        Map<String, Integer> commandFrequency = new HashMap<>();
        
        for (List<String> sequence : commandSequences) {
            Set<String> uniqueCommands = new HashSet<>(sequence);
            for (String cmd : uniqueCommands) {
                commandFrequency.put(cmd, commandFrequency.getOrDefault(cmd, 0) + 1);
            }
        }
        
        // Filter to commands that appear frequently
        Set<String> frequentCommands = commandFrequency.entrySet().stream()
            .filter(entry -> entry.getValue() >= MIN_PATTERN_FREQUENCY)
            .map(Map.Entry::getKey)
            .collect(Collectors.toSet());
        
        if (frequentCommands.isEmpty()) {
            return Collections.emptyList();
        }
        
        // Find the first sequence and return only the frequent commands in order
        List<String> firstSequence = commandSequences.get(0);
        return firstSequence.stream()
            .filter(frequentCommands::contains)
            .collect(Collectors.toList());
    }
    
    /**
     * Generate automation suggestion using LLM
     */
    private void generateAutomationSuggestion(
            List<String> commandPattern,
            List<Long> sessionIds,
            String userTargetKey,
            int patternFrequency,
            String sessionType) {
        
        try {
            var token = integrationSecurityTokenService.findByConnectionType("openai")
                .stream().findFirst().orElse(null);
            
            if (token == null) {
                log.warn("No OpenAI token available for automation generation");
                return;
            }
            
            TokenDTO tokenDTO = TokenDTO.builder()
                .ztatToken("")
                .communicationId(UUID.randomUUID().toString())
                .build();
            
            // Build prompt for LLM
            String prompt = buildAutomationPrompt(commandPattern, userTargetKey, patternFrequency);
            
            // Call LLM
            Map<String, Object> payload = Map.of(
                "model", "gpt-4o-mini",
                "messages", List.of(
                    Map.of("role", "system", "content", 
                        "You are an expert in Linux system administration and automation. " +
                        "Generate production-ready, well-documented automation scripts."),
                    Map.of("role", "user", "content", prompt)
                ),
                "max_tokens", 2000,
                "temperature", 0.3
            );
            
            String response = llmService.askQuestion(tokenDTO, payload);
            
            // Parse response
            JsonNode jsonResponse = JsonUtil.MAPPER.readTree(response);
            JsonNode choices = jsonResponse.get("choices");
            
            if (choices != null && choices.isArray() && choices.size() > 0) {
                JsonNode message = choices.get(0).get("message");
                if (message != null) {
                    JsonNode content = message.get("content");
                    if (content != null) {
                        String automationScript = content.asText();
                        
                        // Parse the LLM response to extract script and metadata
                        saveAutomationSuggestion(
                            automationScript,
                            commandPattern,
                            sessionIds,
                            userTargetKey,
                            patternFrequency
                        );
                    }
                }
            }
            
        } catch (io.sentrius.sso.core.exceptions.ZtatException | com.fasterxml.jackson.core.JsonProcessingException e) {
            log.error("Error generating automation suggestion with LLM", e);
        } catch (Exception e) {
            log.error("Error generating automation suggestion with LLM", e);
        }
    }
    
    /**
     * Generate RDP automation suggestion
     */
    private void generateRdpAutomationSuggestion(
            String combinedSummary,
            List<Long> sessionIds,
            String userTargetKey,
            int patternFrequency) {
        
        try {
            var token = integrationSecurityTokenService.findByConnectionType("openai")
                .stream().findFirst().orElse(null);
            
            if (token == null) {
                return;
            }
            
            TokenDTO tokenDTO = TokenDTO.builder()
                .ztatToken("")
                .communicationId(UUID.randomUUID().toString())
                .build();
            
            String prompt = buildRdpAutomationPrompt(combinedSummary, userTargetKey, patternFrequency);
            
            Map<String, Object> payload = Map.of(
                "model", "gpt-4o-mini",
                "messages", List.of(
                    Map.of("role", "system", "content",
                        "You are an expert in Windows automation and RDP workflows. " +
                        "Generate PowerShell or Python automation scripts for Windows tasks."),
                    Map.of("role", "user", "content", prompt)
                ),
                "max_tokens", 2000,
                "temperature", 0.3
            );
            
            String response = llmService.askQuestion(tokenDTO, payload);
            
            JsonNode jsonResponse = JsonUtil.MAPPER.readTree(response);
            JsonNode choices = jsonResponse.get("choices");
            
            if (choices != null && choices.isArray() && choices.size() > 0) {
                JsonNode message = choices.get(0).get("message");
                if (message != null) {
                    JsonNode content = message.get("content");
                    if (content != null) {
                        String automationScript = content.asText();
                        saveRdpAutomationSuggestion(
                            automationScript,
                            sessionIds,
                            userTargetKey,
                            patternFrequency
                        );
                    }
                }
            }
            
        } catch (io.sentrius.sso.core.exceptions.ZtatException | com.fasterxml.jackson.core.JsonProcessingException e) {
            log.error("Error generating RDP automation suggestion", e);
        } catch (Exception e) {
            log.error("Error generating RDP automation suggestion", e);
        }
    }
    
    /**
     * Build prompt for terminal automation
     */
    private String buildAutomationPrompt(List<String> commandPattern, String userTargetKey, int frequency) {
        StringBuilder prompt = new StringBuilder();
        prompt.append("I've observed the following command pattern executed ").append(frequency)
            .append(" times by user on system ").append(userTargetKey).append(":\n\n");
        
        for (int i = 0; i < commandPattern.size(); i++) {
            prompt.append(i + 1).append(". ").append(commandPattern.get(i)).append("\n");
        }
        
        prompt.append("\nPlease analyze this pattern and generate:\n");
        prompt.append("1. A Bash script that automates these commands\n");
        prompt.append("2. A brief description of what the automation does\n");
        prompt.append("3. Any prerequisites or dependencies needed\n");
        prompt.append("4. Safety considerations and recommended execution context\n\n");
        prompt.append("Format your response as:\n");
        prompt.append("DESCRIPTION: <brief description>\n");
        prompt.append("SCRIPT_TYPE: bash\n");
        prompt.append("PREREQUISITES: <any prerequisites>\n");
        prompt.append("SCRIPT:\n```bash\n<script content>\n```\n");
        prompt.append("SAFETY: <safety notes>\n");
        
        return prompt.toString();
    }
    
    /**
     * Build prompt for RDP automation
     */
    private String buildRdpAutomationPrompt(String summaries, String userTargetKey, int frequency) {
        StringBuilder prompt = new StringBuilder();
        prompt.append("I've observed ").append(frequency)
            .append(" RDP sessions by user on system ").append(userTargetKey)
            .append(" with the following summaries:\n\n");
        prompt.append(summaries);
        prompt.append("\nBased on these RDP session patterns, please generate:\n");
        prompt.append("1. A Python or PowerShell script to automate the repetitive tasks\n");
        prompt.append("2. A description of what the automation achieves\n");
        prompt.append("3. Prerequisites for running the automation\n\n");
        prompt.append("Format your response as:\n");
        prompt.append("DESCRIPTION: <brief description>\n");
        prompt.append("SCRIPT_TYPE: python or powershell\n");
        prompt.append("PREREQUISITES: <any prerequisites>\n");
        prompt.append("SCRIPT:\n```\n<script content>\n```\n");
        
        return prompt.toString();
    }
    
    /**
     * Save automation suggestion to database
     */
    private void saveAutomationSuggestion(
            String llmResponse,
            List<String> commandPattern,
            List<Long> sessionIds,
            String userTargetKey,
            int patternFrequency) {
        
        try {
            // Parse LLM response
            String description = extractField(llmResponse, "DESCRIPTION:");
            String scriptType = extractField(llmResponse, "SCRIPT_TYPE:");
            String script = extractScript(llmResponse);
            
            if (script.isEmpty()) {
                log.warn("Could not extract script from LLM response");
                return;
            }
            
            // Check if similar suggestion already exists
            String sessionIdsStr = sessionIds.stream()
                .map(String::valueOf)
                .collect(Collectors.joining(","));
            
            List<AutomationSuggestion> existing = suggestionRepository.findByStatus("PENDING");
            for (AutomationSuggestion existingSuggestion : existing) {
                if (existingSuggestion.getSessionIds().equals(sessionIdsStr)) {
                    log.info("Suggestion already exists for these sessions");
                    return;
                }
            }
            
            // Create suggestion
            String[] parts = userTargetKey.split("@");
            String targetSystem = parts.length > 1 ? parts[1] : "unknown";
            
            AutomationSuggestion suggestion = AutomationSuggestion.builder()
                .sessionIds(sessionIdsStr)
                .suggestedScript(script)
                .description(description.isEmpty() ? "Automation for: " + String.join(", ", commandPattern) : description)
                .scriptType(scriptType.isEmpty() ? "bash" : scriptType.toLowerCase())
                .status("PENDING")
                .confidenceScore(calculateConfidenceScore(patternFrequency, commandPattern.size()))
                .patternFrequency(patternFrequency)
                .targetSystem(targetSystem)
                .metadata(String.format("{\"command_pattern\": %s}", commandPattern))
                .build();
            
            suggestionRepository.save(suggestion);
            
            log.info("Created automation suggestion for {} sessions on {}", patternFrequency, userTargetKey);
            
        } catch (Exception e) {
            log.error("Error saving automation suggestion", e);
        }
    }
    
    /**
     * Save RDP automation suggestion
     */
    private void saveRdpAutomationSuggestion(
            String llmResponse,
            List<Long> sessionIds,
            String userTargetKey,
            int patternFrequency) {
        
        try {
            String description = extractField(llmResponse, "DESCRIPTION:");
            String scriptType = extractField(llmResponse, "SCRIPT_TYPE:");
            String script = extractScript(llmResponse);
            
            if (script.isEmpty()) {
                return;
            }
            
            String sessionIdsStr = sessionIds.stream()
                .map(String::valueOf)
                .collect(Collectors.joining(","));
            
            String[] parts = userTargetKey.split("@");
            String targetSystem = parts.length > 1 ? parts[1] : "unknown";
            
            AutomationSuggestion suggestion = AutomationSuggestion.builder()
                .sessionIds(sessionIdsStr)
                .suggestedScript(script)
                .description(description.isEmpty() ? "RDP automation for repetitive tasks" : description)
                .scriptType(scriptType.isEmpty() ? "python" : scriptType.toLowerCase())
                .status("PENDING")
                .confidenceScore(calculateConfidenceScore(patternFrequency, 1))
                .patternFrequency(patternFrequency)
                .targetSystem(targetSystem)
                .metadata("{\"session_type\": \"rdp\"}")
                .build();
            
            suggestionRepository.save(suggestion);
            
            log.info("Created RDP automation suggestion for {} sessions on {}", patternFrequency, userTargetKey);
            
        } catch (Exception e) {
            log.error("Error saving RDP automation suggestion", e);
        }
    }
    
    /**
     * Extract a field from LLM response
     */
    private String extractField(String response, String fieldName) {
        int start = response.indexOf(fieldName);
        if (start == -1) {
            return "";
        }
        
        start += fieldName.length();
        int end = response.indexOf("\n", start);
        if (end == -1) {
            end = response.length();
        }
        
        return response.substring(start, end).trim();
    }
    
    /**
     * Extract script content from code blocks
     */
    private String extractScript(String response) {
        int start = response.indexOf("```");
        if (start == -1) {
            return "";
        }
        
        // Skip language identifier (e.g., ```bash or ```python)
        start = response.indexOf("\n", start);
        if (start == -1) {
            return "";
        }
        start++;
        
        int end = response.indexOf("```", start);
        if (end == -1) {
            return "";
        }
        
        return response.substring(start, end).trim();
    }
    
    /**
     * Calculate confidence score based on pattern frequency and complexity
     */
    private double calculateConfidenceScore(int frequency, int patternLength) {
        // Base score on frequency (0.4 to 0.7)
        double frequencyScore = Math.min(0.7, 0.4 + (frequency * 0.1));
        
        // Bonus for pattern length (up to 0.3)
        double lengthBonus = Math.min(0.3, patternLength * 0.05);
        
        return Math.min(1.0, frequencyScore + lengthBonus);
    }
    
    /**
     * Decode Base64 encoded command
     */
    private String decodeCommand(String encodedCommand) {
        try {
            byte[] decodedBytes = Base64.getDecoder().decode(encodedCommand);
            return new String(decodedBytes, StandardCharsets.UTF_8);
        } catch (Exception e) {
            log.warn("Failed to decode command: {}", encodedCommand);
            return "";
        }
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
}
