package io.sentrius.sso.core.repository.monitoring;

import io.sentrius.sso.core.model.monitoring.NotificationHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface NotificationHistoryRepository extends JpaRepository<NotificationHistory, Long> {
    List<NotificationHistory> findByAcknowledgedOrderBySentAtDesc(Boolean acknowledged);
    List<NotificationHistory> findAllByOrderBySentAtDesc();
}
