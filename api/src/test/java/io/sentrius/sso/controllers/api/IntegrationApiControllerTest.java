package io.sentrius.sso.controllers.api;

import com.fasterxml.jackson.core.JsonProcessingException;
import io.sentrius.sso.core.config.SystemOptions;
import io.sentrius.sso.core.integrations.external.ExternalIntegrationDTO;
import io.sentrius.sso.core.model.security.IntegrationSecurityToken;
import io.sentrius.sso.core.services.ErrorOutputService;
import io.sentrius.sso.core.services.UserService;
import io.sentrius.sso.core.services.security.CryptoService;
import io.sentrius.sso.core.services.security.IntegrationSecurityTokenService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.security.GeneralSecurityException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class IntegrationApiControllerTest {

    @Mock
    private UserService userService;
    
    @Mock
    private SystemOptions systemOptions;
    
    @Mock
    private ErrorOutputService errorOutputService;
    
    @Mock
    private IntegrationSecurityTokenService integrationService;
    
    @Mock
    private CryptoService cryptoService;
    
    @Mock
    private HttpServletRequest request;
    
    @Mock
    private HttpServletResponse response;

    private IntegrationApiController controller;

    @BeforeEach
    void setUp() {
        controller = new IntegrationApiController(
            userService, systemOptions, errorOutputService, 
            integrationService, cryptoService
        );
    }

    @Test
    void addOpenaiIntegrationReturnsSuccessForValidDTO() throws JsonProcessingException, GeneralSecurityException {
        ExternalIntegrationDTO dto = new ExternalIntegrationDTO();
        dto.setName("TestOpenAI");
        dto.setApiToken("test-token");
        
        IntegrationSecurityToken savedToken = IntegrationSecurityToken.builder()
            .id(1L)
            .connectionType("openai")
            .name("TestOpenAI")
            .connectionInfo("{\"name\":\"TestOpenAI\",\"apiToken\":\"test-token\"}")
            .build();
        
        when(integrationService.save(any(IntegrationSecurityToken.class))).thenReturn(savedToken);

        ResponseEntity<ExternalIntegrationDTO> result = controller.addOpenaiIntegration(request, response, dto);

        assertEquals(HttpStatus.OK, result.getStatusCode());
        assertNotNull(result.getBody());
        assertEquals("TestOpenAI", result.getBody().getName());
        verify(integrationService).save(any(IntegrationSecurityToken.class));
    }

    @Test
    void deleteJiraIntegrationReturnsSuccessForValidId() throws JsonProcessingException {
        doNothing().when(integrationService).deleteById(1L);

        ResponseEntity<String> result = controller.deleteJiraIntegration(request, response, "1");

        assertEquals(HttpStatus.OK, result.getStatusCode());
        assertEquals("OK", result.getBody());
        verify(integrationService).deleteById(1L);
    }

    @Test
    void deleteIntegrationReturnsSuccessForValidId() {
        doNothing().when(integrationService).deleteById(1L);

        ResponseEntity<String> result = controller.deleteIntegration(request, response, "1");

        assertEquals(HttpStatus.OK, result.getStatusCode());
        assertEquals("OK", result.getBody());
        verify(integrationService).deleteById(1L);
    }

    @Test
    void controllerCanBeInstantiated() {
        IntegrationApiController testController = new IntegrationApiController(
            userService, systemOptions, errorOutputService, 
            integrationService, cryptoService
        );
        assertNotNull(testController);
    }

    @Test
    void fieldsMapIsInitialized() {
        // Test that the static fields map is properly initialized
        assertNotNull(IntegrationApiController.fields);
        // The fields map should contain UserConfig fields
        assertFalse(IntegrationApiController.fields.isEmpty());
    }
}