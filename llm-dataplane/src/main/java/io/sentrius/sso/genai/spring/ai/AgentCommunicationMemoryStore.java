package io.sentrius.sso.genai.spring.ai;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import com.fasterxml.jackson.core.JsonProcessingException;
import io.sentrius.sso.core.model.chat.AgentCommunication;
import io.sentrius.sso.core.services.agents.AgentService;
import io.sentrius.sso.core.utils.JsonUtil;
import io.sentrius.sso.genai.Message;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;


@Slf4j
@Service
public class AgentCommunicationMemoryStore  {

    private final AgentService service;
    private final ConcurrentHashMap<String, List<Message>> inMemoryCache = new ConcurrentHashMap<>();

    public AgentCommunicationMemoryStore(AgentService service) {
        this.service = service;
    }


    public void add(String conversationId, List<Message> messages) {
        if (inMemoryCache.containsKey(conversationId)) {
            log.info("Adding messages to existing conversationId: {}", conversationId);
            inMemoryCache.get(conversationId).addAll(messages);
        }
        else {
            log.info("Not adding messages from conversation : {}", conversationId);
        }
    }

    public List<Message> get(String conversationId, int lastN) {
        if (inMemoryCache.containsKey(conversationId)) {
            return inMemoryCache.get(conversationId);
        }

        List<AgentCommunication> comms = service.getCommunications(UUID.fromString(conversationId));
        List<Message> messages = new ArrayList<>();

        for (AgentCommunication comm : comms) {
            if (comm.getMessageType().equals("chat_request")) {
                try {
                    io.sentrius.sso.genai.Message msg = JsonUtil.MAPPER.readValue(comm.getPayload(),
                        io.sentrius.sso.genai.Message.class);
                    messages.add(msg);
                } catch (JsonProcessingException e) {
                    throw new RuntimeException(e);
                }
            }
        }

        // Store to cache for quick reuse
        inMemoryCache.put(conversationId, messages);
        return messages;
    }

    public void clear(String conversationId) {
        inMemoryCache.remove(conversationId);
    }
}
