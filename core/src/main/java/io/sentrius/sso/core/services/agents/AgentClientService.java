package io.sentrius.sso.core.services.agents;

import java.util.List;
import com.google.common.collect.Maps;
import io.sentrius.sso.core.exceptions.ZtatException;
import io.sentrius.sso.core.services.security.KeycloakService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

@Slf4j
@Service
public class AgentClientService {

    final ZeroTrustClientService zeroTrustClientService;

    private final RestTemplate restTemplate = new RestTemplate();

    public AgentClientService(ZeroTrustClientService zeroTrustClientService, KeycloakService keycloakService) {
        this.zeroTrustClientService = zeroTrustClientService;
    }



    /**
     * Request a Zero Trust Access Token (ZTAT) using Keycloak JWT and `ZtatRequestDTO`
     */
    public String heartbeat(String name) throws ZtatException {

        String url =  "/agent/heartbeat";

        return zeroTrustClientService.callPutOnApi(url, Maps.immutableEntry("status", List.of("heartbeat")), Maps.immutableEntry(
            "name", List.of(name)));

    }


    /**
     * Request a Zero Trust Access Token (ZTAT) using Keycloak JWT and `ZtatRequestDTO`
     */
    public String getAtatRequests() throws ZtatException {

        String url =  "/zerotrust/accesstoken/list/atat";

        return zeroTrustClientService.callGetOnApi(url);

    }

}
