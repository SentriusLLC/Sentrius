package io.sentrius.agent.analysis.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.sentrius.sso.core.dto.UserDTO;
import io.sentrius.sso.core.dto.agents.AgentExecution;
import io.sentrius.sso.core.exceptions.ZtatException;
import io.sentrius.sso.core.services.agents.AgentExecutionService;
import io.sentrius.sso.core.services.agents.LLMService;
import io.sentrius.sso.core.utils.JsonUtil;
import io.sentrius.sso.genai.Message;
import io.sentrius.sso.genai.model.LLMRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Service for analyzing agent execution logs and generating human-readable summaries using LLM.
 */
@Slf4j
@Service
public class AgentExecutionSummarizerService {

    private static final int MAX_LOG_LENGTH = 5000; // Maximum log characters to send to LLM

    private final LLMService llmService;
    private final AgentExecutionService agentExecutionService;

    @Autowired
    public AgentExecutionSummarizerService(LLMService llmService, AgentExecutionService agentExecutionService) {
        this.llmService = llmService;
        this.agentExecutionService = agentExecutionService;
    }

    /**
     * Analyze agent execution and generate a summary using LLM.
     *
     * @param executionId The execution ID
     * @param agentId     The agent ID (e.g., pod name)
     * @param agentType   The type of agent
     * @param podLogs     The pod logs to analyze
     * @return Map containing status, summary, resourceLinks, and exitCode
     */
    public Map<String, Object> summarizeExecution(String executionId, String agentId, 
                                                   String agentType, String podLogs) {
        log.info("Summarizing agent execution using LLM: {}, agent: {}, type: {}", executionId, agentId, agentType);

        Map<String, Object> result = new HashMap<>();

        try {
            // Get agent execution context for authentication
            UserDTO systemUser = UserDTO.builder()
                .username("analytics-agent")
                .build();
            AgentExecution agentExecution = agentExecutionService.getAgentExecution(systemUser);
            agentExecution.setCommunicationId(UUID.randomUUID().toString());

            // Use LLM to analyze the logs
            String llmResponse = analyzeLogsWithLLM(agentExecution, agentType, agentId, podLogs);
            
            // Parse LLM response
            Map<String, Object> parsedResponse = parseLLMResponse(llmResponse);
            
            result.putAll(parsedResponse);

            log.info("Successfully summarized execution {} using LLM", executionId);
        } catch (ZtatException e) {
            log.error("ZTAT error summarizing execution with LLM: {}", executionId, e);
            // Fallback to basic extraction if LLM fails
            result.put("status", determineStatusFromLogs(podLogs));
            result.put("summary", "Agent execution completed. LLM unavailable for detailed summary.");
            result.put("resourceLinks", serializeResourceLinks(extractResourceLinks(podLogs)));
            result.put("exitCode", extractExitCode(podLogs));
        } catch (JsonProcessingException e) {
            log.error("JSON processing error summarizing execution with LLM: {}", executionId, e);
            // Fallback to basic extraction if LLM fails
            result.put("status", determineStatusFromLogs(podLogs));
            result.put("summary", "Agent execution completed. Error parsing LLM response.");
            result.put("resourceLinks", serializeResourceLinks(extractResourceLinks(podLogs)));
            result.put("exitCode", extractExitCode(podLogs));
        } catch (Exception e) {
            log.error("Error summarizing execution with LLM: {}", executionId, e);
            // Fallback to basic extraction if LLM fails
            result.put("status", determineStatusFromLogs(podLogs));
            result.put("summary", "Agent execution completed. Error generating detailed summary: " + e.getMessage());
            result.put("resourceLinks", serializeResourceLinks(extractResourceLinks(podLogs)));
            result.put("exitCode", extractExitCode(podLogs));
        }

        return result;
    }

    /**
     * Use LLM to analyze agent logs and generate a structured summary.
     */
    private String analyzeLogsWithLLM(AgentExecution agentExecution, String agentType,
                                       String agentId, String podLogs) throws ZtatException, JsonProcessingException {
        // Construct the prompt for LLM
        String prompt = buildAnalysisPrompt(agentType, agentId, podLogs);
        
        // Create LLM request
        Message message = Message.builder()
            .role("user")
            .content(prompt)
            .build();
        
        LLMRequest request = LLMRequest.builder()
            .model("gpt-4.1")
            .messages(List.of(message))
            .maxTokens(1000)
            .temperature(0.3f) // Lower temperature for more consistent analysis
            .build();
        
        // Call LLM service
        String response = llmService.askQuestion(agentExecution, request);
        
        log.debug("LLM response received for execution analysis");
        return response;
    }

    /**
     * Build the prompt for LLM analysis.
     */
    private String buildAnalysisPrompt(String agentType, String agentId, String podLogs) {
        // Truncate logs if too long (keep last N characters which are usually most relevant)
        String truncatedLogs = podLogs.length() > MAX_LOG_LENGTH ? 
            "...[truncated]...\n" + podLogs.substring(podLogs.length() - MAX_LOG_LENGTH) : podLogs;
        
        return String.format("""
            Analyze the following agent execution logs and provide a structured summary in JSON format.
            
            Agent Type: %s
            Agent ID: %s
            
            Logs:
            ```
            %s
            ```
            
            Provide your analysis in the following JSON format:
            {
              "status": "COMPLETED|FAILED|ERROR|RUNNING",
              "summary": "A concise human-readable summary (2-3 sentences) of what the agent did",
              "resourceLinks": [
                {"type": "issue|pull_request|documentation|link", "url": "https://...", "label": "Display label"}
              ],
              "exitCode": 0 or null if not available,
              "operations": ["list of key operations performed"]
            }
            
            Guidelines:
            - Status should be COMPLETED if successful, FAILED if errors occurred, ERROR if exceptions, RUNNING if incomplete
            - Summary should be concise, professional, and describe the agent's actions
            - Extract any URLs mentioned in logs as resourceLinks
            - Identify the exit code if mentioned
            - List 3-5 key operations the agent performed
            
            Return ONLY the JSON, no additional text.
            """, agentType, agentId, truncatedLogs);
    }

    /**
     * Parse the LLM response and extract structured data.
     */
    private Map<String, Object> parseLLMResponse(String llmResponse) {
        Map<String, Object> result = new HashMap<>();
        
        try {
            if (llmResponse == null || llmResponse.trim().isEmpty()) {
                log.warn("Empty LLM response received");
                return getDefaultResult();
            }

            // Clean up the response - handle escaped strings
            String cleanedResponse = cleanLLMResponse(llmResponse);

            log.debug("Cleaned LLM response (first 500 chars): {}",
                cleanedResponse.length() > 500 ? cleanedResponse.substring(0, 500) : cleanedResponse);

            // Parse the outer response structure first
            JsonNode rootNode = JsonUtil.MAPPER.readTree(cleanedResponse);

            // Extract the actual content from LLM response structure
            String content = extractContentFromResponse(rootNode, cleanedResponse);

            // Extract JSON from markdown code blocks if present
            content = extractJsonFromMarkdown(content);
            
            // Clean up any remaining escape sequences
            content = unescapeJsonString(content);

            // Try to find JSON object in the content
            content = extractJsonObject(content);

            if (content == null || content.trim().isEmpty()) {
                log.warn("Could not extract JSON from LLM response");
                return getDefaultResult();
            }

            // Parse the actual analysis JSON
            JsonNode analysisNode = JsonUtil.MAPPER.readTree(content);
            
            result.put("status", analysisNode.has("status") ? 
                analysisNode.get("status").asText() : "COMPLETED");
            result.put("summary", analysisNode.has("summary") ? 
                analysisNode.get("summary").asText() : "Agent execution completed.");
            
            // Handle resource links
            if (analysisNode.has("resourceLinks") && analysisNode.get("resourceLinks").isArray()) {
                result.put("resourceLinks", analysisNode.get("resourceLinks").toString());
            } else {
                result.put("resourceLinks", null);
            }
            
            result.put("exitCode", analysisNode.has("exitCode") && !analysisNode.get("exitCode").isNull() ? 
                analysisNode.get("exitCode").asInt() : null);
                
        } catch (Exception e) {
            log.error("Failed to parse LLM response, using defaults", e);
            log.debug("Raw LLM response that failed to parse: {}",
                llmResponse != null && llmResponse.length() > 1000 ? llmResponse.substring(0, 1000) : llmResponse);
            return getDefaultResult();
        }

        return result;
    }

    /**
     * Clean up LLM response - handle various escape sequences and formats
     */
    private String cleanLLMResponse(String response) {
        if (response == null) return "";

        String cleaned = response.trim();

        // If the response starts with a backslash, it might be an escaped JSON string
        if (cleaned.startsWith("\\")) {
            // Try to unescape
            cleaned = unescapeJsonString(cleaned);
        }

        // If response is wrapped in quotes (double-stringified JSON)
        if (cleaned.startsWith("\"") && cleaned.endsWith("\"")) {
            try {
                // Parse as a string first to unescape it
                cleaned = JsonUtil.MAPPER.readValue(cleaned, String.class);
            } catch (Exception e) {
                // If that fails, just remove the quotes
                cleaned = cleaned.substring(1, cleaned.length() - 1);
            }
        }

        return cleaned;
    }

    /**
     * Extract content from various LLM response structures
     */
    private String extractContentFromResponse(JsonNode rootNode, String originalResponse) {
        // Try standard OpenAI response format
        if (rootNode.has("choices") && rootNode.get("choices").isArray() &&
            rootNode.get("choices").size() > 0) {
            JsonNode messageNode = rootNode.get("choices").get(0).get("message");
            if (messageNode != null && messageNode.has("content")) {
                return messageNode.get("content").asText();
            }
        }

        // Try OpenAI Responses API format (output array)
        if (rootNode.has("output") && rootNode.get("output").isArray()) {
            StringBuilder content = new StringBuilder();
            for (JsonNode item : rootNode.get("output")) {
                if ("message".equals(item.path("type").asText())) {
                    for (JsonNode contentItem : item.path("content")) {
                        if ("output_text".equals(contentItem.path("type").asText())) {
                            content.append(contentItem.path("text").asText());
                        }
                    }
                }
            }
            if (content.length() > 0) {
                return content.toString();
            }
        }

        // Try direct content field
        if (rootNode.has("content")) {
            return rootNode.get("content").asText();
        }

        // Try text field
        if (rootNode.has("text")) {
            return rootNode.get("text").asText();
        }

        // If the root node itself looks like our analysis response, return original
        if (rootNode.has("status") || rootNode.has("summary")) {
            return originalResponse;
        }

        // Return original response as-is
        return originalResponse;
    }

    /**
     * Unescape JSON string escape sequences
     */
    private String unescapeJsonString(String input) {
        if (input == null) return null;

        return input
            .replace("\\n", "\n")
            .replace("\\r", "\r")
            .replace("\\t", "\t")
            .replace("\\\"", "\"")
            .replace("\\'", "'")
            .replace("\\\\", "\\");
    }

    /**
     * Extract JSON object from a string that might contain non-JSON text
     */
    private String extractJsonObject(String content) {
        if (content == null) return null;

        content = content.trim();

        // If it already starts with {, assume it's JSON
        if (content.startsWith("{")) {
            return content;
        }

        // Try to find JSON object in the content
        int braceStart = content.indexOf('{');
        int braceEnd = content.lastIndexOf('}');

        if (braceStart >= 0 && braceEnd > braceStart) {
            return content.substring(braceStart, braceEnd + 1);
        }

        return content;
    }

    /**
     * Get default result when parsing fails
     */
    private Map<String, Object> getDefaultResult() {
        Map<String, Object> result = new HashMap<>();
        result.put("status", "COMPLETED");
        result.put("summary", "Agent execution completed. Summary unavailable.");
        result.put("resourceLinks", null);
        result.put("exitCode", null);
        return result;
    }

    /**
     * Extract JSON from markdown code blocks.
     */
    private String extractJsonFromMarkdown(String content) {
        // Remove markdown code block markers if present
        if (content.contains("```json")) {
            int start = content.indexOf("```json") + 7;
            int end = content.lastIndexOf("```");
            if (end > start) {
                content = content.substring(start, end).trim();
            }
        } else if (content.contains("```")) {
            int start = content.indexOf("```") + 3;
            int end = content.lastIndexOf("```");
            if (end > start) {
                content = content.substring(start, end).trim();
            }
        }
        return content.trim();
    }


    /**
     * Generate a human-readable summary of the agent execution.
     */
    private String generateSummary(String agentType, String agentId, String podLogs) {
        List<String> summaryParts = new ArrayList<>();

        // Agent type-specific summaries
        switch (agentType) {
            case "chat-helper":
                summaryParts.add("Chat helper agent executed successfully.");
                if (containsIgnoreCase(podLogs, "user_message") || containsIgnoreCase(podLogs, "message")) {
                    summaryParts.add("Processed user chat messages and provided conversational assistance.");
                }
                break;
            case "coding":
                summaryParts.add("Coding agent executed.");
                if (containsIgnoreCase(podLogs, "code") || containsIgnoreCase(podLogs, "function")) {
                    summaryParts.add("Analyzed and generated code snippets.");
                }
                break;
            case "mcp":
                summaryParts.add("MCP agent executed.");
                if (containsIgnoreCase(podLogs, "mcp") || containsIgnoreCase(podLogs, "protocol")) {
                    summaryParts.add("Handled MCP protocol operations.");
                }
                break;
            case "agent-summarizer":
                summaryParts.add("Agent summarizer executed.");
                summaryParts.add("Generated execution summaries for other agents.");
                break;
            case "analytics":
                summaryParts.add("Analytics agent executed.");
                if (containsIgnoreCase(podLogs, "analysis") || containsIgnoreCase(podLogs, "metric")) {
                    summaryParts.add("Performed analytics and metric collection.");
                }
                break;
            default:
                summaryParts.add(String.format("Agent of type '%s' executed.", agentType));
                break;
        }

        // Analyze logs for key activities
        if (containsIgnoreCase(podLogs, "error") && containsIgnoreCase(podLogs, "exception")) {
            summaryParts.add("Encountered errors during execution.");
        } else if (containsIgnoreCase(podLogs, "success") || containsIgnoreCase(podLogs, "completed")) {
            summaryParts.add("Execution completed successfully.");
        }

        // Extract key operations from logs
        List<String> operations = extractOperations(podLogs);
        if (!operations.isEmpty()) {
            summaryParts.add(String.format("Performed %d operation(s): %s", 
                operations.size(), String.join(", ", operations.subList(0, Math.min(3, operations.size())))));
            if (operations.size() > 3) {
                summaryParts.add(String.format("and %d more", operations.size() - 3));
            }
        }

        // Check for provenance events
        if (containsIgnoreCase(podLogs, "provenance") || containsIgnoreCase(podLogs, "event")) {
            summaryParts.add("Submitted provenance events for audit trail.");
        }

        // Basic fallback summary
        if (summaryParts.size() == 1) {
            summaryParts.add("Agent execution completed with standard operations.");
        }

        return String.join(" ", summaryParts);
    }

    /**
     * Extract operation names from logs.
     */
    private List<String> extractOperations(String podLogs) {
        List<String> operations = new ArrayList<>();
        String[] operationKeywords = {
            "initialized", "connected", "processed", "analyzed", "generated",
            "submitted", "fetched", "updated", "created", "executed"
        };

        String[] lines = podLogs.split("\n");
        for (String line : lines) {
            String lineLower = line.toLowerCase();
            for (String keyword : operationKeywords) {
                if (lineLower.contains(keyword) && !operations.contains(keyword)) {
                    operations.add(keyword);
                    break;
                }
            }
            if (operations.size() >= 5) break; // Limit to 5 operations
        }

        return operations;
    }

    /**
     * Extract resource links from logs.
     */
    private List<Map<String, String>> extractResourceLinks(String podLogs) {
        List<Map<String, String>> resourceLinks = new ArrayList<>();

        // Look for URLs in logs
        Pattern urlPattern = Pattern.compile("https?://[^\\s<>\"{}|\\\\^`\\[\\]]+");
        Matcher matcher = urlPattern.matcher(podLogs);

        int count = 0;
        while (matcher.find() && count < 5) { // Limit to 5 URLs
            String url = matcher.group();
            Map<String, String> resource = new HashMap<>();

            // Try to determine the type of resource
            String resourceType = "link";
            String label = url;

            if (url.contains("github.com")) {
                if (url.contains("/issues/")) {
                    resourceType = "issue";
                    String issueNum = url.substring(url.lastIndexOf('/') + 1);
                    label = "GitHub Issue: " + issueNum;
                } else if (url.contains("/pull/")) {
                    resourceType = "pull_request";
                    String prNum = url.substring(url.lastIndexOf('/') + 1);
                    label = "GitHub PR: " + prNum;
                } else {
                    resourceType = "repository";
                    label = "GitHub Repository";
                }
            } else if (url.contains("docs.") || url.contains("/docs/")) {
                resourceType = "documentation";
                label = "Documentation";
            }

            resource.put("type", resourceType);
            resource.put("url", url);
            resource.put("label", label);
            resourceLinks.add(resource);
            count++;
        }

        return resourceLinks;
    }

    /**
     * Fallback: Determine execution status from logs when LLM is unavailable.
     */
    private String determineStatusFromLogs(String podLogs) {
        String logsLower = podLogs.toLowerCase();

        // Check for error indicators
        if ((logsLower.contains("error") || logsLower.contains("exception") || logsLower.contains("failed"))) {
            if (logsLower.contains("traceback") || logsLower.contains("stacktrace")) {
                return "FAILED";
            }
            return "ERROR";
        }

        // Check for completion indicators
        if (logsLower.contains("completed successfully") || logsLower.contains("execution completed")) {
            return "COMPLETED";
        }

        // Check for running indicators
        if (logsLower.contains("running") || logsLower.contains("in progress")) {
            return "RUNNING";
        }

        // Default to completed if no clear indicators
        return "COMPLETED";
    }

    /**
     * Extract exit code from logs.
     */
    private Integer extractExitCode(String podLogs) {
        String logsLower = podLogs.toLowerCase();

        // Look for exit code patterns
        String[] exitPatterns = {
            "exit\\s+code[:\\s]+(\\d+)",
            "exitcode[:\\s]+(\\d+)",
            "return\\s+code[:\\s]+(\\d+)",
            "status[:\\s]+(\\d+)"
        };

        for (String patternStr : exitPatterns) {
            Pattern pattern = Pattern.compile(patternStr);
            Matcher matcher = pattern.matcher(logsLower);
            if (matcher.find()) {
                try {
                    return Integer.parseInt(matcher.group(1));
                } catch (NumberFormatException e) {
                    // Continue to next pattern
                }
            }
        }

        // If completed successfully, assume exit code 0
        if (logsLower.contains("completed successfully")) {
            return 0;
        }

        return null;
    }

    /**
     * Serialize resource links to JSON string.
     */
    private String serializeResourceLinks(List<Map<String, String>> resourceLinks) {
        try {
            return JsonUtil.MAPPER.writeValueAsString(resourceLinks);
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize resource links", e);
            return null;
        }
    }

    /**
     * Case-insensitive contains check.
     */
    private boolean containsIgnoreCase(String str, String searchStr) {
        return str != null && searchStr != null && str.toLowerCase().contains(searchStr.toLowerCase());
    }
}
