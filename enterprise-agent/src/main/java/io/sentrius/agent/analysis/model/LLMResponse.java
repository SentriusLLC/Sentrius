package io.sentrius.agent.analysis.model;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.core.JsonParser;
import io.sentrius.sso.core.utils.JsonUtil;
import io.sentrius.sso.genai.Response;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

@Data
@Builder
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Slf4j
public class LLMResponse {
    String previousOperation;
    String nextOperation;
    String memoryLookup;
    String summaryForLLM;
    String responseForUser;
    
    /**
     * Tracks the current status of plan execution.
     * Valid values: "idle", "in_progress", "completed", "awaiting_input"
     * - "idle": No plan is currently being executed
     * - "in_progress": A plan is being actively executed
     * - "completed": The plan has been successfully executed
     * - "awaiting_input": The plan requires user input to continue
     */
    @Builder.Default
    String planStatus = "idle";
    
    /**
     * List of operations that have been executed in the current conversation/plan.
     * This helps the LLM understand what has already been done and avoid repeating actions.
     */
    @Builder.Default
    List<String> executedOperations = new ArrayList<>();
    
    @Builder.Default
    public Map<String, Object> arguments = new HashMap<>();
    
    /**
     * Checks if the response indicates that no further action is needed.
     * This is true when:
     * - nextOperation is null or empty
     * - planStatus is "completed" or "idle"
     * - The response is purely conversational (greeting, thanks, etc.)
     */
    @JsonIgnore
    public boolean isConversationalOnly() {
        return (nextOperation == null || nextOperation.isEmpty()) &&
               ("completed".equals(planStatus) || "idle".equals(planStatus));
    }
    
    /**
     * Checks if the current response requires action execution.
     * Returns false if:
     * - nextOperation is null or empty
     * - planStatus is "completed" (task finished)
     * - planStatus is "awaiting_input" (waiting for user input before continuing)
     */
    @JsonIgnore
    public boolean requiresExecution() {
        return nextOperation != null && !nextOperation.isEmpty() &&
               !"completed".equals(planStatus) &&
               !"awaiting_input".equals(planStatus);
    }

    public void setArguments(Map<String, Object> arguments) {
        log.info("Setting arguments: {}", arguments);
        if (arguments != null) {
            this.arguments = arguments.entrySet().stream()
                .collect(java.util.stream.Collectors.toMap(
                    Map.Entry::getKey,
                    entry -> {
                        Object v = entry.getValue();
                        return (v instanceof String str)
                            ? str.trim().replaceAll("^[\"']|[\"']$", "")
                            : v;
                    }
                ));
        } else {
            this.arguments = Map.of();
        }
    }

    public static Optional<LLMResponse> extractStructuredResponse(Response response) {

        if (response.getOutputItems() == null) {
            return Optional.empty();
        }

        // Walk from end → start (latest > earliest)
        for (int i = response.getOutputItems().size() - 1; i >= 0; i--) {
            Response.OutputItem item = response.getOutputItems().get(i);

            if (item.getContent() == null) continue;

            for (Response.ContentItem content : item.getContent()) {

                // ✅ Trust the type, not markdown
                if (!"output_text".equals(content.getType())) {
                    continue;
                }

                String text = content.getText();
                if (text == null || text.isBlank()) continue;

                // ✅ Assume this is raw JSON
                try {
                    return Optional.of(
                        JsonUtil.MAPPER
                            .enable(JsonParser.Feature.ALLOW_COMMENTS)
                            .readValue(text, LLMResponse.class)
                    );
                } catch (Exception ignored) {
                    // Not structured JSON, keep scanning
                }
            }
        }

        return Optional.empty();
    }

    public static String extractStructuredResponseString(Response response) {

        if (response.getOutputItems() == null) {
            return "";
        }

        // Walk from end → start (latest > earliest)
        for (int i = response.getOutputItems().size() - 1; i >= 0; i--) {
            Response.OutputItem item = response.getOutputItems().get(i);

            if (item.getContent() == null) continue;

            for (Response.ContentItem content : item.getContent()) {

                // ✅ Trust the type, not markdown
                if (!"output_text".equals(content.getType())) {
                    continue;
                }

                String text = content.getText();
                if (text == null || text.isBlank()) continue;

                // ✅ Assume this is raw JSON
                try {
                    return text;
                } catch (Exception ignored) {
                    // Not structured JSON, keep scanning
                }
            }
        }

        return "";
    }

}
