package io.sentrius.sso.core.utils;

import org.junit.jupiter.api.Test;

import java.sql.Timestamp;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

class TimeAgoFormatterTest {

    @Test
    void formatTimestampToMinutesAgoReturnsJustNowForRecentTimestamp() {
        Timestamp recentTimestamp = Timestamp.from(Instant.now().minusSeconds(30));
        String result = TimeAgoFormatter.formatTimestampToMinutesAgo(recentTimestamp);
        assertEquals("just now", result);
    }

    @Test
    void formatTimestampToMinutesAgoReturnsOneMinuteAgoForOneMinute() {
        Timestamp oneMinuteAgo = Timestamp.from(Instant.now().minusSeconds(60));
        String result = TimeAgoFormatter.formatTimestampToMinutesAgo(oneMinuteAgo);
        assertEquals("1 minute ago", result);
    }

    @Test
    void formatTimestampToMinutesAgoReturnsCorrectMinutesForMultipleMinutes() {
        Timestamp twoMinutesAgo = Timestamp.from(Instant.now().minusSeconds(120));
        String result = TimeAgoFormatter.formatTimestampToMinutesAgo(twoMinutesAgo);
        assertEquals("2 minutes ago", result);

        Timestamp fiveMinutesAgo = Timestamp.from(Instant.now().minusSeconds(300));
        result = TimeAgoFormatter.formatTimestampToMinutesAgo(fiveMinutesAgo);
        assertEquals("5 minutes ago", result);
    }

    @Test
    void formatTimestampToMinutesAgoHandlesLargeMinuteValues() {
        Timestamp oneHourAgo = Timestamp.from(Instant.now().minusSeconds(3600));
        String result = TimeAgoFormatter.formatTimestampToMinutesAgo(oneHourAgo);
        assertEquals("60 minutes ago", result);
    }

    @Test
    void formatTimestampToMinutesAgoHandlesExactBoundaries() {
        // Test exactly 59 seconds (should be "just now")
        Timestamp almostOneMinute = Timestamp.from(Instant.now().minusSeconds(59));
        String result = TimeAgoFormatter.formatTimestampToMinutesAgo(almostOneMinute);
        assertEquals("just now", result);

        // Test exactly 60 seconds (should be "1 minute ago")
        Timestamp exactlyOneMinute = Timestamp.from(Instant.now().minusSeconds(60));
        result = TimeAgoFormatter.formatTimestampToMinutesAgo(exactlyOneMinute);
        assertEquals("1 minute ago", result);

        // Test exactly 61 seconds (should be "1 minute ago")
        Timestamp justOverOneMinute = Timestamp.from(Instant.now().minusSeconds(61));
        result = TimeAgoFormatter.formatTimestampToMinutesAgo(justOverOneMinute);
        assertEquals("1 minute ago", result);

        // Test exactly 119 seconds (should be "1 minute ago")
        Timestamp almostTwoMinutes = Timestamp.from(Instant.now().minusSeconds(119));
        result = TimeAgoFormatter.formatTimestampToMinutesAgo(almostTwoMinutes);
        assertEquals("1 minute ago", result);

        // Test exactly 120 seconds (should be "2 minutes ago")
        Timestamp exactlyTwoMinutes = Timestamp.from(Instant.now().minusSeconds(120));
        result = TimeAgoFormatter.formatTimestampToMinutesAgo(exactlyTwoMinutes);
        assertEquals("2 minutes ago", result);
    }

    @Test
    void formatTimestampToMinutesAgoHandlesFutureTimestamp() {
        Timestamp futureTimestamp = Timestamp.from(Instant.now().plusSeconds(60));
        String result = TimeAgoFormatter.formatTimestampToMinutesAgo(futureTimestamp);
        assertEquals("just now", result); // Future timestamp should result in negative duration, treated as 0
    }
}