package io.sentrius.agent.analysis.agents.agents;

import java.lang.reflect.Method;
import java.util.List;
import io.sentrius.sso.core.dto.capabilities.ParameterDescriptor;
import lombok.Builder;
import lombok.Getter;

@Builder
@Getter
public class AgentVerb {
    private String name;
    private String description;
    private Method method;
    private List<ParameterDescriptor> paramDescriptions;
    private boolean isAiCallable = true;
    @Builder.Default
    private boolean requiresTokenManagement = false;
    @Builder.Default
    private Class<?> returnType =  String.class;

    @Builder.Default
    private String returnName =  "";

    private String exampleJson = "";
    @Builder.Default
    private String argName = "arg1";
}