package io.sentrius.agent.launcher.service;

import io.kubernetes.client.custom.IntOrString;
import io.kubernetes.client.custom.Quantity;
import io.kubernetes.client.openapi.ApiClient;
import io.kubernetes.client.openapi.apis.CoreV1Api;
import io.kubernetes.client.openapi.models.*;
import io.kubernetes.client.util.Config;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Service
public class PodLauncherService {

    private final CoreV1Api coreV1Api;

    @Value("${sentrius.agent.registry}")
    private String agentRegistry;

    @Value("${sentrius.agent.namespace}")
    private String agentNamespace;

    @Value("${sentrius.agent.registry.version}")
    private String agentVersion;

    @Value("${sentrius.agent.callback.format.url:http://sentrius-agent-%s.%s.svc.cluster.local:8090}")
    private String callbackFormatUrl;


    Pattern pattern = Pattern.compile("^service-account-(.*?)-[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$");


    public PodLauncherService() throws IOException {
        ApiClient client = Config.defaultClient(); // in-cluster or kubeconfig
        this.coreV1Api = new CoreV1Api(client);
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
                }else {
                    log.info("Not Deleting pod: {}", podName);
                }


            } else {
                log.info("Pod {} does not match agentId pattern or has no agentId label, skipping deletion", podName);
            }


        }


    }


    public V1Pod launchAgentPod(String agentId, String callbackUrl) throws Exception {
        var myAgentRegistry = "";
        if (agentRegistry != null ) {
            if ("local".equalsIgnoreCase(agentRegistry)) {
                myAgentRegistry = "";
            } else if (!agentRegistry.endsWith("/")) {
                myAgentRegistry += "/";
            }
        }

        var constructedCallbackUrl = buildAgentCallbackUrl(agentId);

        String image = String.format("%ssentrius-launchable-agent:%s", myAgentRegistry, agentVersion);

        log.info("Launching agent pod with ID: {}, Image: {}, Callback URL: {}", agentId, image, callbackUrl);
        V1Pod pod = new V1Pod()
            .metadata(new V1ObjectMeta()
                .generateName("sentrius-agent-")
                .labels(Map.of("agentId", agentId)))
            .spec(new V1PodSpec()
                .containers(List.of(new V1Container()
                    .name("agent")
                    .image(image)
                    .imagePullPolicy("IfNotPresent")

                    .args(List.of("--spring.config.location=file:/config/agent.properties",
                        "--agent.namePrefix=" + agentId, "--agent.ai.config=/config/chat-helper.yaml", "--agent.listen.websocket=true",
                        "--agent.callback.url=" + constructedCallbackUrl
                        ))
                    .resources(new V1ResourceRequirements()
                        .limits(Map.of(
                            "cpu", Quantity.fromString("1000m"),
                            "memory", Quantity.fromString("1Gi")
                        )))
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

        return createdPod;
    }
}
