package io.sentrius.sso.core.model.verbs;

import java.util.List;
import io.sentrius.sso.genai.Message;
import lombok.Builder;
import lombok.Data;
import lombok.Getter;

@Builder
@Data
@Getter
public class VerbResponse {
    private List<Message> messages;
    private Object response;
    private Class<?> returnType;
    @Builder.Default
    private Class<?extends OutputInterpreterIfc> outputInterpreter = DefaultInterpreter.class;
}
