package io.sentrius.sso.selfhealing;

import io.sentrius.sso.core.integrations.ticketing.GitHubService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.Map;

/**
 * Service for creating GitHub Pull Requests as part of the self-healing process
 */
@Slf4j
@Service
public class SelfHealingGitHubService {

    private final RestTemplate restTemplate;

    @Value("${self-healing.github.enabled:false}")
    private boolean githubEnabled;

    @Value("${self-healing.github.api-url:https://api.github.com}")
    private String githubApiUrl;

    @Value("${self-healing.github.token:}")
    private String githubToken;

    @Value("${self-healing.github.owner:}")
    private String defaultOwner;

    @Value("${self-healing.github.repo:}")
    private String defaultRepo;

    public SelfHealingGitHubService(RestTemplateBuilder builder) {
        this.restTemplate = builder.build();
    }

    /**
     * Check if GitHub integration is enabled and configured
     */
    public boolean isGitHubConfigured() {
        return githubEnabled 
                && githubToken != null && !githubToken.isEmpty()
                && defaultOwner != null && !defaultOwner.isEmpty()
                && defaultRepo != null && !defaultRepo.isEmpty();
    }

    /**
     * Create a Pull Request on GitHub with the healing changes
     * 
     * @param title The PR title
     * @param description The PR description/body
     * @param branch The branch name containing the fix
     * @param baseBranch The base branch to merge into (usually 'main' or 'master')
     * @return The URL of the created PR, or null if creation failed
     */
    public String createPullRequest(String title, String description, String branch, String baseBranch) {
        if (!isGitHubConfigured()) {
            log.warn("GitHub integration not configured, skipping PR creation");
            return null;
        }

        try {
            String url = String.format("%s/repos/%s/%s/pulls", 
                    githubApiUrl, defaultOwner, defaultRepo);

            HttpHeaders headers = new HttpHeaders();
            headers.setBearerAuth(githubToken);
            headers.setContentType(MediaType.APPLICATION_JSON);

            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("title", title);
            requestBody.put("body", description);
            requestBody.put("head", branch);
            requestBody.put("base", baseBranch);

            HttpEntity<Map<String, Object>> request = new HttpEntity<>(requestBody, headers);
            
            ResponseEntity<Map> response = restTemplate.exchange(
                    url, HttpMethod.POST, request, Map.class);

            if (response.getStatusCode() == HttpStatus.CREATED && response.getBody() != null) {
                String prUrl = (String) response.getBody().get("html_url");
                log.info("Successfully created GitHub PR: {}", prUrl);
                return prUrl;
            } else {
                log.error("Failed to create PR, status: {}", response.getStatusCode());
                return null;
            }
        } catch (Exception e) {
            log.error("Error creating GitHub PR", e);
            return null;
        }
    }

    /**
     * Create a branch on GitHub
     * 
     * @param branchName The name of the new branch
     * @param fromSha The SHA to create the branch from
     * @return true if successful, false otherwise
     */
    public boolean createBranch(String branchName, String fromSha) {
        if (!isGitHubConfigured()) {
            log.warn("GitHub integration not configured, skipping branch creation");
            return false;
        }

        try {
            String url = String.format("%s/repos/%s/%s/git/refs", 
                    githubApiUrl, defaultOwner, defaultRepo);

            HttpHeaders headers = new HttpHeaders();
            headers.setBearerAuth(githubToken);
            headers.setContentType(MediaType.APPLICATION_JSON);

            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("ref", "refs/heads/" + branchName);
            requestBody.put("sha", fromSha);

            HttpEntity<Map<String, Object>> request = new HttpEntity<>(requestBody, headers);
            
            ResponseEntity<Map> response = restTemplate.exchange(
                    url, HttpMethod.POST, request, Map.class);

            if (response.getStatusCode() == HttpStatus.CREATED) {
                log.info("Successfully created branch: {}", branchName);
                return true;
            } else {
                log.error("Failed to create branch, status: {}", response.getStatusCode());
                return false;
            }
        } catch (Exception e) {
            log.error("Error creating GitHub branch", e);
            return false;
        }
    }

    /**
     * Get the latest commit SHA from a branch
     */
    public String getLatestCommitSha(String branch) {
        if (!isGitHubConfigured()) {
            return null;
        }

        try {
            String url = String.format("%s/repos/%s/%s/git/refs/heads/%s", 
                    githubApiUrl, defaultOwner, defaultRepo, branch);

            HttpHeaders headers = new HttpHeaders();
            headers.setBearerAuth(githubToken);

            HttpEntity<Void> request = new HttpEntity<>(headers);
            
            ResponseEntity<Map> response = restTemplate.exchange(
                    url, HttpMethod.GET, request, Map.class);

            if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
                Map<String, Object> object = (Map<String, Object>) response.getBody().get("object");
                return (String) object.get("sha");
            }
        } catch (Exception e) {
            log.error("Error getting commit SHA for branch: {}", branch, e);
        }
        
        return null;
    }
}
