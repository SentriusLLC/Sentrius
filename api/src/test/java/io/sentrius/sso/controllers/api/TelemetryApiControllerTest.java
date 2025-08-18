package io.sentrius.sso.controllers.api;

import io.sentrius.sso.core.config.SystemOptions;
import io.sentrius.sso.core.services.ErrorOutputService;
import io.sentrius.sso.core.services.UserService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
class TelemetryApiControllerTest {

    @Mock
    private UserService userService;

    @Mock
    private SystemOptions systemOptions;

    @Mock
    private ErrorOutputService errorOutputService;

    @InjectMocks
    private TelemetryApiController telemetryApiController;

    @Test
    void testGetTracesHandlesInvalidJaegerUrl() {
        // Set an invalid Jaeger URL to test error handling
        ReflectionTestUtils.setField(telemetryApiController, "jaegerQueryUrl", "invalid-url");

        ResponseEntity<?> response = telemetryApiController.getTraces(
            "test-service", null, "1h", null, null, null
        );

        assertEquals(500, response.getStatusCode().value());
        assertNotNull(response.getBody());
    }

    @Test
    void testGetTracesWithValidParameters() {
        // Set a mock Jaeger URL
        ReflectionTestUtils.setField(telemetryApiController, "jaegerQueryUrl", "http://localhost:16686");

        // This test will fail to connect to Jaeger, but should not throw exception
        ResponseEntity<?> response = telemetryApiController.getTraces(
            "sentrius-api", "test-operation", "1h", 1000L, 10000L, "error=true"
        );

        // Should return 500 since we can't connect to real Jaeger, but shouldn't crash
        assertEquals(500, response.getStatusCode().value());
    }

    @Test
    void testGetServicesWithInvalidUrl() {
        ReflectionTestUtils.setField(telemetryApiController, "jaegerQueryUrl", "invalid-url");

        ResponseEntity<?> response = telemetryApiController.getServices();

        assertEquals(500, response.getStatusCode().value());
        assertNotNull(response.getBody());
    }

    @Test
    void testGetTraceByIdWithInvalidUrl() {
        ReflectionTestUtils.setField(telemetryApiController, "jaegerQueryUrl", "invalid-url");

        ResponseEntity<?> response = telemetryApiController.getTrace("test-trace-id");

        assertEquals(500, response.getStatusCode().value());
        assertNotNull(response.getBody());
    }
}