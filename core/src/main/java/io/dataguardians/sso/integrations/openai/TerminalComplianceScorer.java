package io.dataguardians.sso.integrations.openai;

import io.dataguardians.security.TokenProvider;

/**
 * Query compliance scorer with user defined rules to be provided to OpenAI
 */
public class TerminalComplianceScorer extends ComplianceScorer {

    public TerminalComplianceScorer(
        TokenProvider token, GenerativeAPI generator, GeneratorConfiguration config,
        TerminalLogConfiguration complianceConfig) {
        super(token, generator, config, complianceConfig);
    }

    /**
     * Generates input for the generative AI endpoint.
     *
     * @return Question to be asked to the generative AI endpoint.
     */
    @Override
    public String generateInput(String on) {
        String queryStr = "This is a mission critical system with admins performing break glass activities through " +
            "ssh.";

        TerminalLogConfiguration queryComplianceConfiguration = (TerminalLogConfiguration) complianceConfig;
        for (var rule : queryComplianceConfiguration.getRules())
            queryStr += rule.getRule() + ",";

        queryStr += ". Can you give me a confidence score from 0 to 1 on whether the next 10 terminal log output from" +
            " a single terminal session are exhibiting risky behavior: "
            + on + ";  Please only provide the score. ";

        return queryStr;
    }

}