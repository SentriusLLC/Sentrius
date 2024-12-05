package io.dataguardians.sso.core.services.openai;


import java.util.concurrent.CompletableFuture;
import com.fasterxml.jackson.core.JsonProcessingException;
import io.dataguardians.sso.core.model.LLMResponse;
import io.dataguardians.sso.core.model.security.IntegrationSecurityToken;
import io.dataguardians.sso.core.services.IntegrationSecurityTokenService;
import io.dataguardians.sso.core.utils.JsonUtil;
import io.dataguardians.sso.integrations.external.ExternalIntegrationDTO;
import io.dataguardians.sso.integrations.openai.ApiKey;
import io.dataguardians.sso.integrations.openai.GenerativeAPI;
import io.dataguardians.sso.integrations.openai.GeneratorConfiguration;
import io.dataguardians.sso.integrations.openai.OpenAITwoPartyMonitor;
import io.dataguardians.sso.integrations.openai.TerminalComplianceScorer;
import io.dataguardians.sso.integrations.openai.TerminalLogConfiguration;
import io.dataguardians.sso.integrations.openai.model.TwoPartyRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@Slf4j
@RequiredArgsConstructor
public class OpenAITwoPartyMonitorService implements io.dataguardians.sso.core.services.PluggableServices {

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
