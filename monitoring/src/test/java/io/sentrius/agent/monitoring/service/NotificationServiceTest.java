package io.sentrius.agent.monitoring.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for NotificationService
 */
class NotificationServiceTest {
    
    private NotificationService notificationService;
    
    @BeforeEach
    void setUp() {
        notificationService = new NotificationService();
    }
    
    @Test
    void testSendNotification_Internal() {
        // Given
        String title = "Test Notification";
        String message = "This is a test message";
        String severity = "INFO";
        List<String> channels = List.of("INTERNAL");
        
        // When
        notificationService.sendNotification(title, message, severity, channels);
        
        // Then
        var notifications = notificationService.getRecentNotifications();
        assertFalse(notifications.isEmpty(), "Should have at least one notification");
        
        var notification = notifications.values().stream()
            .filter(n -> n.title().equals(title))
            .findFirst();
        
        assertTrue(notification.isPresent(), "Should find the sent notification");
        assertEquals(message, notification.get().message());
        assertEquals(severity, notification.get().severity());
    }
    
    @Test
    void testSendNotification_MultipleChannels() {
        // Given
        String title = "Multi-Channel Test";
        String message = "Testing multiple channels";
        String severity = "WARNING";
        List<String> channels = List.of("INTERNAL", "SLACK", "EMAIL");
        
        // When
        notificationService.sendNotification(title, message, severity, channels);
        
        // Then - Should be logged but not fail
        var notifications = notificationService.getRecentNotifications();
        assertFalse(notifications.isEmpty());
    }
    
    @Test
    void testCleanupOldNotifications() {
        // Given - Send more than 1000 notifications
        for (int i = 0; i < 1100; i++) {
            notificationService.sendNotification(
                "Notification " + i,
                "Message " + i,
                "INFO",
                List.of("INTERNAL")
            );
        }
        
        // When
        notificationService.cleanupOldNotifications();
        
        // Then
        var notifications = notificationService.getRecentNotifications();
        assertTrue(notifications.size() <= 1000, 
                   "Should keep at most 1000 notifications, got: " + notifications.size());
    }
    
    @Test
    void testSendNotification_DefaultToInternal() {
        // Given
        String title = "Default Channel Test";
        String message = "Should use INTERNAL by default";
        String severity = "ERROR";
        
        // When - Pass null channels
        notificationService.sendNotification(title, message, severity, null);
        
        // Then
        var notifications = notificationService.getRecentNotifications();
        assertFalse(notifications.isEmpty());
        
        var notification = notifications.values().stream()
            .filter(n -> n.title().equals(title))
            .findFirst();
        
        assertTrue(notification.isPresent());
    }
}
