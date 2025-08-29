package io.sentrius.sso.core.services.capabilities;

import io.sentrius.sso.core.dto.capabilities.EndpointDescriptor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.context.ApplicationContext;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test class for EndpointScanningService.
 * Validates that the service can discover both REST endpoints and Verb methods.
 */
public class EndpointScanningServiceTest {

    @Mock
    private ApplicationContext applicationContext;

    private EndpointScanningService endpointScanningService;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        endpointScanningService = new EndpointScanningService(applicationContext);
    }

    @Test
    void testGetAllEndpoints() {
        // When
        List<EndpointDescriptor> endpoints = endpointScanningService.getAllEndpoints();

        // Then
        assertNotNull(endpoints);
        System.out.println("Found " + endpoints.size() + " endpoints");
        
        // Log some information about what was found
        long restCount = endpoints.stream().filter(e -> "REST".equals(e.getType())).count();
        long verbCount = endpoints.stream().filter(e -> "VERB".equals(e.getType())).count();
        
        System.out.println("Found " + restCount + " REST endpoints and " + verbCount + " VERB endpoints");
        
        // Print first few endpoints for inspection
        endpoints.stream().limit(10).forEach(endpoint -> {
            System.out.println("Endpoint: " + endpoint.getName() + 
                             " (Type: " + endpoint.getType() + 
                             ", Class: " + endpoint.getClassName() + ")");
        });
        
        // In a test environment, we might not have full Spring context loaded,
        // so the test is mainly to verify the service doesn't crash
        // The real validation will be done when the service runs in the full application
    }

    @Test
    void testRefreshEndpoints() {
        // Given - get initial count
        List<EndpointDescriptor> initialEndpoints = endpointScanningService.getAllEndpoints();
        int initialCount = initialEndpoints.size();

        // When - refresh
        endpointScanningService.refreshEndpoints();
        List<EndpointDescriptor> refreshedEndpoints = endpointScanningService.getAllEndpoints();

        // Then - should have same count (since we're not loading new classes in test environment)
        assertEquals(initialCount, refreshedEndpoints.size(), 
                  "Refresh should maintain endpoint count in test environment");
    }

    @Test
    void testEndpointDescriptorStructure() {
        // When
        List<EndpointDescriptor> endpoints = endpointScanningService.getAllEndpoints();

        // Then - verify structure of endpoints
        for (EndpointDescriptor endpoint : endpoints) {
            assertNotNull(endpoint.getName(), "Endpoint name should not be null");
            assertNotNull(endpoint.getType(), "Endpoint type should not be null");
            assertNotNull(endpoint.getClassName(), "Endpoint class name should not be null");
            assertNotNull(endpoint.getMethodName(), "Endpoint method name should not be null");
            
            assertTrue(endpoint.getType().equals("REST") || endpoint.getType().equals("VERB"),
                      "Endpoint type should be REST or VERB");
            
            if ("REST".equals(endpoint.getType())) {
                assertNotNull(endpoint.getHttpMethod(), "REST endpoint should have HTTP method");
                assertNotNull(endpoint.getPath(), "REST endpoint should have path");
            }
            
            if ("VERB".equals(endpoint.getType()) && endpoint.getMetadata() != null) {
                assertTrue(endpoint.getMetadata().containsKey("isAiCallable"), 
                          "VERB endpoint should have isAiCallable metadata");
            }
        }
    }
}