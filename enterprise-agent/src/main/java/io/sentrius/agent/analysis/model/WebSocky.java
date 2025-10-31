package io.sentrius.agent.analysis.model;

import java.util.ArrayList;
import java.util.List;
import io.sentrius.sso.core.dto.HostSystemDTO;
import io.sentrius.sso.core.dto.agents.AgentExecutionContextDTO;
import io.sentrius.sso.core.model.verbs.VerbResponse;
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
public class WebSocky {
    HostSystemDTO host;
    String sessionId;
    Long uniqueIdentifier;
    WebSocketSession webSocketSession;
    @Builder.Default
    List<LLMResponse> communicationResponses = new ArrayList<>();

    @Builder.Default
    List<VerbResponse> verbResponses = new ArrayList<>();

    @Builder.Default
    AgentExecutionContextDTO agentExecutionContextDTO = AgentExecutionContextDTO.builder()
            .build();

}
