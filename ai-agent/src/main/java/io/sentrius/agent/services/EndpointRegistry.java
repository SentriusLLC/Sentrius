package io.sentrius.agent.services;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import com.fasterxml.jackson.core.JsonProcessingException;
import io.sentrius.sso.core.dto.agents.AgentExecution;
import io.sentrius.sso.core.dto.agents.AgentMemoryDTO;
import io.sentrius.sso.core.dto.agents.MemoryQueryDTO;
import io.sentrius.sso.core.dto.capabilities.EndpointDescriptor;
import io.sentrius.sso.core.embeddings.EmbeddingServiceIfc;
import io.sentrius.sso.core.exceptions.ZtatException;
import io.sentrius.sso.core.services.agents.AgentClientService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@RequiredArgsConstructor
@Component
@Slf4j
public class EndpointRegistry {
    public static final String MEMORY_NAME = "all-endpoints";
    Map<String, float[]> embeddingMap = new HashMap<>();
    Map<String, EndpointDescriptor> descriptorMap = new HashMap<>();

    private final AgentClientService agentClientService;
    private final EmbeddingServiceIfc embeddingService;

    public void loadEndpoints(AgentExecution dto) throws ZtatException, JsonProcessingException {
        List<EndpointDescriptor> endpoints = agentClientService.getAvailableEndpoints(dto); // however you get them

        List<AgentMemoryDTO> requestedMemories = new ArrayList<>();
        for (EndpointDescriptor ed : endpoints) {
            String key = buildKey(ed);
            String json = EndpointDescriptor.toEmbeddableJson(ed);
            float[] embedding = null;

            MemoryQueryDTO query = MemoryQueryDTO.builder()
                    .agentId(MEMORY_NAME)
                    .memoryKey(key)
                    .searchTerm(key)
                    .build();
            List<AgentMemoryDTO> existing = agentClientService.retrieveMemories(dto, MEMORY_NAME, query);

            if (existing != null && !existing.isEmpty() && existing.get(0).isHasEmbedding()) {
                embedding = existing.get(0).getEmbedding();
                log.info("Reusing existing embedding for {}", key);
                log.info("Key={} | Embedding hash={} | First5={}",
                    key,
                    System.identityHashCode(embedding),
                    Arrays.toString(Arrays.copyOfRange(embedding, 0, 5)));
                embeddingMap.put(key, embedding);
            } else {
                //embedding = embeddingService.embed(dto, json);

                AgentMemoryDTO memory = AgentMemoryDTO.builder()
                    .memoryKey(key)
                    .memoryValue(json)
                    .memoryType("endpoint")
                    .agentId(MEMORY_NAME)
                    .classification("public")
                    .accessLevel("read")
                    .creatorUserId("system")
                    .hasEmbedding(true)
                    .build();
                requestedMemories.add(memory);
                log.info("Storing embedding memory for {}", key);
            }

            descriptorMap.put(key, ed);

        }

        if (!requestedMemories.isEmpty()) {
            List<String> texts = new ArrayList<>();
            for (AgentMemoryDTO memory : requestedMemories) {
                texts.add(memory.getMemoryValue());
            }
            List<float[]> embeddings = embeddingService.embed(dto, texts);
            for (int i = 0; i < embeddings.size(); i++) {
                float[] embedding = embeddings.get(i);
                AgentMemoryDTO memory = requestedMemories.get(i);
                memory.setEmbedding(embedding);
                memory.setHasEmbedding(true);
                embeddingMap.put(memory.getMemoryKey(), embedding);
            }
            agentClientService.storeMemories(dto, MEMORY_NAME, requestedMemories);
        }
    }

    public List<EndpointDescriptor> getAll() {
        return new ArrayList<>(descriptorMap.values());
    }

    public Optional<EndpointDescriptor> getDescriptor(String key) {
        return Optional.ofNullable(descriptorMap.get(key));
    }

    public Optional<float[]> getEmbedding(String key) {
        return Optional.ofNullable(embeddingMap.get(key));
    }

    public Optional<float[]> getEmbedding(EndpointDescriptor ed) {
        return Optional.ofNullable(embeddingMap.get(buildKey(ed)));
    }

    private String buildKey(EndpointDescriptor ed) {
        return ed.getHttpMethod() + "@" + ed.getPath();
    }

    public List<EndpointDescriptor> getAllEndpoints() {
        return new ArrayList<>(descriptorMap.values());
    }
}
