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

    /**
     * Get a specific release by tag name
     */
    public JsonNode getReleaseByTag(String githubToken, String owner, String repo, String tag) {
        try {
            String url = String.format("%s/repos/%s/%s/releases/tags/%s", 
                GITHUB_API_BASE, owner, repo, tag);
            
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
            log.error("Error getting release by tag: {}/{}/{}", owner, repo, tag, e);
            throw new RuntimeException("Failed to get release by tag", e);
        }
    }

    /**
     * Get a specific repository
     */
    public JsonNode getRepository(String githubToken, String owner, String repo) {
        try {
            String url = String.format("%s/repos/%s/%s", GITHUB_API_BASE, owner, repo);
            
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
            log.error("Error getting repository: {}/{}", owner, repo, e);
            throw new RuntimeException("Failed to get repository", e);
        }
    }

    /**
     * List repositories for the authenticated user
     */
    public JsonNode listUserRepositories(String githubToken, String visibility, String sort, 
                                          String direction, Integer page, Integer perPage) {
        try {
            String url = String.format("%s/user/repos", GITHUB_API_BASE);
            
            UriComponentsBuilder builder = UriComponentsBuilder.fromHttpUrl(url);
            if (visibility != null) builder.queryParam("visibility", visibility);
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
            log.error("Error listing user repositories", e);
            throw new RuntimeException("Failed to list user repositories", e);
        }
    }

    /**
     * List repositories for a specific user
     */
    public JsonNode listUserRepositoriesByUsername(String githubToken, String username, String type, 
                                                     String sort, String direction, Integer page, Integer perPage) {
        try {
            String url = String.format("%s/users/%s/repos", GITHUB_API_BASE, username);
            
            UriComponentsBuilder builder = UriComponentsBuilder.fromHttpUrl(url);
            if (type != null) builder.queryParam("type", type);
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
            log.error("Error listing repositories for user: {}", username, e);
            throw new RuntimeException("Failed to list user repositories", e);
        }
    }

    /**
     * List organization repositories
     */
    public JsonNode listOrganizationRepositories(String githubToken, String org, String type, 
                                                   String sort, String direction, Integer page, Integer perPage) {
        try {
            String url = String.format("%s/orgs/%s/repos", GITHUB_API_BASE, org);
            
            UriComponentsBuilder builder = UriComponentsBuilder.fromHttpUrl(url);
            if (type != null) builder.queryParam("type", type);
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
            log.error("Error listing organization repositories: {}", org, e);
            throw new RuntimeException("Failed to list organization repositories", e);
        }
    }

    /**
     * Create a repository for the authenticated user
     */
    public JsonNode createRepository(String githubToken, String name, String description, 
                                      Boolean privateRepo, Boolean hasIssues, Boolean hasProjects, Boolean hasWiki) {
        try {
            String url = String.format("%s/user/repos", GITHUB_API_BASE);
            
            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("name", name);
            if (description != null) requestBody.put("description", description);
            if (privateRepo != null) requestBody.put("private", privateRepo);
            if (hasIssues != null) requestBody.put("has_issues", hasIssues);
            if (hasProjects != null) requestBody.put("has_projects", hasProjects);
            if (hasWiki != null) requestBody.put("has_wiki", hasWiki);
            
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
            log.error("Error creating repository: {}", name, e);
            throw new RuntimeException("Failed to create repository", e);
        }
    }

    /**
     * Update a repository
     */
    public JsonNode updateRepository(String githubToken, String owner, String repo, Map<String, Object> updates) {
        try {
            String url = String.format("%s/repos/%s/%s", GITHUB_API_BASE, owner, repo);
            
            HttpHeaders headers = createHeaders(githubToken);
            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(updates, headers);
            
            ResponseEntity<String> response = restTemplate.exchange(
                url,
                HttpMethod.PATCH,
                entity,
                String.class
            );
            
            return objectMapper.readTree(response.getBody());
        } catch (Exception e) {
            log.error("Error updating repository: {}/{}", owner, repo, e);
            throw new RuntimeException("Failed to update repository", e);
        }
    }

    /**
     * Delete a repository
     */
    public void deleteRepository(String githubToken, String owner, String repo) {
        try {
            String url = String.format("%s/repos/%s/%s", GITHUB_API_BASE, owner, repo);
            
            HttpHeaders headers = createHeaders(githubToken);
            HttpEntity<String> entity = new HttpEntity<>(headers);
            
            restTemplate.exchange(
                url,
                HttpMethod.DELETE,
                entity,
                String.class
            );
            
            log.info("Successfully deleted repository: {}/{}", owner, repo);
        } catch (Exception e) {
            log.error("Error deleting repository: {}/{}", owner, repo, e);
            throw new RuntimeException("Failed to delete repository", e);
        }
    }

    /**
     * Create an issue
     */
    public JsonNode createIssue(String githubToken, String owner, String repo, String title, 
                                 String body, String[] labels, String[] assignees) {
        try {
            String url = String.format("%s/repos/%s/%s/issues", GITHUB_API_BASE, owner, repo);
            
            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("title", title);
            if (body != null) requestBody.put("body", body);
            if (labels != null) requestBody.put("labels", labels);
            if (assignees != null) requestBody.put("assignees", assignees);
            
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
            log.error("Error creating issue: {}/{}", owner, repo, e);
            throw new RuntimeException("Failed to create issue", e);
        }
    }

    /**
     * Update an issue
     */
    public JsonNode updateIssue(String githubToken, String owner, String repo, int issueNumber, 
                                 Map<String, Object> updates) {
        try {
            String url = String.format("%s/repos/%s/%s/issues/%d", 
                GITHUB_API_BASE, owner, repo, issueNumber);
            
            HttpHeaders headers = createHeaders(githubToken);
            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(updates, headers);
            
            ResponseEntity<String> response = restTemplate.exchange(
                url,
                HttpMethod.PATCH,
                entity,
                String.class
            );
            
            return objectMapper.readTree(response.getBody());
        } catch (Exception e) {
            log.error("Error updating issue: {}/{}/#{}", owner, repo, issueNumber, e);
            throw new RuntimeException("Failed to update issue", e);
        }
    }

    /**
     * List issue comments
     */
    public JsonNode listIssueComments(String githubToken, String owner, String repo, int issueNumber, 
                                       Integer page, Integer perPage) {
        try {
            String url = String.format("%s/repos/%s/%s/issues/%d/comments", 
                GITHUB_API_BASE, owner, repo, issueNumber);
            
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
            log.error("Error listing issue comments: {}/{}/#{}", owner, repo, issueNumber, e);
            throw new RuntimeException("Failed to list issue comments", e);
        }
    }

    /**
     * Create an issue comment
     */
    public JsonNode createIssueComment(String githubToken, String owner, String repo, 
                                        int issueNumber, String body) {
        try {
            String url = String.format("%s/repos/%s/%s/issues/%d/comments", 
                GITHUB_API_BASE, owner, repo, issueNumber);
            
            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("body", body);
            
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
            log.error("Error creating issue comment: {}/{}/#{}", owner, repo, issueNumber, e);
            throw new RuntimeException("Failed to create issue comment", e);
        }
    }

    /**
     * List available issue labels for a repository
     */
    public JsonNode listIssueLabels(String githubToken, String owner, String repo, 
                                     Integer page, Integer perPage) {
        try {
            String url = String.format("%s/repos/%s/%s/labels", GITHUB_API_BASE, owner, repo);
            
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
            log.error("Error listing issue labels: {}/{}", owner, repo, e);
            throw new RuntimeException("Failed to list issue labels", e);
        }
    }

    /**
     * Add labels to an issue
     */
    public JsonNode addIssueLabels(String githubToken, String owner, String repo, 
                                    int issueNumber, String[] labels) {
        try {
            String url = String.format("%s/repos/%s/%s/issues/%d/labels", 
                GITHUB_API_BASE, owner, repo, issueNumber);
            
            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("labels", labels);
            
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
            log.error("Error adding labels to issue: {}/{}/#{}", owner, repo, issueNumber, e);
            throw new RuntimeException("Failed to add labels to issue", e);
        }
    }

    /**
     * Update a pull request
     */
    public JsonNode updatePullRequest(String githubToken, String owner, String repo, 
                                       int pullNumber, Map<String, Object> updates) {
        try {
            String url = String.format("%s/repos/%s/%s/pulls/%d", 
                GITHUB_API_BASE, owner, repo, pullNumber);
            
            HttpHeaders headers = createHeaders(githubToken);
            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(updates, headers);
            
            ResponseEntity<String> response = restTemplate.exchange(
                url,
                HttpMethod.PATCH,
                entity,
                String.class
            );
            
            return objectMapper.readTree(response.getBody());
        } catch (Exception e) {
            log.error("Error updating pull request: {}/{}/#{}", owner, repo, pullNumber, e);
            throw new RuntimeException("Failed to update pull request", e);
        }
    }

    /**
     * Merge a pull request
     */
    public JsonNode mergePullRequest(String githubToken, String owner, String repo, int pullNumber, 
                                      String commitTitle, String commitMessage, String mergeMethod) {
        try {
            String url = String.format("%s/repos/%s/%s/pulls/%d/merge", 
                GITHUB_API_BASE, owner, repo, pullNumber);
            
            Map<String, Object> requestBody = new HashMap<>();
            if (commitTitle != null) requestBody.put("commit_title", commitTitle);
            if (commitMessage != null) requestBody.put("commit_message", commitMessage);
            if (mergeMethod != null) requestBody.put("merge_method", mergeMethod);
            
            HttpHeaders headers = createHeaders(githubToken);
            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);
            
            ResponseEntity<String> response = restTemplate.exchange(
                url,
                HttpMethod.PUT,
                entity,
                String.class
            );
            
            return objectMapper.readTree(response.getBody());
        } catch (Exception e) {
            log.error("Error merging pull request: {}/{}/#{}", owner, repo, pullNumber, e);
            throw new RuntimeException("Failed to merge pull request", e);
        }
    }

    /**
     * List pull request files
     */
    public JsonNode listPullRequestFiles(String githubToken, String owner, String repo, 
                                          int pullNumber, Integer page, Integer perPage) {
        try {
            String url = String.format("%s/repos/%s/%s/pulls/%d/files", 
                GITHUB_API_BASE, owner, repo, pullNumber);
            
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
            log.error("Error listing pull request files: {}/{}/#{}", owner, repo, pullNumber, e);
            throw new RuntimeException("Failed to list pull request files", e);
        }
    }

    /**
     * List pull request reviews
     */
    public JsonNode listPullRequestReviews(String githubToken, String owner, String repo, 
                                            int pullNumber, Integer page, Integer perPage) {
        try {
            String url = String.format("%s/repos/%s/%s/pulls/%d/reviews", 
                GITHUB_API_BASE, owner, repo, pullNumber);
            
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
            log.error("Error listing pull request reviews: {}/{}/#{}", owner, repo, pullNumber, e);
            throw new RuntimeException("Failed to list pull request reviews", e);
        }
    }

    /**
     * Create a pull request review
     */
    public JsonNode createPullRequestReview(String githubToken, String owner, String repo, 
                                             int pullNumber, String body, String event, JsonNode comments) {
        try {
            String url = String.format("%s/repos/%s/%s/pulls/%d/reviews", 
                GITHUB_API_BASE, owner, repo, pullNumber);
            
            Map<String, Object> requestBody = new HashMap<>();
            if (body != null) requestBody.put("body", body);
            if (event != null) requestBody.put("event", event);
            if (comments != null) requestBody.put("comments", comments);
            
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
            log.error("Error creating pull request review: {}/{}/#{}", owner, repo, pullNumber, e);
            throw new RuntimeException("Failed to create pull request review", e);
        }
    }

    /**
     * List pull request review comments
     */
    public JsonNode listPullRequestReviewComments(String githubToken, String owner, String repo, 
                                                    int pullNumber, Integer page, Integer perPage) {
        try {
            String url = String.format("%s/repos/%s/%s/pulls/%d/comments", 
                GITHUB_API_BASE, owner, repo, pullNumber);
            
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
            log.error("Error listing pull request review comments: {}/{}/#{}", owner, repo, pullNumber, e);
            throw new RuntimeException("Failed to list pull request review comments", e);
        }
    }

    /**
     * Get a specific user
     */
    public JsonNode getUser(String githubToken, String username) {
        try {
            String url = String.format("%s/users/%s", GITHUB_API_BASE, username);
            
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
            log.error("Error getting user: {}", username, e);
            throw new RuntimeException("Failed to get user", e);
        }
    }

    /**
     * List user followers
     */
    public JsonNode listUserFollowers(String githubToken, String username, Integer page, Integer perPage) {
        try {
            String url = String.format("%s/users/%s/followers", GITHUB_API_BASE, username);
            
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
            log.error("Error listing followers for user: {}", username, e);
            throw new RuntimeException("Failed to list user followers", e);
        }
    }

    /**
     * List users followed by a user
     */
    public JsonNode listUserFollowing(String githubToken, String username, Integer page, Integer perPage) {
        try {
            String url = String.format("%s/users/%s/following", GITHUB_API_BASE, username);
            
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
            log.error("Error listing following for user: {}", username, e);
            throw new RuntimeException("Failed to list user following", e);
        }
    }

    /**
     * Get an organization
     */
    public JsonNode getOrganization(String githubToken, String org) {
        try {
            String url = String.format("%s/orgs/%s", GITHUB_API_BASE, org);
            
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
            log.error("Error getting organization: {}", org, e);
            throw new RuntimeException("Failed to get organization", e);
        }
    }

    /**
     * List organization members
     */
    public JsonNode listOrganizationMembers(String githubToken, String org, String role, 
                                             Integer page, Integer perPage) {
        try {
            String url = String.format("%s/orgs/%s/members", GITHUB_API_BASE, org);
            
            UriComponentsBuilder builder = UriComponentsBuilder.fromHttpUrl(url);
            if (role != null) builder.queryParam("role", role);
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
            log.error("Error listing organization members: {}", org, e);
            throw new RuntimeException("Failed to list organization members", e);
        }
    }

    /**
     * List organization teams
     */
    public JsonNode listOrganizationTeams(String githubToken, String org, Integer page, Integer perPage) {
        try {
            String url = String.format("%s/orgs/%s/teams", GITHUB_API_BASE, org);
            
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
            log.error("Error listing organization teams: {}", org, e);
            throw new RuntimeException("Failed to list organization teams", e);
        }
    }

    /**
     * List workflow runs for a repository
     */
    public JsonNode listWorkflowRuns(String githubToken, String owner, String repo, String actor, 
                                      String branch, String event, String status, Integer page, Integer perPage) {
        try {
            String url = String.format("%s/repos/%s/%s/actions/runs", GITHUB_API_BASE, owner, repo);
            
            UriComponentsBuilder builder = UriComponentsBuilder.fromHttpUrl(url);
            if (actor != null) builder.queryParam("actor", actor);
            if (branch != null) builder.queryParam("branch", branch);
            if (event != null) builder.queryParam("event", event);
            if (status != null) builder.queryParam("status", status);
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
            log.error("Error listing workflow runs: {}/{}", owner, repo, e);
            throw new RuntimeException("Failed to list workflow runs", e);
        }
    }

    /**
     * Get a specific workflow run
     */
    public JsonNode getWorkflowRun(String githubToken, String owner, String repo, long runId) {
        try {
            String url = String.format("%s/repos/%s/%s/actions/runs/%d", 
                GITHUB_API_BASE, owner, repo, runId);
            
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
            log.error("Error getting workflow run: {}/{}/{}", owner, repo, runId, e);
            throw new RuntimeException("Failed to get workflow run", e);
        }
    }

    /**
     * List workflow run jobs
     */
    public JsonNode listWorkflowRunJobs(String githubToken, String owner, String repo, long runId, 
                                         String filter, Integer page, Integer perPage) {
        try {
            String url = String.format("%s/repos/%s/%s/actions/runs/%d/jobs", 
                GITHUB_API_BASE, owner, repo, runId);
            
            UriComponentsBuilder builder = UriComponentsBuilder.fromHttpUrl(url);
            if (filter != null) builder.queryParam("filter", filter);
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
            log.error("Error listing workflow run jobs: {}/{}/{}", owner, repo, runId, e);
            throw new RuntimeException("Failed to list workflow run jobs", e);
        }
    }

    /**
     * Get workflow run job logs URL
     */
    public String getWorkflowRunLogsUrl(String githubToken, String owner, String repo, long runId) {
        try {
            String url = String.format("%s/repos/%s/%s/actions/runs/%d/logs", 
                GITHUB_API_BASE, owner, repo, runId);
            
            HttpHeaders headers = createHeaders(githubToken);
            HttpEntity<String> entity = new HttpEntity<>(headers);
            
            ResponseEntity<String> response = restTemplate.exchange(
                url,
                HttpMethod.GET,
                entity,
                String.class
            );
            
            return response.getHeaders().getLocation() != null ? 
                response.getHeaders().getLocation().toString() : url;
        } catch (Exception e) {
            log.error("Error getting workflow run logs URL: {}/{}/{}", owner, repo, runId, e);
            throw new RuntimeException("Failed to get workflow run logs URL", e);
        }
    }

    /**
     * List workflow run artifacts
     */
    public JsonNode listWorkflowRunArtifacts(String githubToken, String owner, String repo, long runId, 
                                              Integer page, Integer perPage) {
        try {
            String url = String.format("%s/repos/%s/%s/actions/runs/%d/artifacts", 
                GITHUB_API_BASE, owner, repo, runId);
            
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
            log.error("Error listing workflow run artifacts: {}/{}/{}", owner, repo, runId, e);
            throw new RuntimeException("Failed to list workflow run artifacts", e);
        }
    }

    /**
     * Trigger a workflow dispatch event
     */
    public void triggerWorkflowDispatch(String githubToken, String owner, String repo, String workflowId, 
                                         String ref, Map<String, Object> inputs) {
        try {
            String url = String.format("%s/repos/%s/%s/actions/workflows/%s/dispatches", 
                GITHUB_API_BASE, owner, repo, workflowId);
            
            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("ref", ref);
            if (inputs != null) requestBody.put("inputs", inputs);
            
            HttpHeaders headers = createHeaders(githubToken);
            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);
            
            restTemplate.exchange(
                url,
                HttpMethod.POST,
                entity,
                String.class
            );
            
            log.info("Successfully triggered workflow dispatch: {}/{}/{}", owner, repo, workflowId);
        } catch (Exception e) {
            log.error("Error triggering workflow dispatch: {}/{}/{}", owner, repo, workflowId, e);
            throw new RuntimeException("Failed to trigger workflow dispatch", e);
        }
    }

    /**
     * List tags for a repository
     */
    public JsonNode listTags(String githubToken, String owner, String repo, Integer page, Integer perPage) {
        try {
            String url = String.format("%s/repos/%s/%s/tags", GITHUB_API_BASE, owner, repo);
            
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
            log.error("Error listing tags: {}/{}", owner, repo, e);
            throw new RuntimeException("Failed to list tags", e);
        }
    }

    /**
     * Get a Git reference
     */
    public JsonNode getReference(String githubToken, String owner, String repo, String ref) {
        try {
            String url = String.format("%s/repos/%s/%s/git/ref/%s", 
                GITHUB_API_BASE, owner, repo, ref);
            
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
            log.error("Error getting reference: {}/{}/{}", owner, repo, ref, e);
            throw new RuntimeException("Failed to get reference", e);
        }
    }

    /**
     * Create a Git reference
     */
    public JsonNode createReference(String githubToken, String owner, String repo, String ref, String sha) {
        try {
            String url = String.format("%s/repos/%s/%s/git/refs", GITHUB_API_BASE, owner, repo);
            
            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("ref", ref);
            requestBody.put("sha", sha);
            
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
            log.error("Error creating reference: {}/{}/{}", owner, repo, ref, e);
            throw new RuntimeException("Failed to create reference", e);
        }
    }

    /**
     * Update a Git reference
     */
    public JsonNode updateReference(String githubToken, String owner, String repo, String ref, 
                                     String sha, Boolean force) {
        try {
            String url = String.format("%s/repos/%s/%s/git/refs/%s", 
                GITHUB_API_BASE, owner, repo, ref);
            
            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("sha", sha);
            if (force != null) requestBody.put("force", force);
            
            HttpHeaders headers = createHeaders(githubToken);
            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);
            
            ResponseEntity<String> response = restTemplate.exchange(
                url,
                HttpMethod.PATCH,
                entity,
                String.class
            );
            
            return objectMapper.readTree(response.getBody());
        } catch (Exception e) {
            log.error("Error updating reference: {}/{}/{}", owner, repo, ref, e);
            throw new RuntimeException("Failed to update reference", e);
        }
    }

    /**
     * Delete a Git reference
     */
    public void deleteReference(String githubToken, String owner, String repo, String ref) {
        try {
            String url = String.format("%s/repos/%s/%s/git/refs/%s", 
                GITHUB_API_BASE, owner, repo, ref);
            
            HttpHeaders headers = createHeaders(githubToken);
            HttpEntity<String> entity = new HttpEntity<>(headers);
            
            restTemplate.exchange(
                url,
                HttpMethod.DELETE,
                entity,
                String.class
            );
            
            log.info("Successfully deleted reference: {}/{}/{}", owner, repo, ref);
        } catch (Exception e) {
            log.error("Error deleting reference: {}/{}/{}", owner, repo, ref, e);
            throw new RuntimeException("Failed to delete reference", e);
        }
    }

    /**
     * Get repository collaborators
     */
    public JsonNode listCollaborators(String githubToken, String owner, String repo, 
                                       Integer page, Integer perPage) {
        try {
            String url = String.format("%s/repos/%s/%s/collaborators", GITHUB_API_BASE, owner, repo);
            
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
            log.error("Error listing collaborators: {}/{}", owner, repo, e);
            throw new RuntimeException("Failed to list collaborators", e);
        }
    }

    /**
     * Add repository collaborator
     */
    public void addCollaborator(String githubToken, String owner, String repo, String username, 
                                 String permission) {
        try {
            String url = String.format("%s/repos/%s/%s/collaborators/%s", 
                GITHUB_API_BASE, owner, repo, username);
            
            Map<String, Object> requestBody = new HashMap<>();
            if (permission != null) requestBody.put("permission", permission);
            
            HttpHeaders headers = createHeaders(githubToken);
            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);
            
            restTemplate.exchange(
                url,
                HttpMethod.PUT,
                entity,
                String.class
            );
            
            log.info("Successfully added collaborator: {}/{}/{}", owner, repo, username);
        } catch (Exception e) {
            log.error("Error adding collaborator: {}/{}/{}", owner, repo, username, e);
            throw new RuntimeException("Failed to add collaborator", e);
        }
    }

    /**
     * Remove repository collaborator
     */
    public void removeCollaborator(String githubToken, String owner, String repo, String username) {
        try {
            String url = String.format("%s/repos/%s/%s/collaborators/%s", 
                GITHUB_API_BASE, owner, repo, username);
            
            HttpHeaders headers = createHeaders(githubToken);
            HttpEntity<String> entity = new HttpEntity<>(headers);
            
            restTemplate.exchange(
                url,
                HttpMethod.DELETE,
                entity,
                String.class
            );
            
            log.info("Successfully removed collaborator: {}/{}/{}", owner, repo, username);
        } catch (Exception e) {
            log.error("Error removing collaborator: {}/{}/{}", owner, repo, username, e);
            throw new RuntimeException("Failed to remove collaborator", e);
        }
    }

    /**
     * List repository webhooks
     */
    public JsonNode listWebhooks(String githubToken, String owner, String repo, Integer page, Integer perPage) {
        try {
            String url = String.format("%s/repos/%s/%s/hooks", GITHUB_API_BASE, owner, repo);
            
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
            log.error("Error listing webhooks: {}/{}", owner, repo, e);
            throw new RuntimeException("Failed to list webhooks", e);
        }
    }

    /**
     * Create a repository webhook
     */
    public JsonNode createWebhook(String githubToken, String owner, String repo, Map<String, Object> config, 
                                   String[] events, Boolean active) {
        try {
            String url = String.format("%s/repos/%s/%s/hooks", GITHUB_API_BASE, owner, repo);
            
            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("config", config);
            if (events != null) requestBody.put("events", events);
            if (active != null) requestBody.put("active", active);
            
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
            log.error("Error creating webhook: {}/{}", owner, repo, e);
            throw new RuntimeException("Failed to create webhook", e);
        }
    }

    /**
     * Delete a repository webhook
     */
    public void deleteWebhook(String githubToken, String owner, String repo, long hookId) {
        try {
            String url = String.format("%s/repos/%s/%s/hooks/%d", 
                GITHUB_API_BASE, owner, repo, hookId);
            
            HttpHeaders headers = createHeaders(githubToken);
            HttpEntity<String> entity = new HttpEntity<>(headers);
            
            restTemplate.exchange(
                url,
                HttpMethod.DELETE,
                entity,
                String.class
            );
            
            log.info("Successfully deleted webhook: {}/{}/{}", owner, repo, hookId);
        } catch (Exception e) {
            log.error("Error deleting webhook: {}/{}/{}", owner, repo, hookId, e);
            throw new RuntimeException("Failed to delete webhook", e);
        }
    }

    /**
     * Get API rate limit status
     */
    public JsonNode getRateLimit(String githubToken) {
        try {
            String url = String.format("%s/rate_limit", GITHUB_API_BASE);
            
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
            log.error("Error getting rate limit", e);
            throw new RuntimeException("Failed to get rate limit", e);
        }
    }
}
