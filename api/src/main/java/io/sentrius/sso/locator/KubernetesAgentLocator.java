package io.sentrius.sso.locator;


import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.URI;

@Component
public class KubernetesAgentLocator {

    @Value("${sentrius.agent.namespace}")
    private String agentNamespace;

    @Value("${sentrius.agent.port:8080}")
    private int agentPort;

    public URI resolveWebSocketUri(String agentId) {
        // DNS: sentrius-agent-[ID].[namespace].svc.cluster.local
        String fqdn = String.format("ws://sentrius-agent-%s.%s.svc.cluster.local:%d/ws",
            agentId, agentNamespace, agentPort);
        return URI.create(fqdn);
    }
}
