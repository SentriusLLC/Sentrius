package io.sentrius.agent.analysis.agents.verbs;

import java.util.List;
import java.util.Map;
import io.sentrius.sso.core.dto.agents.AgentContextDTO;
import io.sentrius.sso.core.trust.ATPLPolicy;
import io.sentrius.sso.core.trust.Capability;
import io.sentrius.sso.core.trust.CapabilitySet;

public class ExampleFactory {
    public static Object createExample(String paramName, Class<?> type) {
        if (type.equals(Map.class)) {
            return Map.of("key", "value");
        }
        if (type.equals(List.class)) {
            return List.of(Map.of("key", "value"));
        }
        if (type.equals(String.class)) {
            return "{ \"" + paramName + "\" : Example String value\" }";
        }
        if (type.equals(AgentContextDTO.class)) {
            return AgentContextDTO.builder().context("This is the context for the agent").description("Agent " +
                "description").build();
        }
        if (type.equals(ATPLPolicy.class)) {
            return ATPLPolicy.builder()
                .policyId("policy-001")
                .description("Example policy")
                .version("v0")
                .capabilities(
                    CapabilitySet.builder().primitives(
                        List.of(
                            Capability.builder().description("description").endpoints(List.of(
                    "endpoint1", "endpoint2")).build()
                        )).build())
                .build();
        }
        // fallback
        return Map.of("field", "value");
    }
}
