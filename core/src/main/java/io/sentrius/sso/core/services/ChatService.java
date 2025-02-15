package io.sentrius.sso.core.services;

import java.util.List;
import io.sentrius.sso.core.model.chat.ChatLog;
import io.sentrius.sso.core.repository.ChatLogRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ChatService {

    private final ChatLogRepository chatLogRepository;

    @Transactional
    public void save(ChatLog chatLog) {
        chatLogRepository.save(chatLog);
    }

    @Transactional
    public List<ChatLog> findBySessionIdAndChatGroupId(Long sessionId) {
        return chatLogRepository.findBySessionId(sessionId);
    }


}
