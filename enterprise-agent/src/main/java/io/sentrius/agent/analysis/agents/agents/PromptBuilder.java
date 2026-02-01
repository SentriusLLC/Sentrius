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
            prompt.append("STRICT RESPONSE PROTOCOL\n" +
                "You are participating in a machine-executed planning protocol.\n\n" +

                "RULES:\n" +
                "- You MUST respond with EXACTLY ONE valid JSON object.\n" +
                "- You MUST NOT include prose, explanations, markdown, or conversational text outside JSON.\n" +
                "- You MUST ONLY use verbs explicitly listed in Available Verbs.\n" +
                "- You MUST produce a complete execution plan when possible.\n" +
                "- If required information is missing, you MUST return an EMPTY plan and explain why USING ONLY the JSON fields.\n" +
                "- Any response that is not valid JSON MUST be considered a failure.\n\n" +

                "OUTPUT SCHEMA (MANDATORY):\n" +
                "{\n" +
                "  \"plan\": [\n" +
                "    {\n" +
                "      \"verb\": \"<verb_name_from_available_verbs>\",\n" +
                "      \"params\": { <verb_parameters> }\n" +
                "    }\n" +
                "  ]\n" +
                "}\n\n" +

                "FAILURE MODE:\n" +
                "- If no verbs are required, return: { \"plan\": [] }\n" +
                "- NEVER ask questions.\n" +
                "- NEVER explain outside the JSON structure.\n"
            );
        }
            // Append verb discovery instructions instead of full verb list
            prompt.append("VERB DISCOVERY:\n");
            prompt.append("The system has 75+ verbs organized by category (slack, k8s, llm, mcp, jira, teams, etc.).\n");
            prompt.append("Use the following verb lookup verbs to discover what operations are available:\n\n");

            // Only include the verb lookup verbs in the prompt
            String[] verbLookupVerbs = {
                "search_verbs",
                "get_verbs_by_category",
                "get_verb_summary",
                "get_verb_details",
                "find_verbs_by_intent"
            };

            for (String verbName : verbLookupVerbs) {
                AgentVerb verb = verbRegistry.getVerbs().get(verbName);
                if (verb != null) {
                    prompt.append("- ").append(verbName);
                    prompt.append(" (").append(buildMethodSignature(verb.getMethod())).append(") - ");
                    prompt.append(verb.getDescription()).append("\n");

                    if (verb.getExampleJson() != null && !verb.getExampleJson().isEmpty()) {
                        prompt.append("  Example: ").append(verb.getExampleJson()).append("\n");
                    } else {
                        prompt.append("  Example params: {}\n");
                    }
                }
            }

            prompt.append("\nWORKFLOW:\n");
            prompt.append("1. Use search_verbs or d to discover verbs for your task\n");
            prompt.append("2. Use get_verb_details to see full details about a specific verb\n");
            prompt.append("3. Execute the discovered verb with appropriate parameters\n");
            prompt.append("4. All discovered verbs can be used in your plan\n\n");

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