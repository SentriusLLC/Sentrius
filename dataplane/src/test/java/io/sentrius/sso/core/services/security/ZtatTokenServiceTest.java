package io.sentrius.sso.core.services.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jws;
import io.sentrius.sso.core.config.SystemOptions;
import io.sentrius.sso.core.services.agents.ZeroTrustClientService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.security.PublicKey;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for ZtatTokenService with independent key management
 */
@ExtendWith(MockitoExtension.class)
class ZtatTokenServiceTest {

    @Mock
    private CryptoService cryptoService;

    @Mock
    private ZeroTrustClientService zeroTrustClientService;

    @Mock
    private SystemOptions systemOptions;

    private ZtatTokenService ztatTokenService;

    @BeforeEach
    void setUp() {
        // Mock crypto service to return a test key
        byte[] testKey = new byte[32];
        for (int i = 0; i < 32; i++) {
            testKey[i] = (byte) i;
        }
        when(cryptoService.getKey()).thenReturn(testKey);

        ztatTokenService = new ZtatTokenService(cryptoService, zeroTrustClientService, systemOptions);
    }

    @Test
    void testIssueServiceTokenForRdpProxy() {
        // Test that service tokens are generated for rdp-proxy audience with local keys
        String token = ztatTokenService.issueServiceToken(
            "testuser",
            "rdp-proxy",
            "rdp-dev",
            300
        );

        assertNotNull(token);
        assertTrue(token.length() > 0);
        // Token should have 3 parts (header.payload.signature)
        assertEquals(3, token.split("\\.").length);
    }
    @Test
    void testIssueServiceTokenForNonRdpProxy() {
        // Test that service tokens work for other audiences
        String token = ztatTokenService.issueServiceToken(
            "testuser",
            "other-service",
            "target-machine",
            300
        );

        assertNotNull(token);
        assertTrue(token.length() > 0);
        assertEquals(3, token.split("\\.").length);
    }

    @Test
    void testIssueZtat() {
        try {
            // Mock hash method for fingerprint computation
            when(cryptoService.hash(anyString(), isNull()))
                .thenReturn("test-fingerprint");

            String token = ztatTokenService.issueZtat(
                "agent-123",
                "session-456",
                "publicKeyBase64String"
            );

            assertNotNull(token);
            assertTrue(token.length() > 0);
            assertEquals(3, token.split("\\.").length);

            // Parse and verify the token
            Jws<Claims> jws = ztatTokenService.parseZtat(token);
            assertNotNull(jws);
            assertEquals("ztat-auth", jws.getBody().getSubject());
            assertEquals("agent-123", jws.getBody().get("agentId"));
            assertEquals("session-456", jws.getBody().get("sessionId"));
            assertNotNull(jws.getBody().get("keyfp"));
        } catch (Exception e) {
            fail("Test should not throw exception: " + e.getMessage());
        }
    }

    @Test
    void testComputeFingerprint() {
        try {
            // Mock the hash method
            when(cryptoService.hash(anyString(), isNull()))
                .thenReturn("mocked-fingerprint-hash");

            String fingerprint = ztatTokenService.computeFingerprint("testPublicKey");

            assertNotNull(fingerprint);
            assertEquals("mocked-fingerprint-hash", fingerprint);
            verify(cryptoService, times(1)).hash("testPublicKey", null);
        } catch (Exception e) {
            fail("Test should not throw exception: " + e.getMessage());
        }
    }

    @Test
    void testParseZtat() {
        try {
            // Mock hash method for fingerprint computation
            when(cryptoService.hash(anyString(), isNull()))
                .thenReturn("test-fingerprint");

            // Create a token
            String token = ztatTokenService.issueZtat(
                "agent-999",
                "session-888",
                "testPublicKey"
            );

            // Parse it
            Jws<Claims> jws = ztatTokenService.parseZtat(token);

            assertNotNull(jws);
            assertNotNull(jws.getBody());
            assertEquals("ztat-auth", jws.getBody().getSubject());
            assertEquals("agent-999", jws.getBody().get("agentId"));
            assertEquals("session-888", jws.getBody().get("sessionId"));
        } catch (Exception e) {
            fail("Test should not throw exception: " + e.getMessage());
        }
    }

    @Test
    void testGetSigningKey() {
        assertNotNull(ztatTokenService.getSigningKey());
    }

    @Test
    void testGetCurrentPublicKey() {
        // Test that we can retrieve the public key
        PublicKey publicKey = ztatTokenService.getCurrentPublicKey();
        
        assertNotNull(publicKey);
        assertEquals("RSA", publicKey.getAlgorithm());
    }
}
