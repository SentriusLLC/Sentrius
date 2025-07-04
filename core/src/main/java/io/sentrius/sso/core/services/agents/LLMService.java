package io.sentrius.sso.core.services.agents;

import io.sentrius.sso.core.dto.ztat.TokenDTO;
import io.sentrius.sso.core.exceptions.ZtatException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.stereotype.Service;

@Service
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
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
    public <T> String askQuestion(TokenDTO dto, T body) throws ZtatException {
        return zeroTrustClientService.callPostOnApi(dto, openAiEndpoint, "/chat/completions", body);
    }

}
