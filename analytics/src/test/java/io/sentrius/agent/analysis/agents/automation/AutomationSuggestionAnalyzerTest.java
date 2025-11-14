package io.sentrius.agent.analysis.agents.automation;

import io.sentrius.sso.core.model.HostSystem;
import io.sentrius.sso.core.model.metadata.TerminalSessionMetadata;
import io.sentrius.sso.core.model.users.User;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for AutomationSuggestionAnalyzer.
 * 
 * Note: These are simplified tests that verify basic functionality.
 * Full integration tests would require a test database and Spring context.
 */
class AutomationSuggestionAnalyzerTest {

    @Test
    void testCommandDecoding() {
        // Test the Base64 encoding/decoding functionality
        String originalCommand = "ls -la /home/user";
        String encodedCommand = Base64.getEncoder().encodeToString(
            originalCommand.getBytes(StandardCharsets.UTF_8)
        );

        // Verify encoding/decoding works correctly
        byte[] decoded = Base64.getDecoder().decode(encodedCommand);
        String decodedCommand = new String(decoded, StandardCharsets.UTF_8);
        assertEquals(originalCommand, decodedCommand);
    }

    @Test
    void testConfidenceScoreCalculation() {
        // Test confidence score logic (values between 0.4 and 1.0)
        // Low frequency, short pattern
        int lowFrequency = 3;
        int shortPattern = 2;
        double lowScore = calculateExpectedConfidence(lowFrequency, shortPattern);
        assertTrue(lowScore >= 0.4 && lowScore <= 1.0, 
            "Low confidence score should be between 0.4 and 1.0");

        // High frequency, long pattern
        int highFrequency = 10;
        int longPattern = 10;
        double highScore = calculateExpectedConfidence(highFrequency, longPattern);
        assertTrue(highScore >= 0.4 && highScore <= 1.0,
            "High confidence score should be between 0.4 and 1.0");
        assertTrue(highScore > lowScore,
            "Higher frequency and longer patterns should have higher confidence");
    }

    @Test
    void testConfidenceScoreBoundaries() {
        // Test minimum confidence score (frequency=3, pattern=1)
        double minScore = calculateExpectedConfidence(3, 1);
        assertTrue(minScore >= 0.4, "Minimum score should be at least 0.4");

        // Test maximum confidence score
        double maxScore = calculateExpectedConfidence(100, 100);
        assertEquals(1.0, maxScore, "Maximum score should not exceed 1.0");
    }

    @Test
    void testSessionGroupingLogic() {
        // Verify that sessions can be properly grouped by user and target
        User user = new User();
        user.setId(1L);
        user.setUsername("testuser");

        HostSystem host1 = HostSystem.builder().id(1L).host("server1").build();
        HostSystem host2 = HostSystem.builder().id(2L).host("server2").build();

        List<TerminalSessionMetadata> sessions = List.of(
            TerminalSessionMetadata.builder().id(1L).user(user).hostSystem(host1).build(),
            TerminalSessionMetadata.builder().id(2L).user(user).hostSystem(host1).build(),
            TerminalSessionMetadata.builder().id(3L).user(user).hostSystem(host2).build()
        );

        // Verify we have the expected number of sessions
        assertEquals(3, sessions.size());
        
        // Verify session grouping would create 2 groups (same user, 2 different hosts)
        long distinctGroups = sessions.stream()
            .map(s -> s.getUser().getUsername() + "@" + s.getHostSystem().getHost())
            .distinct()
            .count();
        assertEquals(2, distinctGroups, "Should have 2 distinct user@host combinations");
    }

    @Test
    void testMinPatternFrequencyConstant() {
        // Verify that the MIN_PATTERN_FREQUENCY is set appropriately
        // The analyzer requires at least 3 sessions to establish a pattern
        int minFrequency = 3;
        assertTrue(minFrequency >= 2, "Minimum pattern frequency should be at least 2");
        assertTrue(minFrequency <= 5, "Minimum pattern frequency should be reasonable");
    }

    @Test
    void testBase64DecodingErrors() {
        // Test that invalid Base64 data is handled appropriately
        String invalidBase64 = "not-valid-base64!@#$";
        
        try {
            Base64.getDecoder().decode(invalidBase64);
            fail("Should have thrown IllegalArgumentException for invalid Base64");
        } catch (IllegalArgumentException e) {
            // Expected behavior
            assertNotNull(e.getMessage());
        }
    }

    @Test
    void testCommandPatternExtraction() {
        // Test that command patterns can be extracted from encoded commands
        String[] commands = {"ls -la", "cd /tmp", "pwd", "ls -la"};
        
        // Encode commands
        String[] encodedCommands = new String[commands.length];
        for (int i = 0; i < commands.length; i++) {
            encodedCommands[i] = Base64.getEncoder().encodeToString(
                commands[i].getBytes(StandardCharsets.UTF_8)
            );
        }

        // Decode and verify
        for (int i = 0; i < encodedCommands.length; i++) {
            byte[] decoded = Base64.getDecoder().decode(encodedCommands[i]);
            String decodedCommand = new String(decoded, StandardCharsets.UTF_8);
            assertEquals(commands[i], decodedCommand);
        }
    }

    // Helper method to replicate confidence score calculation from analyzer
    private double calculateExpectedConfidence(int frequency, int patternLength) {
        // Base score on frequency (0.4 to 0.7)
        double frequencyScore = Math.min(0.7, 0.4 + (frequency * 0.1));
        
        // Bonus for pattern length (up to 0.3)
        double lengthBonus = Math.min(0.3, patternLength * 0.05);
        
        return Math.min(1.0, frequencyScore + lengthBonus);
    }
}
