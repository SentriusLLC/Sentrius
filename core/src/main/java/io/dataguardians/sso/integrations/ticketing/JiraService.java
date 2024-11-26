package io.dataguardians.sso.integrations.ticketing;

import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.http.HttpHeaders

@Service
public class JiraService {
    private final RestTemplate restTemplate;

    @Value("${jira.base-url}")
    private String jiraBaseUrl;

    @Value("${jira.api-token}")
    private String apiToken;

    @Value("${jira.username}")
    private String username;

    public JiraService(RestTemplateBuilder builder) {
        this.restTemplate = builder.build();
    }

    public boolean isTicketActive(String ticketKey) {
        String url = String.format("%s/rest/api/3/issue/%s", jiraBaseUrl, ticketKey);

        HttpHeaders headers = new HttpHeaders();
        headers.setBasicAuth(username, apiToken);
        headers.setContentType(MediaType.APPLICATION_JSON);

        HttpEntity<Void> requestEntity = new HttpEntity<>(headers);
        ResponseEntity<Map> response = restTemplate.exchange(url, HttpMethod.GET, requestEntity, Map.class);

        Map<String, Object> fields = (Map<String, Object>) response.getBody().get("fields");
        String status = (String) ((Map<String, Object>) fields.get("status")).get("name");

        return "Active".equalsIgnoreCase(status);
    }
}
