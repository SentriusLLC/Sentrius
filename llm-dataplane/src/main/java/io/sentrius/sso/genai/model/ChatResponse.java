package io.sentrius.sso.genai.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;

@Getter
@Data
@Setter
@Builder
@AllArgsConstructor
@RequiredArgsConstructor
public class ChatResponse {
    private String role;
    private String content;
    private String terminalMessage;
    @Builder.Default
    private boolean alert = false;
}
