package io.sentrius.sso.core.services.agents;

import java.time.Duration;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.google.common.collect.Maps;
import io.sentrius.sso.core.dto.AgentCommunicationDTO;
import io.sentrius.sso.core.dto.AgentHeartbeatDTO;
import io.sentrius.sso.core.dto.AgentRegistrationDTO;
import io.sentrius.sso.core.dto.ztat.AgentExecution;
import io.sentrius.sso.core.dto.ztat.AtatRequest;
import io.sentrius.sso.core.dto.ztat.TokenDTO;
import io.sentrius.sso.core.dto.ztat.ZtatRequestDTO;
import io.sentrius.sso.core.exceptions.ZtatException;
import io.sentrius.sso.core.services.security.KeycloakService;
import io.sentrius.sso.core.utils.JsonUtil;
import io.sentrius.sso.provenance.ProvenanceEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

@Slf4j
@Service
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public class AgentClientService {

    final ZeroTrustClientService zeroTrustClientService;


    @Value("${agent.callback.url:http://localhost:8080}")
    private String callbackUrl;

    private final RestTemplate restTemplate = new RestTemplate();

    public AgentClientService(ZeroTrustClientService zeroTrustClientService, KeycloakService keycloakService) {
        this.zeroTrustClientService = zeroTrustClientService;
    }



    /**
     * Request a Zero Trust Access Token (ZTAT) using Keycloak JWT and `ZtatRequestDTO`
     */
    public String heartbeat(TokenDTO token, String name) throws ZtatException {

        String url =  "/agent/heartbeat";

        AgentHeartbeatDTO heartbeatDTO = AgentHeartbeatDTO.builder().name(name).status("heartbeat").agentUrl(callbackUrl).build();

        return zeroTrustClientService.callPostOnApi(token, url, heartbeatDTO);

    }


    /**
     * Request a Zero Trust Access Token (ZTAT) using Keycloak JWT and `ZtatRequestDTO`
     */
    public String getAtatRequests(TokenDTO token) throws ZtatException {

        String url =  "/zerotrust/accesstoken/list/atat";

        return zeroTrustClientService.callGetOnApi(token, url);

    }

    public Set<String> getCommunicationIds(AgentExecution execution, ZtatRequestDTO atatRequest)
        throws ZtatException, JsonProcessingException {

        String responseUrl = "/agent/chat/atat/links";
        var response = zeroTrustClientService.callGetOnApi(execution, responseUrl,
            Maps.immutableEntry("requestId", List.of(atatRequest.getRequestId())));
        if (response != null) {
            log.info("response is {}", response);
            return JsonUtil.MAPPER.readValue(
                response,
                new TypeReference<>() {
                }
            );
        }



        return Set.of();
    }

    public void submitProvenance(AgentExecution execution, ProvenanceEvent event){
        String url = "/agent/provenance/submit";

        try {
            zeroTrustClientService.callPostOnApi(execution, url, event);
        } catch (ZtatException e) {
            log.error("Failed to submit provenance event: {}", e.getMessage());
        }
    }

    public List<AgentCommunicationDTO> getResponse(AgentExecution execution, AtatRequest atatRequest,
                                                  AgentCommunicationDTO lastCommunication,
                                                  long timeToWait, TimeUnit timeUnit)
        throws ZtatException, JsonProcessingException {

        String responseUrl = "/agent/chat/atat/next";
        var millis = Duration.of(timeToWait, timeUnit.toChronoUnit()).toMillis();
        while(millis > 0){
            log.info("millis {}", millis);
            var response = zeroTrustClientService.callGetOnApi(execution, responseUrl, Maps.immutableEntry("id",
                List.of(lastCommunication.getId().toString())));
            if (response != null) {
                log.info("response is {}", response);
                return JsonUtil.MAPPER.readValue(
                    response,
                    new TypeReference<>() {
                    }
                );
            }
            millis -= 200;
            try {
                Thread.sleep(200);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException(e);
            }
        }
        return List.of();
    }

    public List<AgentCommunicationDTO> getResponse(AgentExecution execution, ZtatRequestDTO ztatRequest,
                                                   long timeToWait, TimeUnit timeUnit)
        throws ZtatException, JsonProcessingException {

        String responseUrl = "/agent/chat/atat/first";
        var millis = Duration.of(timeToWait, timeUnit.toChronoUnit()).toMillis();
        while(millis > 0){
            String response = zeroTrustClientService.callGetOnApi(execution, responseUrl);
            if (response != null) {
                return JsonUtil.MAPPER.readValue(
                    response,
                    new TypeReference<>() {
                    }
                );
            }
            millis -= 200;
            try {
                if (millis > 0) {
                    Thread.sleep(200);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException(e);
            }
        }
        return List.of();
    }

    public AgentCommunicationDTO askAgent(AgentExecution execution, AtatRequest atatRequest, String payload)
        throws ZtatException, TimeoutException, JsonProcessingException {
        // pose a question to the user and then wait for a response a reasonable amount of time.
        String ask = "/agent/chat/atat/send";


        AgentCommunicationDTO myQuestion = AgentCommunicationDTO.builder()
            .payload(payload)
            .messageType("atat_chat_ask")
            .sourceAgent(execution.getUser().getUsername())
            .targetAgent(atatRequest.getUserName())
            .build();
        log.info("My question is {}",myQuestion);

        var acommResponse = zeroTrustClientService.callPostOnApi(execution, ask, myQuestion,
            Maps.immutableEntry("requestId", List.of(atatRequest.getRequestId())));
        return JsonUtil.MAPPER.readValue(acommResponse, AgentCommunicationDTO.class);
    }

    public AgentCommunicationDTO sendResponse(AgentExecution execution, AgentCommunicationDTO response,
                                              ZtatRequestDTO ztatRequest
    )
        throws ZtatException, TimeoutException, JsonProcessingException {
        // pose a question to the user and then wait for a response a reasonable amount of time.
        String ask = "/agent/chat/atat/send";

        var acommResponse = zeroTrustClientService.callPostOnApi(execution, ask, response,
            Maps.immutableEntry("requestId", List.of(ztatRequest.getRequestId())));
        return JsonUtil.MAPPER.readValue(acommResponse, AgentCommunicationDTO.class);
    }

    public AgentRegistrationDTO bootstrap(String name, String publicKey, String keyType)
        throws ZtatException, JsonProcessingException {
        String ask = "/agent/bootstrap/register";

        AgentRegistrationDTO registration = AgentRegistrationDTO.builder()
            .agentName(name)
            .agentCallbackUrl(getCallbackUrl())
            .agentPublicKey(publicKey)
            .agentPublicKeyAlgo(keyType)
            .build();

        var acommResponse = zeroTrustClientService.callPostOnApi(ask, registration);
        return JsonUtil.MAPPER.readValue(acommResponse, AgentRegistrationDTO.class);
    }

    public String getCallbackUrl() {
        return callbackUrl;
    }

}
