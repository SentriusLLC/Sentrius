package io.sentrius.sso.core.services.agents;

import io.sentrius.sso.core.exceptions.ZtatException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class LLMService {

    final ZeroTrustClientService zeroTrustClientService;

    @Value("${agent.open.ai.endpoint:http://localhost:8080}")
    private String openAiEndpoint;

    public LLMService(ZeroTrustClientService zeroTrustClientService) {
        this.zeroTrustClientService = zeroTrustClientService;
    }

    /**
     * Request a Zero Trust Access Token (ZTAT) using Keycloak JWT and `ZtatRequestDTO`
     */
    public <T> String askQuestion(T body) throws ZtatException {
        return zeroTrustClientService.callPostOnApi(openAiEndpoint, "/chat/completions", body);
    }

}
