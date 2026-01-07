package io.sentrius.agent.launcher.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.kubernetes.client.custom.IntOrString;
import io.kubernetes.client.custom.Quantity;
import io.kubernetes.client.openapi.ApiClient;
import io.kubernetes.client.openapi.ApiException;
import io.kubernetes.client.openapi.apis.CoreV1Api;
import io.kubernetes.client.openapi.models.*;
import io.kubernetes.client.util.Config;
import io.sentrius.agent.launcher.model.ImageIntent;
import io.sentrius.agent.launcher.model.ResourcesConfig;
import io.sentrius.sso.core.dto.AgentRegistrationDTO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Service
public class PodLauncherService {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    
    private final CoreV1Api coreV1Api;
    private final AgentImageResolver imageResolver;

    @Value("${sentrius.agent.registry}")
    private String agentRegistry;

    @Value("${sentrius.agent.namespace}")
    private String agentNamespace;

    @Value("${sentrius.agent.registry.version}")
    private String agentVersion;

    @Value("${sentrius.agent.callback.format.url:http://sentrius-agent-%s.%s.svc.cluster.local:8090}")
    private String callbackFormatUrl;


    Pattern pattern = Pattern.compile("^service-account-(.*?)-[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$");


    @Autowired
    public PodLauncherService(AgentImageResolver imageResolver) throws IOException {
        ApiClient client = Config.defaultClient(); // in-cluster or kubeconfig
        this.coreV1Api = new CoreV1Api(client);
        this.imageResolver = imageResolver;
    }

    private String buildAgentCallbackUrl(String agentId) {
        return String.format(callbackFormatUrl, agentId, agentNamespace);
    }

    public List<V1Pod> listAgentPods() throws Exception {
        var response = coreV1Api.listNamespacedPod(
            agentNamespace
        );
        return response.execute().getItems();
    }

    public void deleteAllAgentPods() throws Exception {
        var pods = listAgentPods();
        for (V1Pod pod : pods) {
            String podName = pod.getMetadata().getName();
            String agentId = pod.getMetadata().getLabels().get("agentId");

            log.info("Deleting agent pod: {}", podName);
            coreV1Api.deleteNamespacedPod(podName, agentNamespace).execute();

            String serviceName = "sentrius-agent-" + agentId;
            log.info("Deleting agent service: {}", serviceName);
            try {
                coreV1Api.deleteNamespacedService(serviceName, agentNamespace).execute();
            } catch (Exception ex) {
                log.warn("Could not delete service {}: {}", serviceName, ex.getMessage());
            }
        }
    }

    public void deleteAgentById(String agentId) throws Exception {
        // Delete all pods with this agentId label
        var pods = coreV1Api.listNamespacedPod(
            agentNamespace
        ).execute().getItems();

        for (V1Pod pod : pods) {

            var labels = pod.getMetadata().getLabels();
            var podName = pod.getMetadata().getName();

            Matcher matcher = pattern.matcher(agentId);

            if (matcher.matches() && labels != null && labels.containsKey("agentId")) {
                String name = matcher.group(1);

                var value = labels.get("agentId");
                if (value.equals(name)) {
                    log.info("Deleting pod: {}", podName);
                    coreV1Api.deleteNamespacedPod(podName, agentNamespace).execute();
                    String serviceName = "sentrius-agent-" + agentId;
                    log.info("Deleting service: {}", serviceName);
                    try {
                        coreV1Api.deleteNamespacedService(serviceName, agentNamespace).execute();
                    } catch (Exception ex) {
                        log.warn("Service not found or already deleted: {}", ex.getMessage());
                    }
                } else {
                    log.info("Not Deleting pod: {}", podName);
                }


            } else {
                log.info("Pod {} does not match agentId pattern or has no agentId label, skipping deletion", podName);
            }


        }
    }


        public String statusById(String agentId) throws Exception {
            // Delete all pods with this agentId label
            var pods = coreV1Api.listNamespacedPod(
                agentNamespace
            ).execute().getItems();

            for (V1Pod pod : pods) {

                var labels = pod.getMetadata().getLabels();
                var podName = pod.getMetadata().getName();

                Matcher matcher = pattern.matcher(agentId);

                if (matcher.matches() && labels != null && labels.containsKey("agentId")) {
                    String name = matcher.group(1);

                    var value = labels.get("agentId");
                    if (value.equals(name)) {
                        // get pod status
                        //
                        V1PodStatus status = pod.getStatus();
                        if (status == null) {
                            log.warn("Pod {} has no status information", podName);
                            return "Unknown";
                        }
                        return status.getPhase(); // e.g., "Running", "Pending", "Failed", "Succeeded"

                    }


                }


            }
            return "NotFound";
        }






    public V1Pod launchAgentPod(AgentRegistrationDTO agent) throws Exception {
        String agentId = agent.getAgentName().toLowerCase();
        String callbackUrl = agent.getAgentCallbackUrl();
        String agentType = agent.getAgentType();

        var constructedCallbackUrl = buildAgentCallbackUrl(agentId);


        List<String> argList = new ArrayList<>();
        argList.add("--spring.config.location=file:/config/agent.properties");
        argList.add("--agent.namePrefix=" + agentId);
        argList.add("--agent.type=" + agentType);
        argList.add("--agent.clientId=" + agent.getClientId());
        argList.add("--agent.listen.websocket=true");
        argList.add("--agent.callback.url=" + constructedCallbackUrl);
        if (agent.getAgentPolicyId() != null && !agent.getAgentPolicyId().isEmpty()) {
            argList.add("--agent.ai.policy.id=" + agent.getAgentPolicyId());
        }
        if (agent.getAgentContextId() != null && !agent.getAgentContextId().isEmpty()) {
            argList.add("--agent.ai.context.db.id=" + agent.getAgentContextId());
        }else {
            String agentFile= "chat-helper.yaml";
            switch(agentType){
                case "chat":
                    agentFile = "chat-helper.yaml";
                    break;
                case "atpl-helper":
                    agentFile = "chat-atpl-helper.yaml";
                    break;
                case "abac":
                    agentFile = "abac-helper.yaml";
                    break;
                case "default":
                default:
                    agentFile = "chat-helper.yaml";
            }
            argList.add("--agent.ai.config=/config/" + agentFile);
        }

        log.info("Agent {} using config file: {}", agentId, argList);

        // Use image resolver to determine the correct image
        String image = imageResolver.resolveImage(agent);

        log.info("Launching agent pod with ID: {}, Image: {}, Callback URL: {}", agentId, image, callbackUrl);
        
        // Parse resources from templateLaunchConfiguration if available
        Map<String, Quantity> resourceLimits = parseResourceLimits(agent);
        
        V1Pod pod = new V1Pod()
            .metadata(new V1ObjectMeta()
                .generateName("sentrius-agent-")
                .labels(Map.of("agentId", agentId)))
            .spec(new V1PodSpec()
                .containers(List.of(new V1Container()
                    .name("agent")
                    .image(image)
                    .imagePullPolicy("IfNotPresent")

                    .args(argList)
                    .resources(new V1ResourceRequirements()
                        .limits(resourceLimits))
                        .volumeMounts(List.of(
                            new V1VolumeMount()
                                .name("config-volume")
                                .mountPath("/config/")
                        ))
                    )
                )
                .restartPolicy("Never")
                .volumes(List.of(
                    new V1Volume()
                        .name("config-volume")
                        .configMap(new V1ConfigMapVolumeSource()
                            .name("sentrius-agents-config")
                        )
                )));
        pod.getSpec().setOverhead(null);

        var createdPod = coreV1Api.createNamespacedPod(agentNamespace, pod).execute();

        try {
            // Create corresponding service for WebSocket routing
            V1Service service = new V1Service()
                .metadata(new V1ObjectMeta()
                    .name("sentrius-agent-" + agentId)
                    .labels(Map.of("agentId", agentId)))
                .spec(new V1ServiceSpec()
                    .selector(Map.of("agentId", agentId))
                    .ports(List.of(new V1ServicePort()
                        .protocol("TCP")
                        .port(8090)
                        .targetPort(new IntOrString(8090))
                    ))
                    .type("ClusterIP")
                );

            log.info("Created service pod: {} and service {}", createdPod, service);
            coreV1Api.createNamespacedService(agentNamespace, service).execute();

        }catch(ApiException e){
            if (e.getCode() == 409){
                log.info("Service for agent {} already exists, skipping creation", agentId);
            }
            else{
                throw e;
            }
        }
        return createdPod;
    }

    /**
     * Parse resource limits from agent's templateLaunchConfiguration
     * Falls back to default values if not specified
     */
    private Map<String, Quantity> parseResourceLimits(AgentRegistrationDTO agent) {
        Map<String, Quantity> limits = new HashMap<>();
        
        // Default values
        String defaultCpu = "2000m";
        String defaultMemory = "2Gi";
        
        String launchConfig = agent.getTemplateLaunchConfiguration();
        if (launchConfig != null && !launchConfig.trim().isEmpty()) {
            try {
                LaunchConfiguration config = OBJECT_MAPPER.readValue(launchConfig, LaunchConfiguration.class);
                
                if (config.getResources() != null) {
                    ResourcesConfig resources = config.getResources();
                    
                    if (resources.getCpu() != null) {
                        limits.put("cpu", Quantity.fromString(resources.getCpu()));
                        log.info("Using CPU limit from template: {}", resources.getCpu());
                    } else {
                        limits.put("cpu", Quantity.fromString(defaultCpu));
                    }
                    
                    if (resources.getMemory() != null) {
                        limits.put("memory", Quantity.fromString(resources.getMemory()));
                        log.info("Using memory limit from template: {}", resources.getMemory());
                    } else {
                        limits.put("memory", Quantity.fromString(defaultMemory));
                    }
                    
                    return limits;
                }
            } catch (Exception e) {
                log.warn("Failed to parse resource limits from templateLaunchConfiguration for agent {}: {}", 
                    agent.getAgentName(), e.getMessage());
            }
        }
        
        // Use defaults
        limits.put("cpu", Quantity.fromString(defaultCpu));
        limits.put("memory", Quantity.fromString(defaultMemory));
        return limits;
    }
    
    /**
     * Wrapper class for parsing launch configuration JSON
     */
    @lombok.Data
    @com.fasterxml.jackson.annotation.JsonIgnoreProperties(ignoreUnknown = true)
    private static class LaunchConfiguration {
        private ImageIntent imageIntent;
        private ResourcesConfig resources;
        private String restartPolicy;
    }

}
