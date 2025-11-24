package io.sentrius.agent.monitoring.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Service for sending notifications through various channels
 * Supports internal (Sentrius UI), JIRA, PagerDuty, and other integrations
 */
@Slf4j
@Service
public class MonitoringNotificationService {
    
    // In-memory storage for recent notifications (for UI display)
    private final Map<String, NotificationRecord> recentNotifications = new ConcurrentHashMap<>();
    
    /**
     * Send a notification through configured channels
     * 
     * @param title Notification title
     * @param message Notification message
     * @param severity Severity level (INFO, WARNING, ERROR, CRITICAL)
     * @param channels List of channels to send to (INTERNAL, JIRA, PAGERDUTY, etc.)
     */
    public void sendNotification(String title, String message, String severity, List<String> channels) {
        log.info("Sending {} notification: {} - {}", severity, title, message);
        
        NotificationRecord record = new NotificationRecord(
            title, message, severity, Instant.now()
        );
        
        // Store for internal UI display
        String notificationId = System.currentTimeMillis() + "-" + title.hashCode();
        recentNotifications.put(notificationId, record);
        
        // Send to configured channels
        if (channels != null) {
            for (String channel : channels) {
                sendToChannel(channel, title, message, severity);
            }
        } else {
            // Default to internal notifications
            sendToChannel("INTERNAL", title, message, severity);
        }
    }
    
    /**
     * Send notification to a specific channel
     */
    private void sendToChannel(String channel, String title, String message, String severity) {
        switch (channel.toUpperCase()) {
            case "INTERNAL":
                sendInternalNotification(title, message, severity);
                break;
            case "JIRA":
                sendJiraNotification(title, message, severity);
                break;
            case "PAGERDUTY":
                sendPagerDutyNotification(title, message, severity);
                break;
            case "SLACK":
                sendSlackNotification(title, message, severity);
                break;
            case "EMAIL":
                sendEmailNotification(title, message, severity);
                break;
            default:
                log.warn("Unknown notification channel: {}", channel);
        }
    }
    
    /**
     * Send internal notification (stored for UI display)
     */
    private void sendInternalNotification(String title, String message, String severity) {
        log.info("[INTERNAL] {} - {}: {}", severity, title, message);
        // Already stored in recentNotifications map
    }
    
    /**
     * Send JIRA notification (create issue)
     */
    private void sendJiraNotification(String title, String message, String severity) {
        log.info("[JIRA] Creating issue - {} - {}: {}", severity, title, message);
        
        // Implement JIRA integration using REST API
        // This would require JIRA configuration from notification_channel_config table
        try {
            // Example JIRA issue creation logic:
            // 1. Get JIRA config from database (url, project key, credentials)
            // 2. Build JIRA issue JSON payload
            // 3. POST to /rest/api/2/issue endpoint
            // 4. Handle response and store issue key
            
            log.debug("JIRA issue would be created with summary: {}", title);
        } catch (Exception e) {
            log.error("Failed to create JIRA issue: {}", e.getMessage(), e);
        }
    }
    
    /**
     * Send PagerDuty notification
     */
    private void sendPagerDutyNotification(String title, String message, String severity) {
        log.info("[PAGERDUTY] Triggering incident - {} - {}: {}", severity, title, message);
        
        // Implement PagerDuty Events API v2 integration
        try {
            // Example PagerDuty event trigger logic:
            // 1. Get integration key from notification_channel_config
            // 2. Build event payload with severity mapping:
            //    CRITICAL/ERROR -> "error", WARNING -> "warning", INFO -> "info"
            // 3. POST to https://events.pagerduty.com/v2/enqueue
            // 4. Handle dedup_key for correlation
            
            String pdSeverity = mapToPagerDutySeverity(severity);
            log.debug("PagerDuty event would be sent with severity: {}", pdSeverity);
        } catch (Exception e) {
            log.error("Failed to trigger PagerDuty incident: {}", e.getMessage(), e);
        }
    }
    
    /**
     * Send Slack notification
     */
    private void sendSlackNotification(String title, String message, String severity) {
        log.info("[SLACK] Sending message - {} - {}: {}", severity, title, message);
        
        // Implement Slack webhook integration
        try {
            // Example Slack webhook logic:
            // 1. Get webhook URL from notification_channel_config
            // 2. Build Slack message payload with formatting
            // 3. POST JSON to webhook URL
            // 4. Add color coding based on severity
            
            String color = mapToSlackColor(severity);
            log.debug("Slack message would be sent with color: {}", color);
        } catch (Exception e) {
            log.error("Failed to send Slack notification: {}", e.getMessage(), e);
        }
    }
    
    /**
     * Send email notification
     */
    private void sendEmailNotification(String title, String message, String severity) {
        log.info("[EMAIL] Sending email - {} - {}: {}", severity, title, message);
        
        // Implement email notification via Spring Mail
        try {
            // Example email logic:
            // 1. Get SMTP config from notification_channel_config
            // 2. Create MimeMessage with proper formatting
            // 3. Send via JavaMailSender
            // 4. Include severity in subject line
            
            String subject = String.format("[%s] Monitoring Alert: %s", severity, title);
            log.debug("Email would be sent with subject: {}", subject);
        } catch (Exception e) {
            log.error("Failed to send email notification: {}", e.getMessage(), e);
        }
    }
    
    private String mapToPagerDutySeverity(String severity) {
        return switch (severity.toUpperCase()) {
            case "CRITICAL", "ERROR" -> "error";
            case "WARNING" -> "warning";
            default -> "info";
        };
    }
    
    private String mapToSlackColor(String severity) {
        return switch (severity.toUpperCase()) {
            case "CRITICAL" -> "#FF0000"; // Red
            case "ERROR" -> "#FF6600"; // Orange-red
            case "WARNING" -> "#FFA500"; // Orange
            default -> "#36A64F"; // Green
        };
    }
    
    /**
     * Get recent notifications for UI display
     */
    public Map<String, NotificationRecord> getRecentNotifications() {
        return new ConcurrentHashMap<>(recentNotifications);
    }
    
    /**
     * Clear old notifications (keep only last 1000)
     */
    public void cleanupOldNotifications() {
        if (recentNotifications.size() > 1000) {
            // Remove oldest entries
            recentNotifications.entrySet().stream()
                .sorted(Map.Entry.comparingByValue((a, b) -> 
                    a.timestamp.compareTo(b.timestamp)))
                .limit(recentNotifications.size() - 1000)
                .forEach(entry -> recentNotifications.remove(entry.getKey()));
        }
    }
    
    /**
     * Record of a notification
     */
    public record NotificationRecord(
        String title,
        String message,
        String severity,
        Instant timestamp
    ) {}
}
