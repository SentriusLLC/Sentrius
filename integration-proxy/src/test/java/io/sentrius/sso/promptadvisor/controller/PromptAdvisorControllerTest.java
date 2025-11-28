package io.sentrius.sso.promptadvisor.controller;

import io.sentrius.sso.core.config.SystemOptions;
import io.sentrius.sso.core.model.users.User;
import io.sentrius.sso.core.services.ErrorOutputService;
import io.sentrius.sso.core.services.UserService;
import io.sentrius.sso.core.promptadvisor.model.ValidatePromptRequest;
import io.sentrius.sso.core.promptadvisor.model.ValidatePromptResponse;
import io.sentrius.sso.core.promptadvisor.service.PromptAdvisorService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PromptAdvisorControllerTest {

    @Mock
    private UserService userService;

    @Mock
    private SystemOptions systemOptions;

    @Mock
    private ErrorOutputService errorOutputService;

    @Mock
    private PromptAdvisorService promptAdvisorService;

    @Mock
    private User mockUser;

    private PromptAdvisorController controller;
    private MockHttpServletRequest request;
    private MockHttpServletResponse response;

    @BeforeEach
    void setUp() {
        controller = spy(new PromptAdvisorController(
            userService, systemOptions, errorOutputService, promptAdvisorService
        ));
        request = new MockHttpServletRequest();
        response = new MockHttpServletResponse();
    }

    @Test
    void validatePromptReturnsServiceUnavailableWhenDisabled() {
        // Given
        when(systemOptions.getEnablePromptAdvisor()).thenReturn(false);
        ValidatePromptRequest requestBody = ValidatePromptRequest.builder()
            .prompt("Test prompt")
            .build();

        // When
        ResponseEntity<?> result = controller.validatePrompt(requestBody, request, response);

        // Then
        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, result.getStatusCode());
        verify(promptAdvisorService, never()).validatePrompt(anyString(), any());
    }

    @Test
    void validatePromptReturnsUnauthorizedWhenUserNotFound() {
        // Given
        when(systemOptions.getEnablePromptAdvisor()).thenReturn(true);
        doReturn(null).when(controller).getOperatingUser(request, response);
        ValidatePromptRequest requestBody = ValidatePromptRequest.builder()
            .prompt("Test prompt")
            .build();

        // When
        ResponseEntity<?> result = controller.validatePrompt(requestBody, request, response);

        // Then
        assertEquals(HttpStatus.UNAUTHORIZED, result.getStatusCode());
        verify(promptAdvisorService, never()).validatePrompt(anyString(), any());
    }

    @Test
    void validatePromptReturnsSuccessWhenEnabled() {
        // Given
        when(systemOptions.getEnablePromptAdvisor()).thenReturn(true);
        doReturn(mockUser).when(controller).getOperatingUser(request, response);
        when(mockUser.getUsername()).thenReturn("testuser");

        ValidatePromptRequest requestBody = ValidatePromptRequest.builder()
            .prompt("Test prompt")
            .build();

        Map<String, Integer> ratings = new HashMap<>();
        ratings.put("purpose", 8);
        ratings.put("safety", 9);

        ValidatePromptResponse expectedResponse = ValidatePromptResponse.builder()
            .score(85)
            .ratings(ratings)
            .explanation("Test explanation")
            .recommendations(Arrays.asList("recommendation 1", "recommendation 2"))
            .build();

        when(promptAdvisorService.validatePrompt(anyString(), any()))
            .thenReturn(expectedResponse);

        // When
        ResponseEntity<?> result = controller.validatePrompt(requestBody, request, response);

        // Then
        assertEquals(HttpStatus.OK, result.getStatusCode());
        assertTrue(result.getBody() instanceof ValidatePromptResponse);
        ValidatePromptResponse responseBody = (ValidatePromptResponse) result.getBody();
        assertEquals(85, responseBody.getScore());
    }

    @Test
    void refinePromptReturnsRefinedPrompt() {
        // Given
        when(systemOptions.getEnablePromptAdvisor()).thenReturn(true);
        doReturn(mockUser).when(controller).getOperatingUser(request, response);
        when(mockUser.getUsername()).thenReturn("testuser");

        ValidatePromptRequest requestBody = ValidatePromptRequest.builder()
            .prompt("Original prompt")
            .build();

        when(promptAdvisorService.refinePrompt(anyString(), any()))
            .thenReturn("Refined prompt");

        // When
        ResponseEntity<?> result = controller.refinePrompt(requestBody, request, response);

        // Then
        assertEquals(HttpStatus.OK, result.getStatusCode());
        assertTrue(result.getBody() instanceof Map);
        Map<?, ?> responseBody = (Map<?, ?>) result.getBody();
        assertEquals("Original prompt", responseBody.get("original_prompt"));
        assertEquals("Refined prompt", responseBody.get("refined_prompt"));
    }

    @Test
    void getStatusReturnsConfiguration() {
        // Given
        when(systemOptions.getEnablePromptAdvisor()).thenReturn(true);
        when(systemOptions.getPromptAdvisorThreshold()).thenReturn(70);
        when(systemOptions.getPromptAdvisorMaxIterations()).thenReturn(3);
        when(systemOptions.getPromptAdvisorEndpoint()).thenReturn("http://test-endpoint");
        doReturn(mockUser).when(controller).getOperatingUser(request, response);

        // When
        ResponseEntity<?> result = controller.getStatus(request, response);

        // Then
        assertEquals(HttpStatus.OK, result.getStatusCode());
        assertTrue(result.getBody() instanceof Map);
        Map<?, ?> responseBody = (Map<?, ?>) result.getBody();
        assertEquals(true, responseBody.get("enabled"));
        assertEquals(70, responseBody.get("threshold"));
        assertEquals(3, responseBody.get("max_iterations"));
        assertEquals("http://test-endpoint", responseBody.get("endpoint"));
    }
}
