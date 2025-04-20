package io.sentrius.sso.genai.spring.ai;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import com.fasterxml.jackson.core.JsonProcessingException;
import io.sentrius.sso.core.model.chat.AgentCommunication;
import io.sentrius.sso.core.repository.AgentCommunicationRepository;
import io.sentrius.sso.core.utils.JsonUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.openai.api.OpenAiApi;

@Slf4j
public class AgentCommunicationMemoryStore implements ChatMemory {

    private final AgentCommunicationRepository repository;
    private final ConcurrentHashMap<String, List<Message>> inMemoryCache = new ConcurrentHashMap<>();

    public AgentCommunicationMemoryStore(AgentCommunicationRepository repository) {
        this.repository = repository;
    }


    @Override
    public void add(String conversationId, List<Message> messages) {
        if (inMemoryCache.containsKey(conversationId)) {
            log.info("Adding messages to existing conversationId: {}", conversationId);
            inMemoryCache.get(conversationId).addAll(messages);
        }
        else {
            log.info("Not adding messages from conversation : {}", conversationId);
        }
    }

    @Override
    public List<Message> get(String conversationId, int lastN) {
        if (inMemoryCache.containsKey(conversationId)) {
            return inMemoryCache.get(conversationId);
        }

        List<AgentCommunication> comms = repository.findByCommunicationId(UUID.fromString(conversationId));
        List<Message> messages = new ArrayList<>();

        for (AgentCommunication comm : comms) {
            if (comm.getMessageType().equals("chat_request")) {
                try {
                    io.sentrius.sso.genai.Message msg = JsonUtil.MAPPER.readValue(comm.getPayload(),
                        io.sentrius.sso.genai.Message.class);
                    if (msg.getRole().equalsIgnoreCase("system")) {
                        messages.add(new SystemMessage(
                            comm.getPayload())
                        );
                    }
                    else if (msg.getRole().equalsIgnoreCase("user")) {
                        messages.add(new UserMessage(
                            comm.getPayload())
                        );
                    }
                    else {
                        messages.add(new AssistantMessage(
                            comm.getPayload())
                        );
                    }
                } catch (JsonProcessingException e) {
                    throw new RuntimeException(e);
                }
            }
        }

        // Store to cache for quick reuse
        inMemoryCache.put(conversationId, messages);
        return messages;
    }

    @Override
    public void clear(String conversationId) {
        inMemoryCache.remove(conversationId);
    }
}
