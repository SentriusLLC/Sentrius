package io.sentrius.agent.launcher.service;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import io.kubernetes.client.custom.IntOrString;
import io.kubernetes.client.custom.Quantity;
import io.kubernetes.client.openapi.ApiClient;
import io.kubernetes.client.openapi.ApiException;
import io.kubernetes.client.openapi.apis.CoreV1Api;
import io.kubernetes.client.openapi.models.V1ConfigMapVolumeSource;
import io.kubernetes.client.openapi.models.V1Container;
import io.kubernetes.client.openapi.models.V1ObjectMeta;
import io.kubernetes.client.openapi.models.V1Pod;
import io.kubernetes.client.openapi.models.V1PodSpec;
import io.kubernetes.client.openapi.models.V1PodStatus;
import io.kubernetes.client.openapi.models.V1ResourceRequirements;
import io.kubernetes.client.openapi.models.V1Service;
import io.kubernetes.client.openapi.models.V1ServicePort;
import io.kubernetes.client.openapi.models.V1ServiceSpec;
import io.kubernetes.client.openapi.models.V1Volume;
import io.kubernetes.client.openapi.models.V1VolumeMount;
import io.kubernetes.client.util.Config;
import io.sentrius.sso.core.dto.AgentRegistrationDTO;
import io.sentrius.sso.core.model.AgentStatus;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class PodMonitor {

    private final CoreV1Api coreV1Api;

    @Value("${sentrius.agent.namespace}")
    private String agentNamespace;

    public PodMonitor() throws IOException {
        ApiClient client = Config.defaultClient(); // in-cluster or kubeconfig
        this.coreV1Api = new CoreV1Api(client);
    }

    @Scheduled(fixedDelay = 60000) // Runs every 60 seconds
    @Async
    public void removePodsInErrorState() throws ApiException {

        log.info("Identifying pods to be removed");
        var pods = coreV1Api.listNamespacedPod(
            agentNamespace
        ).execute().getItems();

        List<V1Pod> podsToRemove = new ArrayList<>();
        for (V1Pod pod : pods) {

           var podName = pod.getMetadata().getName();

            if (podName == null || !podName.startsWith("sentrius-agent-")) {
                log.info("Skipping pod {}", podName);
                continue;
            }
            V1PodStatus status = pod.getStatus();
            if (status == null) {
                log.warn("Pod {} has no status information", podName);
                continue;
            }

            String phase = status.getPhase(); // e.g., "Running", "Pending", "Failed", "Succeeded"
            if ("Error".equalsIgnoreCase(phase) || "Failed".equalsIgnoreCase(phase)) {
                log.info("Pod {} is in phase {}, adding to removal list", podName, phase);
                podsToRemove.add(pod);
            } else {
                log.info("Pod {} is in phase {}, skipping", podName, phase);
            }
        }

        for (V1Pod pod : podsToRemove) {
            var podName = pod.getMetadata().getName();
            try {
                assert podName != null;
                coreV1Api.deleteNamespacedPod(
                    podName,
                    agentNamespace
                ).execute();
                log.info("Deleted pod {}", podName);
            } catch (ApiException e) {
                log.error("Failed to delete pod {}: {}", podName, e.getResponseBody());
            }
        }


    }
}
