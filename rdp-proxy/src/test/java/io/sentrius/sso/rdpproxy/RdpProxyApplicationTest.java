package io.sentrius.sso.rdpproxy;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class RdpProxyApplicationTest {

    @Test
    void testMainMethod() {
        // Simple test to verify the main method exists and can be called
        assertDoesNotThrow(() -> {
            String[] args = {};
            // We're not actually calling main() as it would start the application
            // This test just verifies the class structure is correct
            RdpProxyApplication app = new RdpProxyApplication();
            assertNotNull(app);
        });
    }
}