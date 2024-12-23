package io.sentrius.sso.core.services;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import io.sentrius.sso.core.data.NotificationType;
import io.sentrius.sso.core.model.Notification;
import io.sentrius.sso.core.model.users.User;
import io.sentrius.sso.core.repository.NotificationRepository;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class NotificationService {

    @Autowired
    private NotificationRepository notificationRepository;

    @Transactional(readOnly = true)
    public List<Notification> getNotificationsByRecipient(User recipient) {
        return notificationRepository.findByRecipientsContains(recipient);
    }

    @Transactional
    public void sendNotification(Notification notification) {
        try {
            notificationRepository.save(notification);
            log.info("Notification sent: {}", notification);
        } catch (Exception e) {
            log.error("Error while sending a notification", e);
        }
    }

    @Transactional
    public void sendNotification(String message, User recipient) {
        Notification notification =
            Notification.builder().message(message).notificationType(NotificationType.JIT_NOTIFICATION.getValue()).recipients(List.of(recipient)).build();
        sendNotification(notification);
    }

    @Transactional
    public void sendNotification(String message, User recipient, User initiator) {
        Notification notification = Notification.builder().message(message).initiator(initiator).recipients(List.of(recipient)).build();
        sendNotification(notification);
    }

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

    @Transactional
    public void sendNotification(String message, List<User> recipients, User initiator) {
        Notification notification = Notification.builder().message(message).initiator(initiator).recipients(recipients).build();
        sendNotification(notification);
    }

    @Transactional
    public void setNotificationActedUpon(User operatingUser, Long notificationId) {
        Notification notification = notificationRepository.findById(notificationId).orElseThrow(() -> new RuntimeException("Notification not found"));
        if (notification.getRecipients().contains(operatingUser)) {
            notificationRepository.updateRecipientActedStatus(notificationId, operatingUser.getId(), true);
        } else {
            throw new RuntimeException("User is not a recipient of the notification");
        }
    }

    @Transactional
    public void deleteById(User operatingUser, Long notificationId) {
        Notification notification = notificationRepository.findById(notificationId).orElseThrow(() -> new RuntimeException("Notification not found"));
        if (notification.getRecipients().contains(operatingUser)) {
            notificationRepository.deleteById(notificationId);
        } else {
            throw new RuntimeException("User is not a recipient of the notification");
        }
    }

    public List<Notification> findUnseenNotifications(User operatingUser) {
        return notificationRepository.findUnseenNotifications(operatingUser.getId(), false);
    }
}