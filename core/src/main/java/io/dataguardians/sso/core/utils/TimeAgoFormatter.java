package io.dataguardians.sso.core.utils;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.Duration;

public class TimeAgoFormatter {
    public static String formatTimestampToMinutesAgo(Timestamp timestamp) {
        Instant now = Instant.now();
        Instant timestampInstant = timestamp.toInstant();

        Duration duration = Duration.between(timestampInstant, now);
        long minutesAgo = duration.toMinutes();

        if (minutesAgo < 1) {
            return "just now";
        } else if (minutesAgo == 1) {
            return "1 minute ago";
        } else {
            return minutesAgo + " minutes ago";
        }
    }

    public static void main(String[] args) {
        // Example usage
        Timestamp exampleTimestamp = Timestamp.from(Instant.now().minusSeconds(120)); // 2 minutes ago
        System.out.println(formatTimestampToMinutesAgo(exampleTimestamp));
    }
}
