package io.sentrius.sso.github.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

/**
 * Service for making direct GitHub REST API calls
 * Handles authentication, request formatting, and response processing
 */
@Slf4j
@Service
public class GitHubApiService {

    private static final String GITHUB_API_BASE = "https://api.github.com";
    private static final String API_VERSION = "2022-11-28";
    
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    public GitHubApiService(RestTemplate restTemplate, ObjectMapper objectMapper) {
        this.restTemplate = restTemplate;
        this.objectMapper = objectMapper;
    }

    /**
     * Create HTTP headers with GitHub authentication
     */
    private HttpHeaders createHeaders(String githubToken) {
        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "Bearer " + githubToken);
        headers.set("Accept", "application/vnd.github+json");
        headers.set("X-GitHub-Api-Version", API_VERSION);
        headers.setContentType(MediaType.APPLICATION_JSON);
        return headers;
    }

    /**
     * Get file contents from a repository
     */
    public JsonNode getFileContents(String githubToken, String owner, String repo, String path, String ref) {
        try {
            String url = String.format("%s/repos/%s/%s/contents/%s", 
                GITHUB_API_BASE, owner, repo, path);
            
            UriComponentsBuilder builder = UriComponentsBuilder.fromHttpUrl(url);
            if (ref != null && !ref.isEmpty()) {
                builder.queryParam("ref", ref);
            }
            
            HttpHeaders headers = createHeaders(githubToken);
            HttpEntity<String> entity = new HttpEntity<>(headers);
            
            ResponseEntity<String> response = restTemplate.exchange(
                builder.toUriString(),
                HttpMethod.GET,
                entity,
                String.class
            );
            
            JsonNode result = objectMapper.readTree(response.getBody());
            
            // Decode base64 content if present
            if (result.has("content") && result.get("type").asText().equals("file")) {
                String encodedContent = result.get("content").asText().replace("\n", "");
                String decodedContent = new String(Base64.getDecoder().decode(encodedContent));
                ((com.fasterxml.jackson.databind.node.ObjectNode) result).put("decodedContent", decodedContent);
            }
            
            return result;
        } catch (Exception e) {
            log.error("Error getting file contents: {}/{}/{}", owner, repo, path, e);
            throw new RuntimeException("Failed to get file contents", e);
        }
    }

    /**
     * List commits for a repository
     */
    public JsonNode listCommits(String githubToken, String owner, String repo, String sha, 
                                 String path, String author, Integer page, Integer perPage) {
        try {
            String url = String.format("%s/repos/%s/%s/commits", GITHUB_API_BASE, owner, repo);
            
            UriComponentsBuilder builder = UriComponentsBuilder.fromHttpUrl(url);
            if (sha != null) builder.queryParam("sha", sha);
            if (path != null) builder.queryParam("path", path);
            if (author != null) builder.queryParam("author", author);
            if (page != null) builder.queryParam("page", page);
            if (perPage != null) builder.queryParam("per_page", perPage);
            
            HttpHeaders headers = createHeaders(githubToken);
            HttpEntity<String> entity = new HttpEntity<>(headers);
            
            ResponseEntity<String> response = restTemplate.exchange(
                builder.toUriString(),
                HttpMethod.GET,
                entity,
                String.class
            );
            
            return objectMapper.readTree(response.getBody());
        } catch (Exception e) {
            log.error("Error listing commits: {}/{}", owner, repo, e);
            throw new RuntimeException("Failed to list commits", e);
        }
    }

    /**
     * Get a specific commit
     */
    public JsonNode getCommit(String githubToken, String owner, String repo, String sha) {
        try {
            String url = String.format("%s/repos/%s/%s/commits/%s", 
                GITHUB_API_BASE, owner, repo, sha);
            
            HttpHeaders headers = createHeaders(githubToken);
            HttpEntity<String> entity = new HttpEntity<>(headers);
            
            ResponseEntity<String> response = restTemplate.exchange(
                url,
                HttpMethod.GET,
                entity,
                String.class
            );
            
            return objectMapper.readTree(response.getBody());
        } catch (Exception e) {
            log.error("Error getting commit: {}/{}/{}", owner, repo, sha, e);
            throw new RuntimeException("Failed to get commit", e);
        }
    }

    /**
     * Search repositories
     */
    public JsonNode searchRepositories(String githubToken, String query, String sort, 
                                        String order, Integer page, Integer perPage) {
        try {
            String url = String.format("%s/search/repositories", GITHUB_API_BASE);
            
            UriComponentsBuilder builder = UriComponentsBuilder.fromHttpUrl(url);
            builder.queryParam("q", query);
            if (sort != null) builder.queryParam("sort", sort);
            if (order != null) builder.queryParam("order", order);
            if (page != null) builder.queryParam("page", page);
            if (perPage != null) builder.queryParam("per_page", perPage);
            
            HttpHeaders headers = createHeaders(githubToken);
            HttpEntity<String> entity = new HttpEntity<>(headers);
            
            ResponseEntity<String> response = restTemplate.exchange(
                builder.toUriString(),
                HttpMethod.GET,
                entity,
                String.class
            );
            
            return objectMapper.readTree(response.getBody());
        } catch (Exception e) {
            log.error("Error searching repositories: {}", query, e);
            throw new RuntimeException("Failed to search repositories", e);
        }
    }

    /**
     * Search code
     */
    public JsonNode searchCode(String githubToken, String query, String sort, 
                                String order, Integer page, Integer perPage) {
        try {
            String url = String.format("%s/search/code", GITHUB_API_BASE);
            
            UriComponentsBuilder builder = UriComponentsBuilder.fromHttpUrl(url);
            builder.queryParam("q", query);
            if (sort != null) builder.queryParam("sort", sort);
            if (order != null) builder.queryParam("order", order);
            if (page != null) builder.queryParam("page", page);
            if (perPage != null) builder.queryParam("per_page", perPage);
            
            HttpHeaders headers = createHeaders(githubToken);
            HttpEntity<String> entity = new HttpEntity<>(headers);
            
            ResponseEntity<String> response = restTemplate.exchange(
                builder.toUriString(),
                HttpMethod.GET,
                entity,
                String.class
            );
            
            return objectMapper.readTree(response.getBody());
        } catch (Exception e) {
            log.error("Error searching code: {}", query, e);
            throw new RuntimeException("Failed to search code", e);
        }
    }

    /**
     * Search issues and pull requests
     */
    public JsonNode searchIssues(String githubToken, String query, String sort, 
                                  String order, Integer page, Integer perPage) {
        try {
            String url = String.format("%s/search/issues", GITHUB_API_BASE);
            
            UriComponentsBuilder builder = UriComponentsBuilder.fromHttpUrl(url);
            builder.queryParam("q", query);
            if (sort != null) builder.queryParam("sort", sort);
            if (order != null) builder.queryParam("order", order);
            if (page != null) builder.queryParam("page", page);
            if (perPage != null) builder.queryParam("per_page", perPage);
            
            HttpHeaders headers = createHeaders(githubToken);
            HttpEntity<String> entity = new HttpEntity<>(headers);
            
            ResponseEntity<String> response = restTemplate.exchange(
                builder.toUriString(),
                HttpMethod.GET,
                entity,
                String.class
            );
            
            return objectMapper.readTree(response.getBody());
        } catch (Exception e) {
            log.error("Error searching issues: {}", query, e);
            throw new RuntimeException("Failed to search issues", e);
        }
    }

    /**
     * Search users
     */
    public JsonNode searchUsers(String githubToken, String query, String sort, 
                                 String order, Integer page, Integer perPage) {
        try {
            String url = String.format("%s/search/users", GITHUB_API_BASE);
            
            UriComponentsBuilder builder = UriComponentsBuilder.fromHttpUrl(url);
            builder.queryParam("q", query);
            if (sort != null) builder.queryParam("sort", sort);
            if (order != null) builder.queryParam("order", order);
            if (page != null) builder.queryParam("page", page);
            if (perPage != null) builder.queryParam("per_page", perPage);
            
            HttpHeaders headers = createHeaders(githubToken);
            HttpEntity<String> entity = new HttpEntity<>(headers);
            
            ResponseEntity<String> response = restTemplate.exchange(
                builder.toUriString(),
                HttpMethod.GET,
                entity,
                String.class
            );
            
            return objectMapper.readTree(response.getBody());
        } catch (Exception e) {
            log.error("Error searching users: {}", query, e);
            throw new RuntimeException("Failed to search users", e);
        }
    }

    /**
     * Get authenticated user information
     */
    public JsonNode getAuthenticatedUser(String githubToken) {
        try {
            String url = String.format("%s/user", GITHUB_API_BASE);
            
            HttpHeaders headers = createHeaders(githubToken);
            HttpEntity<String> entity = new HttpEntity<>(headers);
            
            ResponseEntity<String> response = restTemplate.exchange(
                url,
                HttpMethod.GET,
                entity,
                String.class
            );
            
            return objectMapper.readTree(response.getBody());
        } catch (Exception e) {
            log.error("Error getting authenticated user", e);
            throw new RuntimeException("Failed to get authenticated user", e);
        }
    }

    /**
     * List issues for a repository
     */
    public JsonNode listIssues(String githubToken, String owner, String repo, String state, 
                                String labels, String sort, String direction, Integer page, Integer perPage) {
        try {
            String url = String.format("%s/repos/%s/%s/issues", GITHUB_API_BASE, owner, repo);
            
            UriComponentsBuilder builder = UriComponentsBuilder.fromHttpUrl(url);
            if (state != null) builder.queryParam("state", state);
            if (labels != null) builder.queryParam("labels", labels);
            if (sort != null) builder.queryParam("sort", sort);
            if (direction != null) builder.queryParam("direction", direction);
            if (page != null) builder.queryParam("page", page);
            if (perPage != null) builder.queryParam("per_page", perPage);
            
            HttpHeaders headers = createHeaders(githubToken);
            HttpEntity<String> entity = new HttpEntity<>(headers);
            
            ResponseEntity<String> response = restTemplate.exchange(
                builder.toUriString(),
                HttpMethod.GET,
                entity,
                String.class
            );
            
            return objectMapper.readTree(response.getBody());
        } catch (Exception e) {
            log.error("Error listing issues: {}/{}", owner, repo, e);
            throw new RuntimeException("Failed to list issues", e);
        }
    }

    /**
     * Get a specific issue
     */
    public JsonNode getIssue(String githubToken, String owner, String repo, int issueNumber) {
        try {
            String url = String.format("%s/repos/%s/%s/issues/%d", 
                GITHUB_API_BASE, owner, repo, issueNumber);
            
            HttpHeaders headers = createHeaders(githubToken);
            HttpEntity<String> entity = new HttpEntity<>(headers);
            
            ResponseEntity<String> response = restTemplate.exchange(
                url,
                HttpMethod.GET,
                entity,
                String.class
            );
            
            return objectMapper.readTree(response.getBody());
        } catch (Exception e) {
            log.error("Error getting issue: {}/{}/#{}", owner, repo, issueNumber, e);
            throw new RuntimeException("Failed to get issue", e);
        }
    }

    /**
     * List pull requests for a repository
     */
    public JsonNode listPullRequests(String githubToken, String owner, String repo, String state, 
                                      String head, String base, String sort, String direction, 
                                      Integer page, Integer perPage) {
        try {
            String url = String.format("%s/repos/%s/%s/pulls", GITHUB_API_BASE, owner, repo);
            
            UriComponentsBuilder builder = UriComponentsBuilder.fromHttpUrl(url);
            if (state != null) builder.queryParam("state", state);
            if (head != null) builder.queryParam("head", head);
            if (base != null) builder.queryParam("base", base);
            if (sort != null) builder.queryParam("sort", sort);
            if (direction != null) builder.queryParam("direction", direction);
            if (page != null) builder.queryParam("page", page);
            if (perPage != null) builder.queryParam("per_page", perPage);
            
            HttpHeaders headers = createHeaders(githubToken);
            HttpEntity<String> entity = new HttpEntity<>(headers);
            
            ResponseEntity<String> response = restTemplate.exchange(
                builder.toUriString(),
                HttpMethod.GET,
                entity,
                String.class
            );
            
            return objectMapper.readTree(response.getBody());
        } catch (Exception e) {
            log.error("Error listing pull requests: {}/{}", owner, repo, e);
            throw new RuntimeException("Failed to list pull requests", e);
        }
    }

    /**
     * Get a specific pull request
     */
    public JsonNode getPullRequest(String githubToken, String owner, String repo, int pullNumber) {
        try {
            String url = String.format("%s/repos/%s/%s/pulls/%d", 
                GITHUB_API_BASE, owner, repo, pullNumber);
            
            HttpHeaders headers = createHeaders(githubToken);
            HttpEntity<String> entity = new HttpEntity<>(headers);
            
            ResponseEntity<String> response = restTemplate.exchange(
                url,
                HttpMethod.GET,
                entity,
                String.class
            );
            
            return objectMapper.readTree(response.getBody());
        } catch (Exception e) {
            log.error("Error getting pull request: {}/{}/#{}", owner, repo, pullNumber, e);
            throw new RuntimeException("Failed to get pull request", e);
        }
    }

    /**
     * Create a pull request
     */
    public JsonNode createPullRequest(String githubToken, String owner, String repo, 
                                       String title, String head, String base, String body) {
        try {
            String url = String.format("%s/repos/%s/%s/pulls", GITHUB_API_BASE, owner, repo);
            
            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("title", title);
            requestBody.put("head", head);
            requestBody.put("base", base);
            if (body != null) requestBody.put("body", body);
            
            HttpHeaders headers = createHeaders(githubToken);
            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);
            
            ResponseEntity<String> response = restTemplate.exchange(
                url,
                HttpMethod.POST,
                entity,
                String.class
            );
            
            return objectMapper.readTree(response.getBody());
        } catch (Exception e) {
            log.error("Error creating pull request: {}/{}", owner, repo, e);
            throw new RuntimeException("Failed to create pull request", e);
        }
    }

    /**
     * List branches for a repository
     */
    public JsonNode listBranches(String githubToken, String owner, String repo, 
                                  Integer page, Integer perPage) {
        try {
            String url = String.format("%s/repos/%s/%s/branches", GITHUB_API_BASE, owner, repo);
            
            UriComponentsBuilder builder = UriComponentsBuilder.fromHttpUrl(url);
            if (page != null) builder.queryParam("page", page);
            if (perPage != null) builder.queryParam("per_page", perPage);
            
            HttpHeaders headers = createHeaders(githubToken);
            HttpEntity<String> entity = new HttpEntity<>(headers);
            
            ResponseEntity<String> response = restTemplate.exchange(
                builder.toUriString(),
                HttpMethod.GET,
                entity,
                String.class
            );
            
            return objectMapper.readTree(response.getBody());
        } catch (Exception e) {
            log.error("Error listing branches: {}/{}", owner, repo, e);
            throw new RuntimeException("Failed to list branches", e);
        }
    }

    /**
     * List releases for a repository
     */
    public JsonNode listReleases(String githubToken, String owner, String repo, 
                                  Integer page, Integer perPage) {
        try {
            String url = String.format("%s/repos/%s/%s/releases", GITHUB_API_BASE, owner, repo);
            
            UriComponentsBuilder builder = UriComponentsBuilder.fromHttpUrl(url);
            if (page != null) builder.queryParam("page", page);
            if (perPage != null) builder.queryParam("per_page", perPage);
            
            HttpHeaders headers = createHeaders(githubToken);
            HttpEntity<String> entity = new HttpEntity<>(headers);
            
            ResponseEntity<String> response = restTemplate.exchange(
                builder.toUriString(),
                HttpMethod.GET,
                entity,
                String.class
            );
            
            return objectMapper.readTree(response.getBody());
        } catch (Exception e) {
            log.error("Error listing releases: {}/{}", owner, repo, e);
            throw new RuntimeException("Failed to list releases", e);
        }
    }

    /**
     * Get the latest release for a repository
     */
    public JsonNode getLatestRelease(String githubToken, String owner, String repo) {
        try {
            String url = String.format("%s/repos/%s/%s/releases/latest", 
                GITHUB_API_BASE, owner, repo);
            
            HttpHeaders headers = createHeaders(githubToken);
            HttpEntity<String> entity = new HttpEntity<>(headers);
            
            ResponseEntity<String> response = restTemplate.exchange(
                url,
                HttpMethod.GET,
                entity,
                String.class
            );
            
            return objectMapper.readTree(response.getBody());
        } catch (Exception e) {
            log.error("Error getting latest release: {}/{}", owner, repo, e);
            throw new RuntimeException("Failed to get latest release", e);
        }
    }
}
