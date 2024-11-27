package io.dataguardians.sso.integrations.ticketing;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

public class GitHubService {
    private final RestTemplate restTemplate;

    private String apiUrl;


    private String token;

    public GitHubService(RestTemplateBuilder builder) {
        this.restTemplate = builder.build();
    }

    public boolean isIssueOpen(String owner, String repo, int issueNumber) {
        String url = String.format("%s/repos/%s/%s/issues/%d", apiUrl, owner, repo, issueNumber);

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token); // Add GitHub personal access token for authentication
        headers.setContentType(MediaType.APPLICATION_JSON);

        HttpEntity<Void> requestEntity = new HttpEntity<>(headers);
        ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.GET, requestEntity, String.class);

        if (response.getStatusCode() == HttpStatus.OK) {
            String body = response.getBody();
            return body != null && body.contains("\"state\":\"open\"");
        } else {
            throw new RuntimeException("Failed to fetch GitHub issue: " + response.getStatusCode());
        }
    }

}
