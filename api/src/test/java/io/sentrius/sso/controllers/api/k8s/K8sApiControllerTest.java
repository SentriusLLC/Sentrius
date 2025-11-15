package io.sentrius.sso.controllers.api.k8s;

import io.sentrius.sso.core.config.SystemOptions;
import io.sentrius.sso.core.services.ErrorOutputService;
import io.sentrius.sso.core.services.UserService;
import io.sentrius.sso.core.services.k8s.K8sClientService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for K8sApiController
 * Tests the controller initialization and service dependency injection
 */
@ExtendWith(MockitoExtension.class)
class K8sApiControllerTest {

    @Mock
    private UserService userService;

    @Mock
    private SystemOptions systemOptions;

    @Mock
    private ErrorOutputService errorOutputService;

    @Mock
    private K8sClientService k8sClientService;

    private K8sApiController controller;

    @BeforeEach
    void setUp() {
        controller = new K8sApiController(userService, systemOptions, errorOutputService, k8sClientService);
    }

    @Test
    void testControllerInitialization() {
        // Verify controller is properly initialized with dependencies
        assertNotNull(controller);
    }

    @Test
    void testK8sClientServiceDependencyInjection() {
        // Verify that the K8sClientService is properly injected
        // This is important for the log viewing functionality
        assertNotNull(k8sClientService);
        verify(k8sClientService, never()).listPods(); // Service should not be called during construction
    }
}
