package io.sentrius.agent.analysis.agents.agents;

import java.lang.reflect.Method;
import io.sentrius.sso.core.model.verbs.DefaultInterpreter;
import io.sentrius.sso.core.model.verbs.InputInterpreterIfc;
import io.sentrius.sso.core.model.verbs.OutputInterpreterIfc;
import lombok.Builder;
import lombok.Getter;

@Builder
@Getter
public class AgentVerb {
    private String name;
    private String description;
    private Method method;
    @Builder.Default
    private boolean requiresTokenManagement = false;
    @Builder.Default
    private Class<?> returnType =  String.class;
    @Builder.Default
    Class<? extends OutputInterpreterIfc> outputInterpreter = DefaultInterpreter.class;
    Class<? extends InputInterpreterIfc> inputInterpreter = DefaultInterpreter.class;
}