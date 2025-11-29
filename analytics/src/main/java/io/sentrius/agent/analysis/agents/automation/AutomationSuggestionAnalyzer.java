package io.sentrius.agent.analysis.agents.automation;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import io.sentrius.sso.core.dto.ztat.TokenDTO;
import io.sentrius.sso.core.exceptions.ZtatException;
import io.sentrius.sso.core.model.automation.AutomationSuggestion;
import io.sentrius.sso.core.model.metadata.TerminalCommand;
import io.sentrius.sso.core.model.metadata.TerminalSessionMetadata;
import io.sentrius.sso.core.model.sessions.RdpSessionSummary;
import io.sentrius.sso.core.repository.RdpSessionSummaryRepository;
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

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(
    name = "agents.automation-suggestion.enabled",
    havingValue = "true",
    matchIfMissing = false
)
public class AutomationSuggestionAnalyzer {

    private final TerminalSessionMetadataService sessionMetadataService;
    private final TerminalCommandService commandService;
    private final RdpSessionSummaryRepository rdpSummaryRepository;
    private final AutomationSuggestionRepository suggestionRepository;
    private final IntegrationSecurityTokenService integrationSecurityTokenService;
    private final LLMService llmService;

    private static final int MIN_PATTERN_FREQUENCY = 3;
    private static final int MIN_COMMAND_SEQUENCE_LENGTH = 2;

    /* ============================================================
       Scheduler
       ============================================================ */

    @Scheduled(fixedDelay = 21600000) // 6 hours
    @Transactional
    public void analyzeSessionsForAutomation() {
        if (!isLLMAvailable()) {
            log.debug("LLM integration not available");
            return;
        }

        analyzeTerminalSessions();
        analyzeRdpSessions();
    }

    /* ============================================================
       Terminal sessions
       ============================================================ */

    private void analyzeTerminalSessions() {
        List<TerminalSessionMetadata> sessions =
            sessionMetadataService.getSessionsByState("PROCESSED");

        if (sessions.isEmpty()) return;

        Map<String, List<TerminalSessionMetadata>> grouped =
            sessions.stream().collect(Collectors.groupingBy(
                s -> (s.getUser() != null ? s.getUser().getUsername() : "unknown")
                    + "@"
                    + (s.getHostSystem() != null ? s.getHostSystem().getHost() : "unknown")
            ));

        grouped.forEach((key, group) -> {
            if (group.size() >= MIN_PATTERN_FREQUENCY) {
                try {
                    analyzeSessionGroupForPatterns(group, key);
                } catch (ZtatException e) {
                    throw new RuntimeException(e);
                } catch (JsonProcessingException e) {
                    throw new RuntimeException(e);
                }
            }
        });
    }

    private void analyzeSessionGroupForPatterns(
        List<TerminalSessionMetadata> sessions,
        String userTargetKey
    ) throws ZtatException, JsonProcessingException {
        List<List<String>> sequences = new ArrayList<>();
        List<Long> sessionIds = new ArrayList<>();

        for (TerminalSessionMetadata session : sessions) {
            List<TerminalCommand> commands =
                commandService.getCommandsBySessionId(session.getId());

            if (commands == null || commands.isEmpty()) continue;

            List<String> decoded = commands.stream()
                .map(cmd -> decodeCommand(cmd.getCommand()))
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toList());

            if (decoded.size() >= MIN_COMMAND_SEQUENCE_LENGTH) {
                sequences.add(decoded);
                sessionIds.add(session.getId());
            }
        }

        List<String> pattern = findCommonCommandPattern(sequences);
        if (pattern.size() < MIN_COMMAND_SEQUENCE_LENGTH) return;

        generateAutomationSuggestion(pattern, sessionIds, userTargetKey, sequences.size());
    }

    /* ============================================================
       RDP sessions
       ============================================================ */

    private void analyzeRdpSessions() {
        List<RdpSessionSummary> sessions = rdpSummaryRepository.findAll();
        if (sessions.isEmpty()) return;

        Map<String, List<RdpSessionSummary>> grouped =
            sessions.stream().collect(Collectors.groupingBy(
                s -> (s.getUserIdentifier() != null ? s.getUserIdentifier() : "unknown")
                    + "@"
                    + (s.getTargetIdentifier() != null ? s.getTargetIdentifier() : "unknown")
            ));

        grouped.forEach((key, group) -> {
            if (group.size() >= MIN_PATTERN_FREQUENCY) {
                try {
                    analyzeRdpSessionGroupForPatterns(group, key);
                } catch (ZtatException e) {
                    throw new RuntimeException(e);
                } catch (JsonProcessingException e) {
                    throw new RuntimeException(e);
                }
            }
        });
    }

    private void analyzeRdpSessionGroupForPatterns(
        List<RdpSessionSummary> sessions,
        String userTargetKey
    ) throws ZtatException, JsonProcessingException {
        StringBuilder combined = new StringBuilder();
        List<Long> sessionIds = new ArrayList<>();

        for (RdpSessionSummary s : sessions) {
            if (s.getSummary() != null && !s.getSummary().isEmpty()) {
                combined.append("Session ").append(s.getId()).append(":\n")
                    .append(s.getSummary()).append("\n\n");
                sessionIds.add(s.getId());
            }
        }

        if (!combined.isEmpty()) {
            generateRdpAutomationSuggestion(
                combined.toString(),
                sessionIds,
                userTargetKey,
                sessions.size()
            );
        }
    }

    /* ============================================================
       LLM calls (Responses API)
       ============================================================ */

    private void generateAutomationSuggestion(
        List<String> pattern,
        List<Long> sessionIds,
        String userTargetKey,
        int frequency
    ) throws JsonProcessingException, ZtatException {
        var token = integrationSecurityTokenService
            .findByConnectionType("openai")
            .stream().findFirst().orElse(null);

        if (token == null) return;

        TokenDTO tokenDTO = TokenDTO.builder()
            .communicationId(UUID.randomUUID().toString())
            .ztatToken("")
            .build();

        String prompt = buildAutomationPrompt(pattern, userTargetKey, frequency);
        Map<String, Object> payload = buildResponsesPayload(
            "You are an expert in Linux system automation.",
            prompt
        );

        String raw = llmService.askQuestion(tokenDTO, payload);
        JsonNode response = JsonUtil.MAPPER.readTree(raw);

        String text = extractResponseText(response);
        if (!text.isEmpty()) {
            saveAutomationSuggestion(text, pattern, sessionIds, userTargetKey, frequency);
        }
    }

    private void generateRdpAutomationSuggestion(
        String summary,
        List<Long> sessionIds,
        String userTargetKey,
        int frequency
    ) throws JsonProcessingException, ZtatException {
        var token = integrationSecurityTokenService
            .findByConnectionType("openai")
            .stream().findFirst().orElse(null);

        if (token == null) return;

        TokenDTO tokenDTO = TokenDTO.builder()
            .communicationId(UUID.randomUUID().toString())
            .ztatToken("")
            .build();

        String prompt = buildRdpAutomationPrompt(summary, userTargetKey, frequency);
        Map<String, Object> payload = buildResponsesPayload(
            "You are an expert in Windows RDP automation.",
            prompt
        );

        String raw = llmService.askQuestion(tokenDTO, payload);
        JsonNode response = JsonUtil.MAPPER.readTree(raw);

        String text = extractResponseText(response);
        if (!text.isEmpty()) {
            saveRdpAutomationSuggestion(text, sessionIds, userTargetKey, frequency);
        }
    }

    /* ============================================================
       Responses helpers
       ============================================================ */

    private Map<String, Object> buildResponsesPayload(String system, String userPrompt) {
        return Map.of(
            "model", "gpt-4o-mini",
            "input", List.of(
                Map.of(
                    "role", "system",
                    "content", List.of(
                        Map.of("type", "text", "text", system)
                    )
                ),
                Map.of(
                    "role", "user",
                    "content", List.of(
                        Map.of("type", "text", "text", userPrompt)
                    )
                )
            ),
            "text", Map.of("verbosity", "medium"),
            "temperature", 0.3
        );
    }

    private String extractResponseText(JsonNode response) {
        JsonNode output = response.get("output");
        if (output != null && output.isArray()) {
            StringBuilder sb = new StringBuilder();
            for (JsonNode item : output) {
                if ("message".equals(item.path("type").asText())) {
                    for (JsonNode content : item.path("content")) {
                        if ("output_text".equals(content.path("type").asText())) {
                            sb.append(content.path("text").asText()).append("\n");
                        }
                    }
                }
            }
            return sb.toString().trim();
        }
        return "";
    }

    /* ============================================================
       Unchanged helpers
       ============================================================ */

    private List<String> findCommonCommandPattern(List<List<String>> sequences) {
        if (sequences.isEmpty()) return Collections.emptyList();

        Map<String, Integer> frequency = new HashMap<>();
        for (List<String> seq : sequences) {
            new HashSet<>(seq).forEach(cmd ->
                frequency.merge(cmd, 1, Integer::sum));
        }

        Set<String> common = frequency.entrySet().stream()
            .filter(e -> e.getValue() >= MIN_PATTERN_FREQUENCY)
            .map(Map.Entry::getKey)
            .collect(Collectors.toSet());

        return sequences.get(0).stream()
            .filter(common::contains)
            .collect(Collectors.toList());
    }

    private String buildAutomationPrompt(List<String> pattern, String key, int freq) {
        StringBuilder sb = new StringBuilder();
        sb.append("Observed ").append(freq)
            .append(" executions of the following command pattern on ")
            .append(key).append(":\n\n");

        for (int i = 0; i < pattern.size(); i++) {
            sb.append(i + 1).append(". ").append(pattern.get(i)).append("\n");
        }

        sb.append("""
                
                Generate:
                1. A production-ready Bash script
                2. Description
                3. Prerequisites
                4. Safety considerations

                Format:
                DESCRIPTION:
                SCRIPT_TYPE: bash
                PREREQUISITES:
                SCRIPT:
                ```bash
                ...
                ```
                SAFETY:
                """);

        return sb.toString();
    }

    private String buildRdpAutomationPrompt(String summary, String key, int freq) {
        return """
               Observed %d RDP sessions on %s with the following summaries:

               %s

               Generate:
               1. Automation script (PowerShell or Python)
               2. Description
               3. Prerequisites

               Format:
               DESCRIPTION:
               SCRIPT_TYPE:
               PREREQUISITES:
               SCRIPT:
               ```
               ...
               ```
               """.formatted(freq, key, summary);
    }

    private void saveAutomationSuggestion(
        String llmResponse,
        List<String> pattern,
        List<Long> sessionIds,
        String key,
        int frequency
    ) {
        String description = extractField(llmResponse, "DESCRIPTION:");
        String scriptType = extractField(llmResponse, "SCRIPT_TYPE:");
        String script = extractScript(llmResponse);

        if (script.isEmpty()) return;

        AutomationSuggestion suggestion = AutomationSuggestion.builder()
            .sessionIds(sessionIds.stream().map(String::valueOf).collect(Collectors.joining(",")))
            .suggestedScript(script)
            .description(description)
            .scriptType(scriptType)
            .status("PENDING")
            .confidenceScore(calculateConfidenceScore(frequency, pattern.size()))
            .patternFrequency(frequency)
            .metadata("{\"type\":\"terminal\"}")
            .build();

        suggestionRepository.save(suggestion);
    }

    private void saveRdpAutomationSuggestion(
        String llmResponse,
        List<Long> sessionIds,
        String key,
        int frequency
    ) {
        String description = extractField(llmResponse, "DESCRIPTION:");
        String scriptType = extractField(llmResponse, "SCRIPT_TYPE:");
        String script = extractScript(llmResponse);

        if (script.isEmpty()) return;

        AutomationSuggestion suggestion = AutomationSuggestion.builder()
            .sessionIds(sessionIds.stream().map(String::valueOf).collect(Collectors.joining(",")))
            .suggestedScript(script)
            .description(description)
            .scriptType(scriptType)
            .status("PENDING")
            .confidenceScore(calculateConfidenceScore(frequency, 1))
            .patternFrequency(frequency)
            .metadata("{\"type\":\"rdp\"}")
            .build();

        suggestionRepository.save(suggestion);
    }

    private String extractField(String response, String field) {
        int start = response.indexOf(field);
        if (start == -1) return "";
        start += field.length();
        int end = response.indexOf("\n", start);
        return (end == -1 ? response.substring(start) : response.substring(start, end)).trim();
    }

    private String extractScript(String response) {
        int start = response.indexOf("```");
        if (start == -1) return "";
        start = response.indexOf("\n", start);
        int end = response.indexOf("```", start);
        if (end == -1) return "";
        return response.substring(start + 1, end).trim();
    }

    private double calculateConfidenceScore(int frequency, int length) {
        return Math.min(1.0, 0.4 + (frequency * 0.1) + (length * 0.05));
    }

    private String decodeCommand(String encoded) {
        try {
            return new String(Base64.getDecoder().decode(encoded), StandardCharsets.UTF_8);
        } catch (Exception e) {
            return "";
        }
    }

    private boolean isLLMAvailable() {
        return integrationSecurityTokenService
            .findByConnectionType("openai")
            .stream().findFirst().isPresent();
    }
}
