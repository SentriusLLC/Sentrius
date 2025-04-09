package io.sentrius.agent.analysis.agents.agents;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.stream.Collectors;

/**
 * The `PromptBuilder` class is responsible for constructing a prompt string
 * based on the roles, context, and available verbs provided by the `AgentConfig`
 * and `VerbRegistry` objects.
 */
public class PromptBuilder {

    private final VerbRegistry verbRegistry;
    private final AgentConfig agentConfig;

    /**
     * Constructs a `PromptBuilder` instance with the specified `VerbRegistry` and `AgentConfig`.
     *
     * @param verbRegistry The registry containing available verbs and their metadata.
     * @param agentConfig The configuration object containing roles and context information.
     */
    public PromptBuilder(VerbRegistry verbRegistry, AgentConfig agentConfig) {
        this.verbRegistry = verbRegistry;
        this.agentConfig = agentConfig;
    }

    /**
     * Builds a prompt string that includes roles, context, instructions, and available verbs.
     *
     * @return A formatted prompt string.
     */
    public String buildPrompt() {
        StringBuilder prompt = new StringBuilder();

        // Append roles to the prompt
        prompt.append("Roles: ").append(String.join(", ", agentConfig.getRoles())).append("\n");

        // Append context to the prompt
        prompt.append("Context: ").append(agentConfig.getContext()).append("\n\n");

        // Append instructions for using the JSON format
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

        // Append the list of available verbs
        prompt.append("Available Verbs:\n");

        // Iterate through the verbs in the registry and append their details
        verbRegistry.getVerbs().forEach((name, verb) -> {
            prompt.append("- ").append(name);
            prompt.append(" (").append(buildMethodSignature(verb.getMethod())).append(") - ");
            prompt.append(verb.getDescription()).append(")\n");
        });

        return prompt.toString();
    }

    /**
     * Builds a method signature string for a given method.
     *
     * @param method The method for which the signature is to be built.
     * @return A string representing the method signature, including parameter names and types.
     */
    private String buildMethodSignature(Method method) {
        return Arrays.stream(method.getParameters())
            .map(p -> p.getName() + ": " + p.getType().getSimpleName())
            .collect(Collectors.joining(", "));
    }
}