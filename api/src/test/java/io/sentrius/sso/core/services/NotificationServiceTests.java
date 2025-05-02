package io.sentrius.sso.core.services;

import io.sentrius.sso.core.model.Notification;
import io.sentrius.sso.core.model.users.User;
import io.sentrius.sso.core.repository.NotificationRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class NotificationServiceTests
{

    @Mock
    private NotificationRepository notificationRepository;

    @InjectMocks
    private NotificationService notificationService;

    @Mock
    private User recipient;

    @Mock
    private User initiator;

    @Mock
    private Notification notification;

    @Test
    void getNotificationsByRecipientReturnsNotificationsForValidRecipient() {
        when(notificationRepository.findByRecipientsContains(recipient)).thenReturn(List.of(notification));

        List<Notification> result = notificationService.getNotificationsByRecipient(recipient);

        assertNotNull(result);
        assertEquals(1, result.size());
        assertEquals(notification, result.get(0));
    }

    @Test
    void sendNotificationSavesNotificationSuccessfully() {


        notificationService.sendNotification(notification);

        verify(notificationRepository, times(1)).save(notification);
    }

    @Test
    void sendNotificationWithMessageAndRecipientCreatesAndSavesNotification() {
        //when(recipient.getId()).thenReturn(1L);

        notificationService.sendNotification("Test message", recipient);

        verify(notificationRepository, times(1)).save(any(Notification.class));
    }

    @Test
    void setNotificationActedUponUpdatesActedStatusForValidRecipient() {
        when(notificationRepository.findById(1L)).thenReturn(Optional.of(notification));
        when(notification.getRecipients()).thenReturn(List.of(recipient));
        when(recipient.getId()).thenReturn(1L);

        notificationService.setNotificationActedUpon(recipient, 1L);

        verify(notificationRepository, times(1)).updateRecipientActedStatus(1L, 1L, true);
    }

    @Test
    void setNotificationActedUponThrowsExceptionForInvalidRecipient() {
        when(notificationRepository.findById(1L)).thenReturn(Optional.of(notification));
        when(notification.getRecipients()).thenReturn(List.of());

        assertThrows(RuntimeException.class, () -> notificationService.setNotificationActedUpon(recipient, 1L));
    }

    @Test
    void deleteByIdDeletesNotificationForValidRecipient() {
        when(notificationRepository.findById(1L)).thenReturn(Optional.of(notification));
        when(notification.getRecipients()).thenReturn(List.of(recipient));

        notificationService.deleteById(recipient, 1L);

        verify(notificationRepository, times(1)).deleteById(1L);
    }

    @Test
    void deleteByIdThrowsExceptionForInvalidRecipient() {
        when(notificationRepository.findById(1L)).thenReturn(Optional.of(notification));
        when(notification.getRecipients()).thenReturn(List.of());

        assertThrows(RuntimeException.class, () -> notificationService.deleteById(recipient, 1L));
    }

    @Test
    void findUnseenNotificationsReturnsUnseenNotifications() {
        when(notificationRepository.findUnseenNotifications(1L, false)).thenReturn(List.of(notification));
        when(recipient.getId()).thenReturn(1L);

        List<Notification> result = notificationService.findUnseenNotifications(recipient);

        assertNotNull(result);
        assertEquals(1, result.size());
        assertEquals(notification, result.get(0));
    }
}
