package io.sentrius.sso.core.integrations.ticketing;

import com.fasterxml.jackson.core.JsonProcessingException;
import io.sentrius.sso.core.integrations.external.ExternalIntegrationDTO;
import io.sentrius.sso.core.model.security.IntegrationSecurityToken;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.web.client.RestTemplate;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class JiraServiceTest {

    @Mock
    private RestTemplateBuilder restTemplateBuilder;

    @Mock
    private RestTemplate restTemplate;

    @Mock
    private IntegrationSecurityToken integrationToken;

    private JiraService jiraService;

    @BeforeEach
    void setUp() {
        when(restTemplateBuilder.build()).thenReturn(restTemplate);
        jiraService = new JiraService(restTemplateBuilder);
    }

    @Test
    void constructorWithRestTemplateBuilderCreatesService() {
        JiraService service = new JiraService(restTemplateBuilder);
        assertNotNull(service);
    }

    @Test
    void constructorWithRestTemplateAndIntegrationConfiguresService() throws JsonProcessingException {
        String connectionInfo = "{\"baseUrl\":\"https://test.atlassian.net\",\"apiToken\":\"testToken\",\"username\":\"testUser\"}";
        when(integrationToken.getConnectionInfo()).thenReturn(connectionInfo);

        JiraService service = new JiraService(restTemplate, integrationToken);
        assertNotNull(service);
    }

    @Test
    void constructorThrowsExceptionForInvalidJson() {
        String invalidJson = "invalid json";
        when(integrationToken.getConnectionInfo()).thenReturn(invalidJson);

        assertThrows(JsonProcessingException.class, () -> new JiraService(restTemplate, integrationToken));
    }

    @Test
    void jiraServiceCanBeInstantiatedWithValidConfig() throws JsonProcessingException {
        String validConnectionInfo = "{\"baseUrl\":\"https://test.atlassian.net\",\"apiToken\":\"token\",\"username\":\"user\"}";
        when(integrationToken.getConnectionInfo()).thenReturn(validConnectionInfo);
        
        JiraService service = new JiraService(restTemplate, integrationToken);
        
        assertNotNull(service);
        verify(integrationToken).getConnectionInfo();
    }

    @Test
    void jiraServiceHandlesEmptyJsonFields() throws JsonProcessingException {
        String connectionInfoWithNulls = "{\"baseUrl\":null,\"apiToken\":null,\"username\":null}";
        when(integrationToken.getConnectionInfo()).thenReturn(connectionInfoWithNulls);
        
        JiraService service = new JiraService(restTemplate, integrationToken);
        assertNotNull(service);
    }
}