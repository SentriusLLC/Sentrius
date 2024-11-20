package io.sentrius.sso.integrations.ticketing;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.node.ArrayNode;
import io.sentrius.sso.core.model.dto.TicketDTO;
import io.sentrius.sso.core.model.security.IntegrationSecurityToken;
import io.sentrius.sso.core.model.users.User;
import io.sentrius.sso.core.utils.JsonUtil;
import io.sentrius.sso.integrations.external.ExternalIntegrationDTO;
import lombok.extern.slf4j.Slf4j;;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;
import org.springframework.http.HttpHeaders;
@Slf4j
public class JiraService {
    private final RestTemplate restTemplate;

    private String jiraBaseUrl;

    private String apiToken;

    private String username;

    public JiraService(RestTemplateBuilder builder) {
        this.restTemplate = builder.build();
    }

    public JiraService(RestTemplate builder, IntegrationSecurityToken integration) throws JsonProcessingException {
        this.restTemplate = builder;

        ExternalIntegrationDTO externalIntegrationDTO = JsonUtil.MAPPER.readValue(integration.getConnectionInfo(),
            ExternalIntegrationDTO.class);
        this.jiraBaseUrl = externalIntegrationDTO.getBaseUrl();
        this.apiToken = externalIntegrationDTO.getApiToken();
        this.username = externalIntegrationDTO.getUsername();
    }

    public boolean isTicketActive(String ticketKey) {
        String url = String.format("%s/rest/api/3/issue/%s", jiraBaseUrl, ticketKey);

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(apiToken);
        //headers.setBasicAuth(username, apiToken);
        headers.setContentType(MediaType.APPLICATION_JSON);

        HttpEntity<Void> requestEntity = new HttpEntity<>(headers);
        ResponseEntity<Map> response = restTemplate.exchange(url, HttpMethod.GET, requestEntity, Map.class);

        Map<String, Object> fields = (Map<String, Object>) response.getBody().get("fields");
        String status = (String) ((Map<String, Object>) fields.get("status")).get("name");

        return "Active".equalsIgnoreCase(status);
    }

    public Optional<String> getUser(User user) throws JsonProcessingException {
        String userSearchUrl = String.format("%s/rest/api/3/user/search?query=%s", jiraBaseUrl, user.getEmailAddress());

        HttpHeaders headers = new HttpHeaders();
        headers.setBasicAuth(username, apiToken); // Use your stored credentials
        headers.setContentType(MediaType.APPLICATION_JSON);

        HttpEntity<Void> requestEntity = new HttpEntity<>(headers);

        ResponseEntity<String> userSearchResponse = restTemplate.exchange(userSearchUrl, HttpMethod.GET, requestEntity, String.class);

        if (userSearchResponse.getStatusCode() == HttpStatus.OK) {
            // Parse the response and find the matching user
            var users = JsonUtil.MAPPER.readValue(userSearchResponse.getBody(), ArrayNode.class);
            if (users.size() > 0) {
                // Assume first user is the best match
                String jiraAccountId = String.valueOf(users.get(0).get("accountId"));
                log.info("Jira User Found: " + jiraAccountId);
                return Optional.of(jiraAccountId);

            } else {
                log.info("No matching user found in Jira for {}.", user.getEmailAddress());
            }
        } else {
            log.info("Failed to search for Jira users. Status: " + userSearchResponse.getStatusCode());
        }
        return Optional.empty();
    }

    public List<TicketDTO> searchForIncidents(String query) throws ExecutionException, InterruptedException {
        List<TicketDTO> ticketsFound = new ArrayList<>();

        // Jira Search API endpoint
        String url = String.format("%s/rest/api/3/search", jiraBaseUrl);

        // JQL query to search by summary, description, or issue key
        boolean isIssueKey = query.matches("[A-Z]+-\\d+"); // Regex to match issue keys like "PROJECT-123"

        String jql = isIssueKey
            ? String.format("(key = \"%s\" OR summary ~ \"%s\" OR description ~ \"%s\") ", query, query, query)
            : String.format("(summary ~ \"%s\" OR description ~ \"%s\") ", query, query);
        log.info("Searching Jira with JQL: {}", jql);

        // Request body for Jira API
        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("jql", jql);
        requestBody.put("expand", Arrays.asList("names", "schema", "operations"));
        requestBody.put("fields", Arrays.asList("summary", "description", "status", "assignee", "key"));
        requestBody.put("fieldsByKeys", false);
        requestBody.put("maxResults", 15);
        requestBody.put("startAt", 0);
        //requestBody.put("fields", Arrays.asList("key", "summary", "description", "status"));

        HttpHeaders headers = new HttpHeaders();
        headers.setBasicAuth(username,apiToken); // Use API token for authentication
        log.info("auth: {}:{}", username, apiToken);

        headers.setContentType(MediaType.APPLICATION_JSON);

        HttpEntity<Map<String, Object>> requestEntity = new HttpEntity<>(requestBody, headers);
        ResponseEntity<Map> response = restTemplate.exchange(url, HttpMethod.POST, requestEntity, Map.class);

        List<Map<String, Object>> issues = (List<Map<String, Object>>) response.getBody().get("issues");
        for (Map<String, Object> issue : issues) {
            String key = (String) issue.get("key");
            Map<String, Object> fields = (Map<String, Object>) issue.get("fields");
            String summary = (String) fields.get("summary");
            String description = (String) fields.get("description");
            String status = (String) ((Map<String, Object>) fields.get("status")).get("name");

            // Add to the result list
            ticketsFound.add(
                TicketDTO.builder().id(key).description(description).summary(summary).status(status).type("jira").build() );
        }

        log.info("Found {} tickets", ticketsFound.size());

        return ticketsFound;
    }

    public boolean assignTicket(String ticketKey, Optional<String> assignee) {
        if (assignee.isEmpty()) {
            log.info("No assignee found for ticket. Skipping assignment.");
            return false;
        }
        String assignUrl = String.format("%s/rest/api/3/issue/%s/assignee", jiraBaseUrl, ticketKey);
        //String assignUrl = "https://your-domain.atlassian.net/rest/api/3/issue/" + issueKey + "/assignee";

        Map<String, String> assignBody = Map.of("accountId", assignee.get());

        HttpHeaders headers = new HttpHeaders();
        headers.setBasicAuth(username,apiToken); // Use API token for authentication
        log.info("auth: {}:{}", username, apiToken);

        headers.setContentType(MediaType.APPLICATION_JSON);

        HttpEntity<Map<String, String>> assignRequestEntity = new HttpEntity<>(assignBody, headers);

        ResponseEntity<Void> assignResponse = restTemplate.exchange(assignUrl, HttpMethod.PUT, assignRequestEntity, Void.class);

        if (assignResponse.getStatusCode() == HttpStatus.NO_CONTENT) {
            log.info("Ticket successfully assigned to user.");
            return true;
        } else {
            log.info("Failed to assign ticket. Status: " + assignResponse.getStatusCode());
            return false;
        }
    }


    public boolean updateTicket(String ticketKey, String message) {
        try {
            String commentUrl = String.format("%s/rest/api/3/issue/%s/comment", jiraBaseUrl, ticketKey);

            // Construct the structured body for the comment
            Map<String, Object> textContent = Map.of("type", "text", "text", message);
            Map<String, Object> paragraphContent = Map.of("type", "paragraph", "content", List.of(textContent));
            Map<String, Object> body = Map.of(
                "version", 1,
                "type", "doc",
                "content", List.of(paragraphContent)
            );

            Map<String, Object> commentBody = Map.of("body", body);

            String jsonPayload = JsonUtil.MAPPER.writeValueAsString(commentBody);

            HttpHeaders headers = new HttpHeaders();
            headers.setBasicAuth(username, apiToken);
            headers.setContentType(MediaType.APPLICATION_JSON);

            log.info("Adding comment body: {}", jsonPayload);

            HttpEntity<String> commentRequestEntity = new HttpEntity<>(jsonPayload, headers);
            ResponseEntity<String> commentResponse = restTemplate.exchange(commentUrl, HttpMethod.POST, commentRequestEntity, String.class);

            if (commentResponse.getStatusCode() == HttpStatus.CREATED) {
                log.info("Comment successfully added to the ticket.");
                return true;
            } else {
                log.info("Failed to add comment. Status: {}", commentResponse.getStatusCode());
                log.info("Response Body: {}", commentResponse.getBody());
                return false;
            }
        } catch (Exception e) {
            log.error("Error while adding comment to ticket: {}", e.getMessage());
            return false;
        }
    }
}
