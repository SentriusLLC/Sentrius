package io.sentrius.agent.analysis.model;

import java.util.HashMap;
import java.util.Map;
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
    @Builder.Default
    public Map<String, Object> arguments = new HashMap<>();

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

}
