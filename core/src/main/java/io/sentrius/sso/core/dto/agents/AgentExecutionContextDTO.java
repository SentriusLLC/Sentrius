package io.sentrius.sso.core.dto.agents;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.sentrius.sso.core.utils.JsonUtil;
import io.sentrius.sso.genai.Message;
import lombok.*;
import lombok.extern.slf4j.Slf4j;

@Builder
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Slf4j
@ToString
@JsonIgnoreProperties(ignoreUnknown = true)
public class AgentExecutionContextDTO {

    @Builder.Default
    private List<Message> messages = new ArrayList<>();

    private AgentContextDTO agentContext;

    @Builder.Default
    private List<JsonNode> agentDataList = new ArrayList<>();

    @Builder.Default
    private Map<String, JsonNode> agentShortTermMemory = new HashMap<>();

    @Builder.Default
    private ObjectNode executionArgs = JsonUtil.MAPPER.createObjectNode();

    @Builder.Default
    private ObjectNode callParams = JsonUtil.MAPPER.createObjectNode();

    @Builder.Default

    private final Map<String,JsonNode> longTermMemories = new ConcurrentHashMap<>();

    // === Memory Management ===

    public void addToMemory(JsonNode node) {
        agentDataList.add(node);
        flatten("", node, agentShortTermMemory);
    }

    public void addToMemory(String key, JsonNode value) {
        putStructuredToMemory(key, value);
    }

    public void putStructuredToMemory(String key, JsonNode value) {
        agentShortTermMemory.put(key, value);
        // Optional: Add to agentDataList if you want to preserve all data too
        ObjectNode wrapper = JsonUtil.MAPPER.createObjectNode();
        wrapper.set(key, value);
        agentDataList.add(wrapper);

    }

    // === Persistent Memory Integration ===

    /**
     * Store value to persistent memory with markings
     */
    public void addToPersistentMemory(String key, JsonNode value, String classification, String[] markings) {
        // Add to short-term memory for immediate access
        putStructuredToMemory(key, value);
        
        // Store metadata for persistent storage
        if (agentContext != null) {
            ObjectNode memoryMeta = JsonUtil.MAPPER.createObjectNode();
            memoryMeta.put("key", key);
            memoryMeta.set("value", value);
            memoryMeta.put("classification", classification != null ? classification : "PRIVATE");
            if (markings != null) {
                memoryMeta.put("markings", String.join(",", markings));
            }
            memoryMeta.put("persistent", true);
            
            // Add to data list with persistent flag
            longTermMemories.put(key, memoryMeta);

            log.info("Added persistent memory meta for {}", key);

        } else {
            log.warn("AgentContext is null, cannot add to persistent memory");
        }
    }

    /**
     * Store simple value to persistent memory
     */
    public void addToPersistentMemory(String key, String value, String classification, String[] markings) {
        JsonNode jsonValue = JsonUtil.MAPPER.convertValue(value, JsonNode.class);
        addToPersistentMemory(key, jsonValue, classification, markings);
    }

    /**
     * Store object to persistent memory
     */
    public void addToPersistentMemory(String key, Object value, String classification, String[] markings) {
        JsonNode jsonValue = JsonUtil.MAPPER.convertValue(value, JsonNode.class);
        addToPersistentMemory(key, jsonValue, classification, markings);
    }

    /**
     * Get list of memories marked for persistent storage
     */
    public Map<String, JsonNode> getPersistentMemoryItems() {

            return longTermMemories;
    }

    /**
     * Check if context has persistent memory items
     */
    public boolean hasPersistentMemory() {
        return !getPersistentMemoryItems().isEmpty();
    }

    public Map<String, JsonNode> flushPersistentMemory() {
        // Deep copy each entry to avoid returning references to mutable JsonNodes
        Map<String, JsonNode> copy = new HashMap<>();
        for (Map.Entry<String, JsonNode> entry : getPersistentMemoryItems().entrySet()) {
            copy.put(entry.getKey(), entry.getValue().deepCopy());
        }
        longTermMemories.clear();
        return copy;

    }

    public static void flatten(String prefix, JsonNode node, Map<String, JsonNode> map) {
        if (node.isObject()) {
            node.fields().forEachRemaining(entry -> {
                String key = prefix.isEmpty() ? entry.getKey() : prefix + "." + entry.getKey();
                log.info("Flattening key: {}", key);
                flatten(key, entry.getValue(), map);
            });
        } else if (node.isArray()) {
            for (int i = 0; i < node.size(); i++) {
                flatten(prefix + "[" + i + "]", node.get(i), map);
            }
            map.put(prefix + "_length", JsonUtil.MAPPER.convertValue(node.size(), JsonNode.class));
        } else {
            map.put(prefix, node);
        }
    }

    // === Execution Argument Access ===

    public Optional<JsonNode> getExecutionArgument(String name) {
        if (executionArgs != null && executionArgs.has(name)) {
            return Optional.of(executionArgs.get(name));
        }

        if (agentShortTermMemory != null && agentShortTermMemory.containsKey(name)) {
            log.info("Getting from shortTermMemory for name: {}", name);
            return Optional.ofNullable(agentShortTermMemory.get(name));
        }
        log.info("Short term memory is {}", agentShortTermMemory);
        log.info("Execution argument '{}' not found in executionArgs or shortTermMemory", name);
        return Optional.empty();
    }

    public Optional<JsonNode> getExecutionArgument(String methodArgumentName, String name) {
        log.info("Getting execution argument for methodArgumentName: {}, name: {}", methodArgumentName, name);
        if (executionArgs != null && executionArgs.has(methodArgumentName)) {
            log.info("Found execution argument for methodArgumentName: {}", executionArgs.get(methodArgumentName));
            if (executionArgs.get(methodArgumentName).has(methodArgumentName)) {
                return Optional.of(executionArgs.get(methodArgumentName).get(methodArgumentName).get(name));
            }
            return Optional.of(executionArgs.get(methodArgumentName).get(name));
        }

        if (agentShortTermMemory != null && agentShortTermMemory.containsKey(methodArgumentName)) {

            log.info("Found execution argument for methodArgumentName: {}", agentShortTermMemory.get(methodArgumentName));
            return Optional.of(agentShortTermMemory.get(methodArgumentName).get(name));
        } else {
            log.info("Execution argument '{}' not found in executionArgs or shortTermMemory {}", methodArgumentName,
                agentShortTermMemory);
        }

        return Optional.empty();
    }

    public <T> Optional<T> getExecutionArgumentScoped(String name, Class<T> clazz) {
        try {
            return getExecutionArgument(name)
                .map(node -> JsonUtil.MAPPER.convertValue(node, clazz));
        } catch (Exception e) {
            log.error("Error while handling scoped argument for '{}'", name, e);
            return Optional.empty();
        }
    }

    public <T> Optional<T> getExecutionArgumentScoped(String name, TypeReference<T> typeRef) {
        return getExecutionArgument(name).flatMap(node -> {
            try {
                return Optional.of(JsonUtil.MAPPER.readValue(
                    JsonUtil.MAPPER.treeAsTokens(node), typeRef
                ));
            } catch (IOException e) {
                log.warn("Failed to deserialize '{}' from shortTermMemory: {}", name, e.getMessage());
                return Optional.empty();
            }
        });
    }

    // === Messages ===

    public void addMessages(List<Message> messages) {
        if (messages != null) {
            this.messages.addAll(messages);
        }
    }

    public void addMessages(Message message) {
        this.messages.add(message);
    }

    // === Label Sanitization ===

    public String getSafeLabel(String name) {
            return getExecutionArgument(name)
                .map(JsonNode::asText)
                .map(this::sanitizeLabelValue)
                .orElse("unknown");
    }

    public String getLabel(String methodArgumentName, String name) {
        var safeGet = getExecutionArgument(methodArgumentName, name);
        if (safeGet.isEmpty()) {
            return getExecutionArgument(name)
                .map(JsonNode::asText)
                .orElse("unknown");
        } else {
            return safeGet
                .map(JsonNode::asText)
                .orElse("unknown");
        }
    }

    public String getSafeLabel(String methodArgumentName, String name) {
        var safeGet = getExecutionArgument(methodArgumentName, name);
        if (safeGet.isEmpty()) {
            return getExecutionArgument(name)
                .map(JsonNode::asText)
                .map(this::sanitizeLabelValue)
                .orElse("unknown");
        } else {
            return safeGet
                .map(JsonNode::asText)
                .map(this::sanitizeLabelValue)
                .orElse("unknown");
        }
    }

    private String sanitizeLabelValue(String value) {
        return value
            .replaceAll("^\"|\"$", "")                       // strip surrounding quotes
            .replaceAll("[^A-Za-z0-9_.-]", "")               // remove invalid characters
            .replaceAll("^[^A-Za-z0-9]+|[^A-Za-z0-9]+$", ""); // trim invalid start/end
    }
}
