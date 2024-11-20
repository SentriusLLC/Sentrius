package io.sentrius.sso.genai.model;

import java.util.ArrayList;
import java.util.List;
import lombok.Builder;
import lombok.Data;
import lombok.Getter;
import lombok.Setter;


@Builder
@Data
@Getter
@Setter
public class Conversation {

    List<ChatResponse> previousMessages;
    ChatResponse newUserMessage;

    @Builder.Default
    List<String> terminalMessages = new ArrayList<>();

    String systemConfines;
}
