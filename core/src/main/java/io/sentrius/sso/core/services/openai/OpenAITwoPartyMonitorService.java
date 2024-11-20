package io.sentrius.sso.core.services.openai;


import java.util.concurrent.CompletableFuture;
import com.fasterxml.jackson.core.JsonProcessingException;
import io.sentrius.sso.core.model.LLMResponse;
import io.sentrius.sso.core.model.security.IntegrationSecurityToken;
import io.sentrius.sso.core.services.IntegrationSecurityTokenService;
import io.sentrius.sso.core.utils.JsonUtil;
import io.sentrius.sso.integrations.external.ExternalIntegrationDTO;
import io.sentrius.sso.genai.GenerativeAPI;
import io.sentrius.sso.genai.GeneratorConfiguration;
import io.sentrius.sso.genai.OpenAITwoPartyMonitor;
import io.sentrius.sso.genai.TerminalLogConfiguration;
import io.sentrius.sso.genai.model.TwoPartyRequest;
import io.sentrius.sso.security.ApiKey;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@Slf4j
@RequiredArgsConstructor
public class OpenAITwoPartyMonitorService implements io.sentrius.sso.core.services.PluggableServices {

    final IntegrationSecurityTokenService integrationSecurityTokenService;

    IntegrationSecurityToken openAiToken = null;

    OpenAITwoPartyMonitor terminalComplianceScorer = null;

    public String getName() {
        return "openaitwoparty";
    }

    @Override
    public boolean isEnabled() {
        if (null == openAiToken) {
            synchronized (this) {
                if (null == openAiToken) {
                    log.info("setting open ai token");
                    openAiToken = integrationSecurityTokenService.findByConnectionType("openai").stream().findFirst().orElse(null);
                    if (openAiToken == null) {
                        log.info("no integration");
                        return false;
                    }
                    ExternalIntegrationDTO externalIntegrationDTO = null;
                    try {
                        externalIntegrationDTO = JsonUtil.MAPPER.readValue(openAiToken.getConnectionInfo(),
                            ExternalIntegrationDTO.class);
                    } catch (JsonProcessingException e) {
                        throw new RuntimeException(e);
                    }
                    ApiKey key =
                        ApiKey.builder().apiKey(externalIntegrationDTO.getApiToken()).principal(externalIntegrationDTO.getUsername()).build();
                    terminalComplianceScorer = new OpenAITwoPartyMonitor(key, new GenerativeAPI(key),
                        GeneratorConfiguration.builder().build(), TerminalLogConfiguration.builder().build()
                    );
                }
            }
        }
        log.info("openai enabled: " + (openAiToken != null));
        return openAiToken != null;
    }

    // Asynchronous method for scoring terminal commands
    public CompletableFuture<LLMResponse> analyzeTerminalLogs(TwoPartyRequest terminalLog) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                log.info("analyzing terminal log {}", (terminalComplianceScorer == null) );
                // Submit terminal log to OpenAI
                var response =  terminalComplianceScorer.generate(terminalLog);
                log.info("score: {}", response.getScore());
                // Return true if malicious (e.g., score > 0.8)
                return response;
            } catch (Exception e) {
                log.info("Failed to analyze terminal log", e);
                e.printStackTrace();
                throw new RuntimeException("Failed to analyze terminal log", e);
            }
        });
    }
}
