package io.sentrius.sso.controllers.api;

import io.sentrius.sso.core.config.SystemOptions;
import io.sentrius.sso.core.dto.tooltip.TooltipChatRequest;
import io.sentrius.sso.core.dto.tooltip.TooltipChatResponse;
import io.sentrius.sso.core.dto.tooltip.TooltipDescribeRequest;
import io.sentrius.sso.core.dto.tooltip.TooltipDescribeResponse;
import io.sentrius.sso.core.dto.ztat.TokenDTO;
import io.sentrius.sso.core.model.users.User;
import io.sentrius.sso.core.services.ErrorOutputService;
import io.sentrius.sso.core.services.UserService;
import io.sentrius.sso.core.services.tooltip.CodebaseIndexingService;
import io.sentrius.sso.core.services.tooltip.TooltipService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Unit tests for TooltipController
 */
@ExtendWith(MockitoExtension.class)
class TooltipControllerTest {

    @Mock
    private TooltipService tooltipService;

    @Mock
    private CodebaseIndexingService indexingService;

    @Mock
    private UserService userService;

    @Mock
    private SystemOptions systemOptions;

    @Mock
    private ErrorOutputService errorOutputService;

    @Mock
    private HttpServletRequest httpRequest;

    @Mock
    private HttpServletResponse httpResponse;

    private TooltipController tooltipController;

    @BeforeEach
    void setUp() {
        tooltipController = new TooltipController(
                userService, systemOptions, errorOutputService, tooltipService, indexingService);

        // Mock user service to return a valid user
        User mockUser = new User();
        mockUser.setUserId("test-user");
        lenient().when(userService.getOperatingUser(any(), any(), any()))
                .thenReturn(mockUser);
    }

    @Test
    void testDescribe_ValidRequest_ReturnsDescription() {
        // Arrange
        TooltipDescribeRequest.ElementContext context = TooltipDescribeRequest.ElementContext.builder()
                .tagName("BUTTON")
                .id("submit-btn")
                .textContent("Submit")
                .build();

        TooltipDescribeRequest request = TooltipDescribeRequest.builder()
                .context(context)
                .timestamp(System.currentTimeMillis())
                .build();

        TooltipDescribeResponse expectedResponse = TooltipDescribeResponse.builder()
                .description("This button submits the form data.")
                .message("This button submits the form data.")
                .success(true)
                .build();

        when(tooltipService.describeElement(any(TooltipDescribeRequest.class), any(TokenDTO.class)))
                .thenReturn(expectedResponse);

        // Act
        ResponseEntity<TooltipDescribeResponse> response = tooltipController.describe(
                request, httpRequest, httpResponse);

        // Assert
        assertNotNull(response);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertTrue(response.getBody().isSuccess());
        assertEquals("This button submits the form data.", response.getBody().getDescription());
        verify(tooltipService).describeElement(any(TooltipDescribeRequest.class), any(TokenDTO.class));
    }

    @Test
    void testDescribe_NullContext_ReturnsBadRequest() {
        // Arrange
        TooltipDescribeRequest request = TooltipDescribeRequest.builder()
                .context(null)
                .timestamp(System.currentTimeMillis())
                .build();

        // Act
        ResponseEntity<TooltipDescribeResponse> response = tooltipController.describe(
                request, httpRequest, httpResponse);

        // Assert
        assertNotNull(response);
        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertNotNull(response.getBody());
        assertFalse(response.getBody().isSuccess());
        assertTrue(response.getBody().getDescription().contains("context is required"));
        verify(tooltipService, never()).describeElement(any(), any());
    }

    @Test
    void testDescribe_ServiceException_ReturnsInternalServerError() {
        // Arrange
        TooltipDescribeRequest.ElementContext context = TooltipDescribeRequest.ElementContext.builder()
                .tagName("BUTTON")
                .id("submit-btn")
                .build();

        TooltipDescribeRequest request = TooltipDescribeRequest.builder()
                .context(context)
                .build();

        when(tooltipService.describeElement(any(TooltipDescribeRequest.class), any(TokenDTO.class)))
                .thenThrow(new RuntimeException("Service error"));

        // Act
        ResponseEntity<TooltipDescribeResponse> response = tooltipController.describe(
                request, httpRequest, httpResponse);

        // Assert
        assertNotNull(response);
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
        assertNotNull(response.getBody());
        assertFalse(response.getBody().isSuccess());
        assertNotNull(response.getBody().getError());
    }

    @Test
    void testChat_ValidRequest_ReturnsResponse() {
        // Arrange
        TooltipChatRequest request = TooltipChatRequest.builder()
                .message("What does the submit button do?")
                .timestamp(System.currentTimeMillis())
                .build();

        TooltipChatResponse expectedResponse = TooltipChatResponse.builder()
                .response("The submit button sends your form data to the server.")
                .message("The submit button sends your form data to the server.")
                .success(true)
                .build();

        when(tooltipService.chat(any(TooltipChatRequest.class), any(TokenDTO.class)))
                .thenReturn(expectedResponse);

        // Act
        ResponseEntity<TooltipChatResponse> response = tooltipController.chat(
                request, httpRequest, httpResponse);

        // Assert
        assertNotNull(response);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertTrue(response.getBody().isSuccess());
        assertEquals("The submit button sends your form data to the server.", 
                response.getBody().getResponse());
        verify(tooltipService).chat(any(TooltipChatRequest.class), any(TokenDTO.class));
    }

    @Test
    void testChat_EmptyMessage_ReturnsBadRequest() {
        // Arrange
        TooltipChatRequest request = TooltipChatRequest.builder()
                .message("")
                .timestamp(System.currentTimeMillis())
                .build();

        // Act
        ResponseEntity<TooltipChatResponse> response = tooltipController.chat(
                request, httpRequest, httpResponse);

        // Assert
        assertNotNull(response);
        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertNotNull(response.getBody());
        assertFalse(response.getBody().isSuccess());
        assertTrue(response.getBody().getResponse().contains("message is required"));
        verify(tooltipService, never()).chat(any(), any());
    }

    @Test
    void testChat_NullMessage_ReturnsBadRequest() {
        // Arrange
        TooltipChatRequest request = TooltipChatRequest.builder()
                .message(null)
                .timestamp(System.currentTimeMillis())
                .build();

        // Act
        ResponseEntity<TooltipChatResponse> response = tooltipController.chat(
                request, httpRequest, httpResponse);

        // Assert
        assertNotNull(response);
        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertNotNull(response.getBody());
        assertFalse(response.getBody().isSuccess());
        verify(tooltipService, never()).chat(any(), any());
    }

    @Test
    void testChat_WithElementContext_IncludesContext() {
        // Arrange
        TooltipDescribeRequest.ElementContext context = TooltipDescribeRequest.ElementContext.builder()
                .tagName("INPUT")
                .id("username")
                .textContent("")
                .build();

        TooltipChatRequest request = TooltipChatRequest.builder()
                .message("What should I enter here?")
                .context(context)
                .timestamp(System.currentTimeMillis())
                .build();

        TooltipChatResponse expectedResponse = TooltipChatResponse.builder()
                .response("Enter your username for authentication.")
                .success(true)
                .build();

        when(tooltipService.chat(any(TooltipChatRequest.class), any(TokenDTO.class)))
                .thenReturn(expectedResponse);

        // Act
        ResponseEntity<TooltipChatResponse> response = tooltipController.chat(
                request, httpRequest, httpResponse);

        // Assert
        assertNotNull(response);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertTrue(response.getBody().isSuccess());
        verify(tooltipService).chat(any(TooltipChatRequest.class), any(TokenDTO.class));
    }
}
