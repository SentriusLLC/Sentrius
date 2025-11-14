package io.sentrius.agent.launcher.service;

import io.kubernetes.client.custom.Quantity;
import io.kubernetes.client.openapi.ApiClient;
import io.kubernetes.client.openapi.apis.BatchV1Api;
import io.kubernetes.client.openapi.apis.CoreV1Api;
import io.kubernetes.client.openapi.models.*;
import io.kubernetes.client.util.Config;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.*;

/**
 * Service for building Docker images as part of the self-healing process
 * Spins up Kubernetes Jobs to build and push Docker images using Kaniko
 */
@Slf4j
@Service
public class DockerImageBuilderService {

    private final CoreV1Api coreV1Api;
    private final BatchV1Api batchV1Api;

    @Value("${self-healing.builder.namespace:dev}")
    private String builderNamespace;

    @Value("${self-healing.builder.image:gcr.io/kaniko-project/executor:latest}")
    private String builderImage;

    @Value("${self-healing.docker.registry:}")
    private String dockerRegistry;

    @Value("${self-healing.builder.timeout-seconds:1800}")
    private int builderTimeoutSeconds;

    public DockerImageBuilderService() throws IOException {
        ApiClient client = Config.defaultClient();
        this.coreV1Api = new CoreV1Api(client);
        this.batchV1Api = new BatchV1Api(client);
    }

    /**
     * Build a Docker image for the fixed pod
     * 
     * @param sessionId The healing session ID
     * @param podName The pod name being healed
     * @param dockerfilePath Path to the Dockerfile in the repository
     * @param contextPath Build context path
     * @return Job name if successful, null otherwise
     */
    public String buildDockerImage(Long sessionId, String podName, String dockerfilePath, String contextPath) {
        try {
            String jobName = generateJobName(sessionId, podName);
            String imageName = generateImageName(podName);
            
            log.info("Creating Docker build job {} for session {}", jobName, sessionId);
            
            V1Job job = createBuildJob(jobName, imageName, dockerfilePath, contextPath, sessionId, podName);
            
            V1Job createdJob = batchV1Api.createNamespacedJob(builderNamespace, job).execute();
            
            log.info("Docker build job {} created successfully", jobName);
            return createdJob.getMetadata().getName();
            
        } catch (Exception e) {
            log.error("Error creating Docker build job for session {}", sessionId, e);
            return null;
        }
    }

    /**
     * Check the status of a build job
     * 
     * @param jobName The name of the build job
     * @return "Running", "Succeeded", "Failed", or "Unknown"
     */
    public String checkBuildStatus(String jobName) {
        try {
            V1Job job = batchV1Api.readNamespacedJobStatus(jobName, builderNamespace).execute();
            
            V1JobStatus status = job.getStatus();
            if (status == null) {
                return "Unknown";
            }
            
            if (status.getSucceeded() != null && status.getSucceeded() > 0) {
                return "Succeeded";
            }
            
            if (status.getFailed() != null && status.getFailed() > 0) {
                return "Failed";
            }
            
            if (status.getActive() != null && status.getActive() > 0) {
                return "Running";
            }
            
            return "Pending";
            
        } catch (Exception e) {
            log.error("Error checking build status for job {}", jobName, e);
            return "Unknown";
        }
    }

    /**
     * Get logs from the build job
     * 
     * @param jobName The build job name
     * @return Build logs or error message
     */
    public String getBuildLogs(String jobName) {
        try {
            // Find the pod created by this job
            V1PodList pods = coreV1Api.listNamespacedPod(builderNamespace)
                    .labelSelector("job-name=" + jobName)
                    .execute();
            
            if (pods.getItems().isEmpty()) {
                return "No pods found for job " + jobName;
            }
            
            String podName = pods.getItems().get(0).getMetadata().getName();
            
            String logs = coreV1Api.readNamespacedPodLog(podName, builderNamespace)
                    .container("kaniko-builder")
                    .execute();
            
            return logs;
            
        } catch (Exception e) {
            log.error("Error getting build logs for job {}", jobName, e);
            return "Error retrieving logs: " + e.getMessage();
        }
    }

    /**
     * Delete a build job and its pods
     * 
     * @param jobName The name of the build job
     */
    public void deleteBuildJob(String jobName) {
        try {
            batchV1Api.deleteNamespacedJob(jobName, builderNamespace)
                    .propagationPolicy("Background")
                    .execute();
            
            log.info("Deleted build job {}", jobName);
            
        } catch (Exception e) {
            log.error("Error deleting build job {}", jobName, e);
        }
    }

    /**
     * Create a Kubernetes Job for building a Docker image using Kaniko
     */
    private V1Job createBuildJob(String jobName, String imageName, String dockerfilePath, 
                                  String contextPath, Long sessionId, String podName) {
        
        // Build the Kaniko command arguments
        List<String> args = new ArrayList<>();
        args.add("--dockerfile=" + dockerfilePath);
        args.add("--context=" + contextPath);
        args.add("--destination=" + imageName);
        args.add("--cache=true");
        args.add("--compressed-caching=false");
        
        // Create the container spec
        V1Container container = new V1Container()
                .name("kaniko-builder")
                .image(builderImage)
                .args(args)
                .resources(new V1ResourceRequirements()
                        .requests(Map.of(
                                "cpu", Quantity.fromString("1000m"),
                                "memory", Quantity.fromString("2Gi")
                        ))
                        .limits(Map.of(
                                "cpu", Quantity.fromString("2000m"),
                                "memory", Quantity.fromString("4Gi")
                        ))
                );
        
        // If Docker registry credentials are configured, mount them
        if (dockerRegistry != null && !dockerRegistry.isEmpty()) {
            container.addVolumeMountsItem(new V1VolumeMount()
                    .name("docker-config")
                    .mountPath("/kaniko/.docker"));
        }
        
        // Create the pod template
        V1PodTemplateSpec podTemplate = new V1PodTemplateSpec()
                .metadata(new V1ObjectMeta()
                        .labels(Map.of(
                                "app", "docker-builder",
                                "session-id", String.valueOf(sessionId),
                                "pod-name", podName != null ? podName : "unknown"
                        ))
                )
                .spec(new V1PodSpec()
                        .restartPolicy("Never")
                        .addContainersItem(container)
                );
        
        // Add Docker config volume if registry is configured
        if (dockerRegistry != null && !dockerRegistry.isEmpty()) {
            podTemplate.getSpec().addVolumesItem(new V1Volume()
                    .name("docker-config")
                    .secret(new V1SecretVolumeSource()
                            .secretName("docker-registry-secret")));
        }
        
        // Create the job spec
        V1JobSpec jobSpec = new V1JobSpec()
                .template(podTemplate)
                .backoffLimit(2)
                .activeDeadlineSeconds((long) builderTimeoutSeconds);
        
        // Create the job
        return new V1Job()
                .apiVersion("batch/v1")
                .kind("Job")
                .metadata(new V1ObjectMeta()
                        .name(jobName)
                        .namespace(builderNamespace)
                        .labels(Map.of(
                                "app", "self-healing-builder",
                                "session-id", String.valueOf(sessionId)
                        ))
                )
                .spec(jobSpec);
    }

    /**
     * Generate a unique job name for the build
     */
    private String generateJobName(Long sessionId, String podName) {
        String sanitized = podName != null ? podName.replaceAll("[^a-z0-9-]", "-").toLowerCase() : "unknown";
        return String.format("build-%s-%s", sanitized, 
                UUID.randomUUID().toString().substring(0, 8));
    }

    /**
     * Generate the Docker image name
     */
    private String generateImageName(String podName) {
        String name = podName != null ? podName : "unknown-pod";
        String tag = "healed-" + System.currentTimeMillis();
        
        if (dockerRegistry != null && !dockerRegistry.isEmpty()) {
            return String.format("%s/%s:%s", dockerRegistry, name, tag);
        } else {
            return String.format("%s:%s", name, tag);
        }
    }
}
