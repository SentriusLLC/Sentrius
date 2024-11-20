package io.dataguardians.sso.core.model;
import jakarta.persistence.*;

@Entity
@Table(name = "notification_recipients")
public class NotificationRecipient {

    @EmbeddedId
    private NotificationRecipientId id;

    @Column(name = "acted", nullable = false)
    private boolean acted = false;

    // Getters, setters, constructors, etc.
}