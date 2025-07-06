package io.sentrius.sso.controllers.api;

import io.sentrius.sso.core.dto.capabilities.EndpointDescriptor;
import io.sentrius.sso.core.services.capabilities.EndpointScanningService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration test for CapabilitiesApiController.
 * This test runs with the full Spring context to verify that endpoint scanning works correctly.
 */
@SpringBootTest
@TestPropertySource(properties = {
    "spring.datasource.url=jdbc:h2:mem:testdb",
    "spring.jpa.hibernate.ddl-auto=create-drop"
})
public class CapabilitiesApiControllerIntegrationTest {

    @Autowired
    private EndpointScanningService endpointScanningService;

    @Test
    public void testEndpointScanning() {
        // When
        List<EndpointDescriptor> allEndpoints = endpointScanningService.getAllEndpoints();

        // Then
        assertNotNull(allEndpoints);
        
        System.out.println("=== ENDPOINT SCANNING RESULTS ===");
        System.out.println("Total endpoints found: " + allEndpoints.size());
        
        // Count by type
        long restCount = allEndpoints.stream().filter(e -> "REST".equals(e.getType())).count();
        long verbCount = allEndpoints.stream().filter(e -> "VERB".equals(e.getType())).count();
        
        System.out.println("REST endpoints: " + restCount);
        System.out.println("VERB endpoints: " + verbCount);
        
        // Print first 10 endpoints for inspection
        System.out.println("\n=== SAMPLE ENDPOINTS ===");
        allEndpoints.stream().limit(10).forEach(endpoint -> {
            System.out.println(String.format("%s: %s [%s] - %s", 
                    endpoint.getType(),
                    endpoint.getName(),
                    endpoint.getHttpMethod() != null ? endpoint.getHttpMethod() + " " + endpoint.getPath() : "N/A",
                    endpoint.getDescription()));
        });
        
        // Verify we found some endpoints
        assertTrue(allEndpoints.size() > 0, "Should have found some endpoints in full Spring context");
        
        // Verify we found some REST endpoints (from the API controllers)
        assertTrue(restCount > 0, "Should have found REST endpoints from API controllers");
        
        // Look for our new capabilities endpoint
        boolean foundCapabilitiesEndpoint = allEndpoints.stream()
                .anyMatch(e -> "REST".equals(e.getType()) && 
                              e.getPath() != null && 
                              e.getPath().contains("/api/v1/capabilities"));
        
        assertTrue(foundCapabilitiesEndpoint, "Should have found our new capabilities endpoint");
        
        // Look for some existing endpoints
        boolean foundUserApiEndpoint = allEndpoints.stream()
                .anyMatch(e -> "REST".equals(e.getType()) && 
                              e.getClassName().contains("UserApiController"));
        
        assertTrue(foundUserApiEndpoint, "Should have found UserApiController endpoints");
    }

    @Test
    public void testEndpointFiltering() {
        // When
        List<EndpointDescriptor> allEndpoints = endpointScanningService.getAllEndpoints();
        
        // Filter REST endpoints
        List<EndpointDescriptor> restEndpoints = allEndpoints.stream()
                .filter(e -> "REST".equals(e.getType()))
                .toList();
        
        // Filter VERB endpoints  
        List<EndpointDescriptor> verbEndpoints = allEndpoints.stream()
                .filter(e -> "VERB".equals(e.getType()))
                .toList();
        
        // Then
        System.out.println("\n=== FILTERING TEST ===");
        System.out.println("Total: " + allEndpoints.size());
        System.out.println("REST only: " + restEndpoints.size());
        System.out.println("VERB only: " + verbEndpoints.size());
        
        assertEquals(allEndpoints.size(), restEndpoints.size() + verbEndpoints.size(),
                "Total should equal sum of REST and VERB endpoints");
        
        // Verify REST endpoints have paths
        restEndpoints.forEach(endpoint -> {
            assertNotNull(endpoint.getPath(), "REST endpoint should have a path");
            assertNotNull(endpoint.getHttpMethod(), "REST endpoint should have HTTP method");
        });
    }
}