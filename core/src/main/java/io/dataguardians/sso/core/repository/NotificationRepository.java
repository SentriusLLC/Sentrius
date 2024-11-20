package io.dataguardians.sso.core.repository;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import io.dataguardians.sso.core.model.Notification;
import io.dataguardians.sso.core.model.users.User;
import org.springframework.stereotype.Repository;

@Repository
public interface NotificationRepository extends JpaRepository<Notification, Long> {
    List<Notification> findByRecipientsContains(User recipient);

    @Modifying
    @Query("UPDATE NotificationRecipient nr SET nr.acted = :acted WHERE nr.id.notificationId = :notificationId AND nr" +
        ".id.userId = " +
        ":userId")
    void updateRecipientActedStatus(@Param("notificationId") Long notificationId, @Param("userId") Long userId, @Param("acted") boolean acted);
}