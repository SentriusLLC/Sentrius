package io.sentrius.agent.analysis.model;

import java.util.List;
import io.sentrius.sso.genai.Message;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Data
@Builder
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class AssessedTerminal {
    Assessment assessment;
    List<Message> messages;

}
