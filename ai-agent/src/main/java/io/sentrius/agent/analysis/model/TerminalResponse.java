package io.sentrius.agent.analysis.model;

import java.util.ArrayList;
import java.util.List;
import io.sentrius.sso.core.dto.HostSystemDTO;
import io.sentrius.sso.genai.Message;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.web.socket.WebSocketSession;

@Data
@Builder
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class TerminalResponse {
    String previousOperation;
    String nextOperation;
    String terminalSummaryForLLM;
    String responseForUser;
}
