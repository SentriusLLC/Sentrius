package io.sentrius.sso.genai;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.sentrius.sso.core.model.categorization.CommandCategory;
import io.sentrius.sso.core.utils.JsonUtil;
import io.sentrius.sso.genai.model.endpoints.ChatApiEndpointRequest;
import io.sentrius.sso.integrations.exceptions.HttpException;
import io.sentrius.sso.security.TokenProvider;
import lombok.extern.slf4j.Slf4j;

/**
 * The ComplianceScorer class contains methods for generating compliance scores.
 * The generate() method returns a double that represents the compliance score.
 *
 * This class can be used to efficiently score compliance in various domains, including but not limited to
 * healthcare, finance, and government regulations.
 *
 * It is recommended to initialize the ComplianceScorer with relevant settings and parameters for a specific
 * compliance scenario. The generate() method can then be called repeatedly on incoming data to obtain
 * compliance scores in real-time.
 *
 * Note: This class does not handle data storage, retrieval or manipulation. It is only intended for
 * calculating compliance scores based on input data.
 */
@Slf4j
public class LLMCommandCategorizer extends DataGenerator<String, CommandCategory> {

    public LLMCommandCategorizer(TokenProvider token, GenerativeAPI generator, GeneratorConfiguration config) {
        super(token, generator, config);
    }

    /**
     * Parses queries from the response.
     *
     * @return List of queries.
     */
    @Override
    public CommandCategory generate(String on) throws HttpException, JsonProcessingException {
        var ipt = generateInput(on);
        log.info("Input: {}", ipt);
        ChatApiEndpointRequest request = ChatApiEndpointRequest.builder().userInput(ipt).build();
        request.setTemperature(0.5f);
        Response hello = api.sample(request, Response.class);
        var resp = hello.concatenateResponses();
        log.info("Response: {}", resp);
        var objectNode = JsonUtil.MAPPER.readValue(resp, ObjectNode.class);
        return CommandCategory.builder().categoryName(objectNode.get("category").asText()).pattern(objectNode.get(
            "pattern").asText()).priority(objectNode.get("priority").asInt()).build();
    }

    /**
     * Generates input for the generative AI endpoint.
     *
     * @return Question to be asked to the generative AI endpoint.
     */
    @Override
    public String generateInput(String on) {
        return """
        Categorize the following command with a generalized pattern, defined as a **regex**, that captures the intent and considers risk factors. Include specific arguments or paths in the regex only if they significantly impact the risk level. If no regex is predefined for the command, generate one that appropriately generalizes the command's behavior while retaining any risk-relevant components.
                
                For example:
                - 'cat /etc/passwd' is risky due to sensitive user data, so it should be included as-is with the regex '^cat /etc/passwd$'.
                - 'cat /etc/hosts' has low risk, so it should be generalized to '^cat /etc/.*$' to cover all files in the `/etc` directory.
                - 'sudo rm -rf /important_dir' should include '/important_dir' if it's sensitive, but otherwise generalized to '^sudo rm -rf .*'.
                
                Command: "%s"
                
                **Categories to choose from:**
                - PRIVILEGED: Commands that require elevated permissions or pose a risk if misused.
                - DESTRUCTIVE: Commands that can delete or alter critical files or system configurations.
                - INFORMATIONAL: Commands that retrieve information without altering the system state.
                - GENERAL: Commands that do not fit into the above categories.
                
                Respond in **JSON format** as follows:
                {
                    "category": "<Category Name>",
                    "priority": <Numerical Priority>,
                    "pattern": "<Generalized regex Pattern>",
                    "rationale": "<Explain why this category and regex were chosen>"
                }
                
    """.formatted(on);
    }

}
