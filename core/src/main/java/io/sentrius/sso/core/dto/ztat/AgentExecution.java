package io.sentrius.sso.core.dto.ztat;

import java.util.ArrayList;
import java.util.List;
import io.sentrius.sso.core.dto.UserDTO;
import io.sentrius.sso.genai.Message;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

@EqualsAndHashCode(callSuper = true)
@Data
@Getter
@Setter
@SuperBuilder
public class AgentExecution extends TokenDTO {
    UserDTO user;
    String executionId;
    @Builder.Default
    List<Message> messages = new ArrayList<>();

    public void addMessages(List<Message> messages) {
        if (messages == null){
            messages = new ArrayList<>();
        }

        this.messages.addAll(messages);

    }

    public void addMessages(Message message) {
        this.messages.add(message);

    }
}
