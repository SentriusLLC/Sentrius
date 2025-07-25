package io.sentrius.agent.analysis.model;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import io.sentrius.sso.core.dto.HostSystemDTO;
import io.sentrius.sso.genai.Message;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.socket.WebSocketSession;

@Data
@Builder
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Slf4j
public class TerminalResponse {
    String previousOperation;
    String nextOperation;
    String terminalSummaryForLLM;
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
