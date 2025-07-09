package io.sentrius.agent.analysis.agents.agents;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.stream.Collectors;
import io.sentrius.agent.analysis.agents.verbs.ExampleFactory;
import io.sentrius.sso.core.utils.JsonUtil;

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

    public String buildPrompt(){
        return buildPrompt(true);
    }

    /**
     * Builds a prompt string that includes roles, context, instructions, and available verbs.
     *
     * @return A formatted prompt string.
     */
    public String buildPrompt(boolean applyInstructions)
    {
        StringBuilder prompt = new StringBuilder();

        // Append roles to the prompt
        //        prompt.append("Roles: ").append(String.join(", ", agentConfig.getRoles())).append("\n");

        // Append context to the prompt
        prompt.append("Context: ").append(agentConfig.getContext()).append("\n\n");

        if (applyInstructions) {
            // Append instructions for using the JSON format
            prompt.append("Instructions: ").append("Respond using this JSON format. Only use verbs provided in " +
                "Available Verbs. Formulate a complete plan with all possible steps.:\n" +
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
                "}\n");
        }
            // Append the list of available verbs
            prompt.append("Verb operations:\n");

            // Iterate through the verbs in the registry and append their details
            verbRegistry.getVerbs().forEach((name, verb) -> {
                prompt.append("- ").append(name);
                prompt.append(" (").append(buildMethodSignature(verb.getMethod())).append(") - ");
                prompt.append(verb.getDescription()).append("\n");
                // Optionally generate example params based on arg1 class
                Class<?>[] paramTypes = verb.getMethod().getParameterTypes();

                if (paramTypes.length > 1 && !paramTypes[1].equals(Void.class)) {
                    var paramName = verb.getMethod().getParameters()[1].getName();
                    Object example = ExampleFactory.createExample(paramName, paramTypes[1]);  // create a stub from
                    // your DTO
                    try {
                        if (verb.getExampleJson() != null && !verb.getExampleJson().isEmpty()) {
                            prompt.append("  Example arg1: ").append(verb.getExampleJson()).append("\n");
                        } else if (example != null) {
                            // Serialize the example object to JSON
                            String exampleJson = JsonUtil.MAPPER.writeValueAsString(example);
                            prompt.append("  Example arg1: ").append(exampleJson).append("\n");
                        }

                    } catch (Exception e) {
                        prompt.append("  Example params: [unavailable due to serialization error]\n");
                    }
                } else {
                    prompt.append("  Example params: {}\n");
                }
            });

        return prompt.toString();
    }

    public static String indent(String input, int spaces) {
        String indent = " ".repeat(spaces);
        return Arrays.stream(input.split("\n"))
            .map(line -> indent + line)
            .collect(Collectors.joining("\n"));
    }


    /**
     * Builds a method signature string for a given method.
     *
     * @param method The method for which the signature is to be built.
     * @return A string representing the method signature, including parameter names and types.
     */
    private String buildMethodSignature(Method method) {
        return Arrays.stream(method.getParameters())
            .filter( p -> !p.getType().getSimpleName().equalsIgnoreCase("TokenDTO") &&
            !p.getType().getSimpleName().equalsIgnoreCase("AgentExecution"))
            .map(p -> p.getName() + ": " + p.getType().getSimpleName())
            .collect(Collectors.joining(", "));
    }
}