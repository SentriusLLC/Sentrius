package io.dataguardians.sso.integrations.openai;

import com.fasterxml.jackson.core.JsonProcessingException;
import io.dataguardians.security.TokenProvider;
import io.dataguardians.sso.integrations.exceptions.HttpException;
import io.dataguardians.sso.integrations.openai.model.endpoints.ChatApiEndpointRequest;
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
public abstract class ComplianceScorer extends DataGenerator<String, Double> {

    protected ComplianceConfiguration complianceConfig;

    public ComplianceScorer(TokenProvider token, GenerativeAPI generator, GeneratorConfiguration config, ComplianceConfiguration complianceConfig) {
        super(token, generator, config);
        this.complianceConfig = complianceConfig;
    }

    /**
     * Parses queries from the response.
     *
     * @return List of queries.
     */
    @Override
    public Double generate(String on) throws HttpException, JsonProcessingException {
        ChatApiEndpointRequest request = ChatApiEndpointRequest.builder().userInput(generateInput(on)).build();
        log.info("Generating compliance score for: " + on);
        request.setTemperature(0.5f);
        Response hello = api.sample(request, Response.class);
        try{
        var dbl = Double.valueOf(hello.concatenateResponses());
        return dbl;
        } catch (NumberFormatException e) {
            log.info("Error parsing compliance score: " + hello.concatenateResponses());
            return 0.0;
        }
    }
}
