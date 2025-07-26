package io.sentrius.sso.controllers.api;

import io.sentrius.sso.config.ApplicationEnvironmentConfig;
import io.sentrius.sso.core.config.SystemOptions;
import io.sentrius.sso.core.model.security.IntegrationSecurityToken;
import io.sentrius.sso.core.model.users.User;
import io.sentrius.sso.core.services.ErrorOutputService;
import io.sentrius.sso.core.services.UserService;
import io.sentrius.sso.core.services.security.IntegrationSecurityTokenService;
import io.sentrius.sso.core.services.security.KeycloakService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.Arrays;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class JiraProxyControllerTest {

    @Mock
    private UserService userService;

    @Mock
    private SystemOptions systemOptions;

    @Mock
    private ErrorOutputService errorOutputService;

    @Mock
    private KeycloakService keycloakService;

    @Mock
    private IntegrationSecurityTokenService integrationSecurityTokenService;

    @Mock
    private RestTemplateBuilder restTemplateBuilder;

    @Mock
    private ApplicationEnvironmentConfig applicationConfig;

    @Mock
    private User mockUser;

    private JiraProxyController jiraProxyController;
    private MockHttpServletRequest request;
    private MockHttpServletResponse response;

    @BeforeEach
    void setUp() {
        jiraProxyController = spy(new JiraProxyController(
            userService, systemOptions, errorOutputService,
            keycloakService, integrationSecurityTokenService,
            restTemplateBuilder, applicationConfig
        ));
        request = new MockHttpServletRequest();
        response = new MockHttpServletResponse();
    }

    @Test
    void searchReturnsUnauthorizedWhenTokenIsInvalid() throws Exception {
        // Given
        String invalidToken = "Bearer invalid-token";
        when(keycloakService.validateJwt("invalid-token")).thenReturn(false);

        // When
        ResponseEntity<?> result = jiraProxyController.searchForJiraIssue(
            invalidToken, "test query", null, request, response
        );

        // Then
        assertEquals(HttpStatus.UNAUTHORIZED.value(), result.getStatusCode().value());
        assertEquals("Invalid Keycloak token", result.getBody());
    }

    @Test
    void searchReturnsNotFoundWhenNoJiraIntegrationConfigured() throws Exception {
        // Given
        String validToken = "Bearer valid-token";
        when(keycloakService.validateJwt("valid-token")).thenReturn(true);
        doReturn(mockUser).when(jiraProxyController).getOperatingUser(any(), any());
        when(integrationSecurityTokenService.findByConnectionType("jira"))
            .thenReturn(Collections.emptyList());

        // When
        ResponseEntity<?> result = jiraProxyController.searchForJiraIssue(
            validToken, "test query", null, request, response
        );

        // Then
        assertEquals(HttpStatus.NOT_FOUND.value(), result.getStatusCode().value());
        assertEquals("No JIRA integration configured", result.getBody());
    }

    @Test
    void searchReturnsBadRequestWhenNoQueryProvided() throws Exception {
        // Given
        String validToken = "Bearer valid-token";
        when(keycloakService.validateJwt("valid-token")).thenReturn(true);
        doReturn(mockUser).when(jiraProxyController).getOperatingUser(any(), any());
        
        IntegrationSecurityToken mockToken = mock(IntegrationSecurityToken.class);
        when(mockToken.getConnectionInfo()).thenReturn(
            "{\"baseUrl\":\"https://test.atlassian.net\",\"apiToken\":\"token\",\"username\":\"user\"}"
        );
        when(integrationSecurityTokenService.findByConnectionType("jira"))
            .thenReturn(Arrays.asList(mockToken));

        // When
        ResponseEntity<?> result = jiraProxyController.searchForJiraIssue(
            validToken, null, null, request, response
        );

        // Then
        assertEquals(HttpStatus.BAD_REQUEST.value(), result.getStatusCode().value());
        assertEquals("Either 'jql' or 'query' parameter is required", result.getBody());
    }

    @Test
    void getIssueReturnsUnauthorizedWhenTokenIsInvalid() throws Exception {
        // Given
        String invalidToken = "Bearer invalid-token";
        when(keycloakService.validateJwt("invalid-token")).thenReturn(false);

        // When
        ResponseEntity<?> result = jiraProxyController.fetchJiraIssue(
            invalidToken, "TEST-123", request, response
        );

        // Then
        assertEquals(HttpStatus.UNAUTHORIZED.value(), result.getStatusCode().value());
        assertEquals("Invalid Keycloak token", result.getBody());
    }

    @Test
    void getIssueReturnsNotFoundWhenNoJiraIntegrationConfigured() throws Exception {
        // Given
        String validToken = "Bearer valid-token";
        when(keycloakService.validateJwt("valid-token")).thenReturn(true);
        doReturn(mockUser).when(jiraProxyController).getOperatingUser(any(), any());
        when(integrationSecurityTokenService.findByConnectionType("jira"))
            .thenReturn(Collections.emptyList());

        // When
        ResponseEntity<?> result = jiraProxyController.fetchJiraIssue(
            validToken, "TEST-123", request, response
        );

        // Then
        assertEquals(HttpStatus.NOT_FOUND.value(), result.getStatusCode().value());
        assertEquals("No JIRA integration configured", result.getBody());
    }

    @Test
    void addCommentReturnsUnauthorizedWhenTokenIsInvalid() throws Exception {
        // Given
        String invalidToken = "Bearer invalid-token";
        when(keycloakService.validateJwt("invalid-token")).thenReturn(false);
        
        JiraProxyController.CommentRequest commentRequest = new JiraProxyController.CommentRequest();
        commentRequest.setText("Test comment");

        // When
        ResponseEntity<?> result = jiraProxyController.addComment(
            invalidToken, "TEST-123", commentRequest, request, response
        );

        // Then
        assertEquals(HttpStatus.UNAUTHORIZED.value(), result.getStatusCode().value());
        assertEquals("Invalid Keycloak token", result.getBody());
    }

    @Test
    void assignIssueReturnsUnauthorizedWhenTokenIsInvalid() throws Exception {
        // Given
        String invalidToken = "Bearer invalid-token";
        when(keycloakService.validateJwt("invalid-token")).thenReturn(false);
        
        JiraProxyController.AssigneeRequest assigneeRequest = new JiraProxyController.AssigneeRequest();
        assigneeRequest.setAccountId("test-account-id");

        // When
        ResponseEntity<?> result = jiraProxyController.assignJiraIssue(
            invalidToken, "TEST-123", assigneeRequest, request, response
        );

        // Then
        assertEquals(HttpStatus.UNAUTHORIZED.value(), result.getStatusCode().value());
        assertEquals("Invalid Keycloak token", result.getBody());
    }

    @Test
    void extractCommentTextHandlesSimpleText() {
        // Given
        JiraProxyController.CommentRequest commentRequest = new JiraProxyController.CommentRequest();
        commentRequest.setText("Simple text comment");

        // When
        String result = invokePrivateExtractCommentText(commentRequest);

        // Then
        assertEquals("Simple text comment", result);
    }

    @Test
    void extractCommentTextHandlesBodyAsString() {
        // Given
        JiraProxyController.CommentRequest commentRequest = new JiraProxyController.CommentRequest();
        commentRequest.setBody("Body as string");

        // When
        String result = invokePrivateExtractCommentText(commentRequest);

        // Then
        assertEquals("Body as string", result);
    }

    // Helper method to access private method for testing
    private String invokePrivateExtractCommentText(JiraProxyController.CommentRequest commentRequest) {
        try {
            java.lang.reflect.Method method = JiraProxyController.class.getDeclaredMethod(
                "extractCommentText", JiraProxyController.CommentRequest.class);
            method.setAccessible(true);
            return (String) method.invoke(jiraProxyController, commentRequest);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}