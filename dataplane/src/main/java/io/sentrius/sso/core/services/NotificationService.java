package io.sentrius.sso.core.services;

import java.util.List;
import io.sentrius.sso.core.data.NotificationType;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import io.sentrius.sso.core.model.Notification;
import io.sentrius.sso.core.model.users.User;
import io.sentrius.sso.core.repository.NotificationRepository;
import lombok.extern.slf4j.Slf4j;

/**
 * Service class for managing notifications.
 * Provides methods to send, retrieve, and manage notifications for users.
 */
@Slf4j
@Service
public class NotificationService {

    @Autowired
    private NotificationRepository notificationRepository;

    /**
     * Retrieves all notifications for a specific recipient.
     *
     * @param recipient The user for whom notifications are retrieved.
     * @return A list of notifications for the recipient.
     */
    @Transactional(readOnly = true)
    public List<Notification> getNotificationsByRecipient(User recipient) {
        return notificationRepository.findByRecipientsContains(recipient);
    }

    /**
     * Sends a notification by saving it to the repository.
     *
     * @param notification The notification to be sent.
     */
    @Transactional
    public void sendNotification(Notification notification) {
        try {
            notificationRepository.save(notification);
            log.info("Notification sent: {}", notification);
        } catch (Exception e) {
            log.error("Error while sending a notification", e);
        }
    }

    /**
     * Sends a notification with a message to a specific recipient.
     *
     * @param message   The message content of the notification.
     * @param recipient The recipient of the notification.
     */
    @Transactional
    public void sendNotification(String message, User recipient) {
        Notification notification =
                Notification.builder().message(message).notificationType(NotificationType.JIT_NOTIFICATION.getValue()).recipients(List.of(recipient)).build();
        sendNotification(notification);
    }

    /**
     * Sends a notification with a message, recipient, and initiator.
     *
     * @param message   The message content of the notification.
     * @param recipient The recipient of the notification.
     * @param initiator The user who initiated the notification.
     */
    @Transactional
    public void sendNotification(String message, User recipient, User initiator) {
        Notification notification = Notification.builder().message(message).initiator(initiator).recipients(List.of(recipient)).build();
        sendNotification(notification);
    }

    /**
     * Sends a notification with a message, type, reference, and multiple recipients.
     *
     * @param message     The message content of the notification.
     * @param type        The type of the notification.
     * @param reference   A reference identifier for the notification.
     * @param recipients  A list of recipients for the notification.
     */
    @Transactional
    public void sendNotification(String message, NotificationType type, String reference, List<User> recipients) {
        try {
            Notification notification = Notification.builder()
                    .notificationReference(reference)
                    .notificationType(type.getValue())
                    .message(message)
                    .initiator(User.builder().id(-1L).build())
                    .recipients(recipients)
                    .build();
            sendNotification(notification);
        } catch (Exception e) {
            log.error("Error while sending a notification", e);
        }
    }

    /**
     * Sends a notification with a message, multiple recipients, and an initiator.
     *
     * @param message     The message content of the notification.
     * @param recipients  A list of recipients for the notification.
     * @param initiator   The user who initiated the notification.
     */
    @Transactional
    public void sendNotification(String message, List<User> recipients, User initiator) {
        Notification notification = Notification.builder().message(message).initiator(initiator).recipients(recipients).build();
        sendNotification(notification);
    }

    /**
     * Marks a notification as acted upon for a specific user.
     *
     * @param operatingUser The user performing the action.
     * @param notificationId The ID of the notification to be marked as acted upon.
     * @throws RuntimeException If the notification is not found or the user is not a recipient.
     */
    @Transactional
    public void setNotificationActedUpon(User operatingUser, Long notificationId) {
        Notification notification = notificationRepository.findById(notificationId).orElseThrow(() -> new RuntimeException("Notification not found"));
        if (notification.getRecipients().contains(operatingUser)) {
            notificationRepository.updateRecipientActedStatus(notificationId, operatingUser.getId(), true);
        } else {
            throw new RuntimeException("User is not a recipient of the notification");
        }
    }

    /**
     * Deletes a notification for a specific user.
     *
     * @param operatingUser The user performing the deletion.
     * @param notificationId The ID of the notification to be deleted.
     * @throws RuntimeException If the notification is not found or the user is not a recipient.
     */
    @Transactional
    public void deleteById(User operatingUser, Long notificationId) {
        Notification notification = notificationRepository.findById(notificationId).orElseThrow(() -> new RuntimeException("Notification not found"));
        if (notification.getRecipients().contains(operatingUser)) {
            notificationRepository.deleteById(notificationId);
        } else {
            throw new RuntimeException("User is not a recipient of the notification");
        }
    }

    /**
     * Finds all unseen notifications for a specific user.
     *
     * @param operatingUser The user for whom unseen notifications are retrieved.
     * @return A list of unseen notifications for the user.
     */
    public List<Notification> findUnseenNotifications(User operatingUser) {
        return notificationRepository.findUnseenNotifications(operatingUser.getId(), false);
    }
}