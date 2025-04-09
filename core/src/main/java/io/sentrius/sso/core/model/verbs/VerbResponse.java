package io.sentrius.sso.core.model.verbs;

import lombok.Builder;
import lombok.Data;
import lombok.Getter;

@Builder
@Data
@Getter
public class VerbResponse {
    private Object response;
    private Class<?> returnType;
    @Builder.Default
    private Class<?extends OutputInterpreterIfc> outputInterpreter = DefaultInterpreter.class;
}
