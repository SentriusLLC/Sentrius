package io.sentrius.sso.core.repository;


import io.sentrius.sso.core.model.NotificationRecipient;
import io.sentrius.sso.core.model.NotificationRecipientId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface NotificationRecipientRepository extends JpaRepository<NotificationRecipient, NotificationRecipientId> {
    // Add custom query methods if needed
}
