package io.sentrius.agent.launcher.service;

import io.kubernetes.client.openapi.ApiClient;
import io.kubernetes.client.openapi.ApiException;
import io.kubernetes.client.openapi.apis.CoreV1Api;
import io.kubernetes.client.openapi.models.V1Node;
import io.kubernetes.client.openapi.models.V1NodeList;
import io.kubernetes.client.openapi.models.V1ContainerImage;
import io.kubernetes.client.util.Config;
import io.sentrius.sso.core.dto.podman.ImageIntent;
import io.sentrius.sso.core.dto.podman.SelectionConfig;
import io.sentrius.sso.core.dto.AgentRegistrationDTO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import java.util.regex.Matcher;

/**
 * Service responsible for resolving the correct container image for agent pods.
 * Supports multiple strategies:
 * 1. Explicit tag from ImageIntent
 * 2. Generation-based selection from registry
 * 3. Fallback to configured registry + version
 */
@Service
@Slf4j
public class AgentImageResolver {

    private final CoreV1Api coreV1Api;
    
    @Value("${sentrius.agent.registry}")
    private String agentRegistry;

    @Value("${sentrius.agent.registry.version}")
    private String agentVersion;
    
    @Value("${sentrius.agent.namespace}")
    private String agentNamespace;
    
    public AgentImageResolver() throws IOException {
        ApiClient client = Config.defaultClient();
        this.coreV1Api = new CoreV1Api(client);
    }

    /**
     * Resolve the complete image reference for an agent
     * 
     * @param agent The agent registration details
     * @return Complete image reference (e.g., "registry/image:tag")
     */
    public String resolveImage(AgentRegistrationDTO agent) {
        String agentName = agent.getAgentName().toLowerCase();
        
        // 1. Parse image intent from template configuration
        ImageIntent intent = ImageIntent.from(agent);
        
        // 2. If explicit tag is specified, use it with configured or specified repo
        if (intent.getTag() != null && !intent.getTag().isEmpty()) {
            String repo = determineRepository(intent, agent);
            String imageRef = String.format("%s:%s", repo, intent.getTag());
            log.info("Using explicit tag for agent {}: {}", agentName, imageRef);
            return imageRef;
        }
        
        // 3. If selection strategy is specified, try to resolve
        if (intent.getSelection() != null) {
            String resolved = resolveWithStrategy(intent, agent);
            if (resolved != null) {
                log.info("Resolved image for agent {} using strategy: {}", agentName, resolved);
                return resolved;
            }
        }
        
        // 4. Fallback to configured registry + version
        String fallbackImage = buildFallbackImage(agent);
        log.info("Using fallback image for agent {}: {}", agentName, fallbackImage);
        return fallbackImage;
    }
    
    /**
     * Determine the repository to use based on intent and configuration
     */
    private String determineRepository(ImageIntent intent, AgentRegistrationDTO agent) {
        // Use explicitly specified repo if available
        if (intent.getRepo() != null && !intent.getRepo().isEmpty()) {
            return intent.getRepo();
        }
        
        // Build from configured registry
        return buildRepositoryPath(agent);
    }
    
    /**
     * Build repository path from configured registry
     */
    private String buildRepositoryPath(AgentRegistrationDTO agent) {
        String registry = normalizeRegistry(agentRegistry);
        
        // For local registry, use simple naming
        if (registry.isEmpty()) {
            return "sentrius-launchable-agent";
        }
        
        // For remote registry, include full path
        return registry + "sentrius-launchable-agent";
    }
    
    /**
     * Normalize registry URL (add trailing slash if needed, handle "local")
     */
    private String normalizeRegistry(String registry) {
        if (registry == null || "local".equalsIgnoreCase(registry)) {
            return "";
        }
        
        if (!registry.endsWith("/")) {
            return registry + "/";
        }
        
        return registry;
    }
    
    /**
     * Attempt to resolve image using the specified selection strategy
     */
    private String resolveWithStrategy(ImageIntent intent, AgentRegistrationDTO agent) {
        SelectionConfig selection = intent.getSelection();
        String strategy = selection.getStrategy();
        
        if (strategy == null) {
            return null;
        }
        
        switch (strategy.toLowerCase()) {
            case "generation":
                return resolveByGeneration(intent, agent, selection);
            case "latest":
                return resolveLatest(intent, agent);
            case "tag":
                if (selection.getSpecificTag() != null) {
                    String repo = determineRepository(intent, agent);
                    return String.format("%s:%s", repo, selection.getSpecificTag());
                }
                return null;
            default:
                log.warn("Unknown selection strategy '{}' for agent {}", strategy, agent.getAgentName());
                return null;
        }
    }
    
    /**
     * Resolve image by generation number
     * Queries available images from Kubernetes nodes and selects based on generation
     */
    private String resolveByGeneration(ImageIntent intent, AgentRegistrationDTO agent, SelectionConfig selection) {
        String repo = determineRepository(intent, agent);
        
        if (selection.getMaxGeneration() != null) {
            // Try to find the best matching generation from available images
            Integer maxGen = selection.getMaxGeneration();
            Integer minGen = selection.getMinGeneration() != null ? selection.getMinGeneration() : 1;
            
            // Query Kubernetes for available images
            List<String> availableImages = queryAvailableImages(repo);
            
            // Find the highest generation within the range
            String bestMatch = findBestGenerationMatch(availableImages, repo, minGen, maxGen);
            
            if (bestMatch != null) {
                log.info("Found generation-based image for agent {}: {}", agent.getAgentName(), bestMatch);
                return bestMatch;
            }
            
            // Fallback: construct the tag for maxGeneration
            String tag = "gen-" + maxGen;
            log.info("No existing generation images found, using constructed tag for agent {}: {}", agent.getAgentName(), tag);
            return String.format("%s:%s", repo, tag);
        }
        
        // Fallback to default version
        log.debug("No maxGeneration specified, falling back to default version");
        return null;
    }
    
    /**
     * Query Kubernetes nodes for available container images
     */
    private List<String> queryAvailableImages(String repo) {
        List<String> images = new ArrayList<>();
        
        try {
            V1NodeList nodeList = coreV1Api.listNode().execute();
            
            for (V1Node node : nodeList.getItems()) {
                if (node.getStatus() != null && node.getStatus().getImages() != null) {
                    for (V1ContainerImage image : node.getStatus().getImages()) {
                        if (image.getNames() != null) {
                            for (String imageName : image.getNames()) {
                                // Check if this image matches our repo
                                if (imageName.startsWith(repo + ":") || imageName.startsWith(repo + "@")) {
                                    images.add(imageName);
                                }
                            }
                        }
                    }
                }
            }
            
            log.debug("Found {} images in cluster for repo: {}", images.size(), repo);
        } catch (ApiException e) {
            log.warn("Failed to query Kubernetes for available images: {} - {}", e.getCode(), e.getMessage());
        }
        
        return images;
    }
    
    /**
     * Find the best generation match from available images
     */
    private String findBestGenerationMatch(List<String> availableImages, String repo, int minGen, int maxGen) {
        Pattern genPattern = Pattern.compile(Pattern.quote(repo) + ":gen-(\\d+)");
        
        int bestGeneration = -1;
        String bestImage = null;
        
        for (String image : availableImages) {
            Matcher matcher = genPattern.matcher(image);
            if (matcher.find()) {
                try {
                    int gen = Integer.parseInt(matcher.group(1));
                    if (gen >= minGen && gen <= maxGen && gen > bestGeneration) {
                        bestGeneration = gen;
                        bestImage = image;
                    }
                } catch (NumberFormatException e) {
                    log.debug("Invalid generation number in image: {}", image);
                }
            }
        }
        
        return bestImage;
    }
    
    /**
     * Resolve to latest available image
     * Checks available images and uses "latest" tag if found, otherwise falls back
     */
    private String resolveLatest(ImageIntent intent, AgentRegistrationDTO agent) {
        String repo = determineRepository(intent, agent);
        
        // Query available images
        List<String> availableImages = queryAvailableImages(repo);
        
        // Check if "latest" tag exists
        String latestImage = repo + ":latest";
        for (String image : availableImages) {
            if (image.equals(latestImage) || image.startsWith(latestImage + " ")) {
                log.info("Found latest tag in cluster for agent {}: {}", agent.getAgentName(), latestImage);
                return latestImage;
            }
        }
        
        // If latest not found but other images exist, log warning but still use latest
        if (!availableImages.isEmpty()) {
            log.info("Latest tag not found in cluster for agent {}, but using it anyway: {}", agent.getAgentName(), latestImage);
        } else {
            log.debug("No images found in cluster for repo {}, using latest tag: {}", repo, latestImage);
        }
        
        return latestImage;
    }
    
    /**
     * Build fallback image reference using configured registry and version
     */
    private String buildFallbackImage(AgentRegistrationDTO agent) {
        String registry = normalizeRegistry(agentRegistry);
        return String.format("%ssentrius-launchable-agent:%s", registry, agentVersion);
    }
}
