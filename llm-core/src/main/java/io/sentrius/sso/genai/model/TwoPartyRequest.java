package io.sentrius.sso.genai.model;

import lombok.Builder;
import lombok.Getter;

@Builder
@Getter
public class TwoPartyRequest {
    @Builder.Default
    private String systemInput = "This is a mission critical system with admins performing break glass activities " +
        "through ssh. You are monitoring as a two party system and acting as a second human monitoring a " +
        "session.  ";

    private String whatSystemIsDoing;
    private String userObjective;
    private String userInput;

    private String previousPrompt;
    private String promptResponse;

}
