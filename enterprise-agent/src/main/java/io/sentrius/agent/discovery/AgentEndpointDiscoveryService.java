package io.sentrius.agent.discovery;

import io.sentrius.agent.analysis.agents.agents.AgentVerb;
import io.sentrius.agent.config.AgentConfigOptions;
import io.sentrius.sso.core.dto.capabilities.EndpointDescriptor;
import io.sentrius.sso.core.dto.agents.AgentExecution;
import io.sentrius.sso.core.exceptions.ZtatException;
import io.sentrius.sso.core.services.agents.ZeroTrustClientService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class AgentEndpointDiscoveryService {

    private final AgentConfigOptions agentConfigOptions;
    private final RestTemplate restTemplate = new RestTemplate();
    private final ZeroTrustClientService zeroTrustClientService;

    // Simulated verb registry for example
    private final Map<String, AgentVerb> verbs = new HashMap<>();

    public Map<String, AgentVerb> discoverEndpoints(AgentExecution agentExecution) {
        String discoveryUrl = agentConfigOptions.getEndpoints() + "?type=VERB";

        log.info("Querying discovery endpoint: {}", discoveryUrl);

        try {

            List<EndpointDescriptor> descriptors = zeroTrustClientService.callGetOnApi(agentExecution, discoveryUrl);
            if (descriptors == null || descriptors.isEmpty()) {
                log.info("No endpoints discovered from capabilities API");
                return verbs;
            }

            List<EndpointDescriptor> verbEndpoints = descriptors.stream()
                .filter(d -> "VERB".equalsIgnoreCase(d.getType()))
                .toList();

            log.info("Discovered {} VERB endpoints", verbEndpoints.size());

            for (EndpointDescriptor endpoint : verbEndpoints) {
                if (!verbs.containsKey(endpoint.getName())) {
                    log.warn("Discovered verb '{}' not registered in agent", endpoint.getName());
                    // You could also register it here dynamically if desired
                    verbs.put(endpoint.getName(), convertToVerbDefinition(endpoint));
                }
            }

        } catch (Exception e) {
            log.error("Failed to discover endpoints: {}", e.getMessage(), e);
        } catch (ZtatException e) {
            throw new RuntimeException(e);
        }
        return verbs;
    }

    private AgentVerb convertToVerbDefinition(EndpointDescriptor descriptor) {
        return AgentVerb.builder()
            .name(descriptor.getName())
            .description(descriptor.getDescription())
            .returnType(descriptor.getReturnType() != null ? descriptor.getReturnType() : String.class)
            .requiresTokenManagement(descriptor.isRequiresTokenManagement())
            .paramDescriptions(descriptor.getParameters())
            .isAiCallable(true) // Assume everything from the API is callable
            .build();
    }
}
