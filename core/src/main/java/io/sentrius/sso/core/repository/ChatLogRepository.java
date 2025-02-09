package io.sentrius.sso.core.repository;

import io.sentrius.sso.core.model.chat.ChatLog;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface ChatLogRepository extends JpaRepository<ChatLog, Long> {
    List<ChatLog> findBySessionIdAndChatGroupId(Long sessionId, String chatGroupId);
}