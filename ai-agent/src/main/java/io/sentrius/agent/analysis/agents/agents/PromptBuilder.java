package io.sentrius.agent.analysis.agents.agents;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.stream.Collectors;

public class PromptBuilder {

    private final VerbRegistry verbRegistry;
    private final AgentConfig agentConfig;

    public PromptBuilder(VerbRegistry verbRegistry, AgentConfig agentConfig) {
        this.verbRegistry = verbRegistry;
        this.agentConfig = agentConfig;
    }

    public String buildPrompt() {
        StringBuilder prompt = new StringBuilder();

        //prompt.append("Agent Name: ").append(agentConfig.getAgentName()).append("\n");
        //prompt.append("Purpose: ").append(agentConfig.getPurpose()).append("\n");
        prompt.append("Roles: ").append(String.join(", ", agentConfig.getRoles())).append("\n");
        prompt.append("Context: ").append(agentConfig.getContext()).append("\n\n");
        prompt.append("Instructions: ").append("Respond using this JSON format. Only use verbs provided in " +
            "Available Verbs:\n" +
            "\n" +
            "{\n" +
            "  \"plan\": [\n" +
            "    {\n" +
            "      \"verb\": \"list_open_terminals\",\n" +
            "      \"params\": {}\n" +
            "    },\n" +
            "    {\n" +
            "      \"verb\": \"send_terminal_command\",\n" +
            "      \"params\": {}\n" +
            "    }\n" +
            "  ]\n" +
            "}\n" );
        prompt.append("Available Verbs:\n");

        verbRegistry.getVerbs().forEach((name, method) -> {
            prompt.append("- ").append(name);
            prompt.append(" (").append(buildMethodSignature(method)).append(")\n");
        });

        return prompt.toString();
    }

    private String buildMethodSignature(Method method) {
        return Arrays.stream(method.getParameters())
            .map(p -> p.getName() + ": " + p.getType().getSimpleName())
            .collect(Collectors.joining(", "));
    }
}
