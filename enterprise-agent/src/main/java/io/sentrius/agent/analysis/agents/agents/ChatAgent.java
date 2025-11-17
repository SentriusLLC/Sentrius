package io.sentrius.agent.analysis.agents.agents;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import io.sentrius.agent.analysis.agents.verbs.AgentVerbs;
import io.sentrius.agent.analysis.agents.verbs.ChatVerbs;
import io.sentrius.agent.analysis.api.AgentKeyService;
import io.sentrius.agent.analysis.api.UserCommunicationService;
import io.sentrius.agent.analysis.model.LLMResponse;
import io.sentrius.agent.config.AgentConfigOptions;
import io.sentrius.sso.core.dto.UserDTO;
import io.sentrius.sso.core.dto.agents.AgentExecution;
import io.sentrius.sso.core.dto.agents.AgentExecutionContextDTO;
import io.sentrius.sso.core.exceptions.ZtatException;
import io.sentrius.sso.core.model.security.Ztat;
import io.sentrius.sso.core.model.verbs.VerbResponse;
import io.sentrius.sso.core.services.agents.AgentClientService;
import io.sentrius.sso.core.services.agents.AgentExecutionService;
import io.sentrius.sso.core.services.agents.ZeroTrustClientService;
import io.sentrius.sso.core.services.security.KeycloakService;
import io.sentrius.sso.core.utils.JsonUtil;
import io.sentrius.sso.genai.Message;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@ConditionalOnProperty(name = "agents.ai.chat.agent.enabled", havingValue = "true", matchIfMissing = false)
public class ChatAgent extends BaseEnterpriseAgent {


    final ZeroTrustClientService zeroTrustClientService;
    final AgentClientService agentClientService;
    final VerbRegistry verbRegistry;
    final AgentExecutionService agentExecutionService;
    final UserCommunicationService userCommunicationService;
    final AgentConfigOptions agentConfigOptions;
    final AgentKeyService agentKeyService;
    private final KeycloakService keycloakService;
    final ChatVerbs chatVerbs;

    private volatile boolean running = true;
    private volatile boolean paused = false;
    private final Object pauseLock = new Object();
    private Thread workerThread;

    private AgentExecution agentExecution;


    @Autowired
    public ChatAgent(
        AgentVerbs agentVerbs, ZeroTrustClientService zeroTrustClientService, AgentClientService agentClientService,
        VerbRegistry verbRegistry, AgentExecutionService agentExecutionService, UserCommunicationService userCommunicationService,
        AgentConfigOptions agentConfigOptions, AgentKeyService agentKeyService, KeycloakService keycloakService,
        ChatVerbs chatVerbs
    ) {
        super(agentVerbs, zeroTrustClientService, agentClientService, verbRegistry);
        this.zeroTrustClientService = zeroTrustClientService;
        this.agentClientService = agentClientService;
        this.verbRegistry = verbRegistry;
        this.agentExecutionService = agentExecutionService;
        this.userCommunicationService = userCommunicationService;
        this.agentConfigOptions = agentConfigOptions;
        this.agentKeyService = agentKeyService;
        this.keycloakService = keycloakService;
        this.chatVerbs = chatVerbs;
    }

    @Override
    public void onApplicationEvent(final ApplicationReadyEvent event) {

        verbRegistry.scanClasspath();


        var keyPair = agentKeyService.getKeyPair();

        try {
            var agentName = agentConfigOptions.getNamePrefix() + "-" + UUID.randomUUID().toString();
            var base64PublicKey = agentKeyService.getBase64PublicKey(keyPair.getPublic());
            var agentRegistrationDTO = agentClientService.bootstrap(agentConfigOptions.getClientId(), agentName,
                base64PublicKey
                , keyPair.getPublic().getAlgorithm());

            var encryptedSecret = agentRegistrationDTO.getClientSecret();
            var decryptedSecret = agentKeyService.
                decryptWithPrivateKey(encryptedSecret, keyPair.getPrivate());
            keycloakService.createKeycloakClient(agentName,
                decryptedSecret);


        } catch (ZtatException e) {
            throw new RuntimeException(e);
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }


        final UserDTO user = UserDTO.builder()
            .username(zeroTrustClientService.getUsername())
            .build();
        agentExecution = agentExecutionService.getAgentExecution(user);

        try {
            agentClientService.heartbeat(agentExecution, agentExecution.getUser().getUsername());
        } catch (ZtatException e) {
            throw new RuntimeException(e);
        }

        while(running) {

            try {
                var register = zeroTrustClientService.registerAgent(agentExecution);
                log.info("Registered agent response: {}", register);

                var ztat = JsonUtil.MAPPER.readValue(register, Ztat.class);
                agentExecution.setZtatToken(ztat.getZtatToken());
                agentExecution.setCommunicationId(ztat.getCommunicationId());
                break;
            }catch (Exception | ZtatException e) {

                log.error(e.getMessage());
                log.info("Registering v1.0.2 agent failed. Retrying in 10 seconds...");
                try {
                    Thread.sleep(10_000);
                } catch (InterruptedException ex) {
                    throw new RuntimeException(ex);
                }
            }
        }

        try {
            verbRegistry.scanEndpoints(agentExecution);
        } catch (ZtatException e) {
            throw new RuntimeException(e);
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }

        int allowedFailures = 20;
        log.info("Agent Registered...");
        AgentExecutionContextDTO agentExecutionContext = AgentExecutionContextDTO.builder().build();
        agentExecutionService.setExecutionContextDTO(agentExecution, agentExecutionContext);
        LLMResponse response = null;
        AgentConfig config = null;
        try {
            config = chatVerbs.getAgentConfig(agentExecution);
            var context = chatVerbs.getAgentContext(agentExecution);
            agentExecutionContext.setAgentContext(context);
        } catch (IOException e) {
            throw new RuntimeException(e);
        } catch (ZtatException e) {
            throw new RuntimeException(e);
        }
        PromptBuilder promptBuilder = new PromptBuilder(verbRegistry, config);
        var prompt = promptBuilder.buildPrompt(false);
        try {
            if (null != agentConfigOptions.getType() && agentConfigOptions.getType().equalsIgnoreCase("chat" +
                "-autonomous")) {

                response = chatVerbs.promptAgent(agentExecution, agentExecutionContext, prompt);
            }
        } catch (ZtatException e) {
            throw new RuntimeException(e);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }


        if (null != agentConfigOptions.getType() &&  agentConfigOptions.getType().equalsIgnoreCase("chat-autonomous") && response == null) {
            log.error("Chat autonomous agent mode enabled but no response received from promptAgent, shutting down...");
            throw new RuntimeException("Chat autonomous agent mode enabled but no response received from promptAgent");
        }
        VerbResponse lastVerbResponse = null;
        LLMResponse nextResponse = null;
        List<VerbResponse> verbResponses = new ArrayList<>();
        while(running) {

                // Check if agent is paused if autonomous mode
                if (null != agentConfigOptions.getType() &&  agentConfigOptions.getType().equalsIgnoreCase("chat" +
                    "-autonomous")) {
                    synchronized (pauseLock) {
                        while (paused) {
                            try {
                                log.info("Agent paused, waiting for resume command...");
                                pauseLock.wait();
                                log.info("Agent resumed from pause.");
                            } catch (InterruptedException e) {
                                Thread.currentThread().interrupt();
                                log.warn("Agent interrupted while paused");
                                break;
                            }
                        }
                    }
                }

                try {

                    Thread.sleep(5_000);
                    agentClientService.heartbeat(agentExecution, agentExecution.getUser().getUsername());
                    if (null != agentConfigOptions.getType() &&  agentConfigOptions.getType().equalsIgnoreCase("chat" +
                        "-autonomous")) {
                        log.info("Chat autonomous agent mode enabled, executing workload...");
                        VerbResponse priorResponse = null;
                        Map<String, Object> args = new HashMap<>();

                        var arguments = response.getArguments();
                        if (null != response) {
                            // Handle memory lookup if specified
                            if (response.getMemoryLookup() != null && !response.getMemoryLookup().isEmpty()) {
                                log.info("Memory lookup requested: {}", response.getMemoryLookup());
                                try {
                                    // Set up memory lookup arguments
                                    Map<String, Object> memoryArgs = new HashMap<>();
                                    memoryArgs.put("query", response.getMemoryLookup());
                                    
                                    // Execute memory lookup
                                    var memoryResponse = verbRegistry.execute(
                                        agentExecution,
                                        agentExecutionContext,
                                        lastVerbResponse,
                                        "lookupAgentMemory",
                                        memoryArgs
                                    );
                                    
                                    // Add memory results to context for LLM
                                    if (memoryResponse != null && memoryResponse.getReturnName() != null) {
                                        var memoryResult = agentExecutionContext.getAgentShortTermMemory()
                                            .get(memoryResponse.getReturnName());
                                        if (memoryResult != null) {
                                            agentExecutionContext.addMessages(
                                                Message.builder()
                                                    .role("system")
                                                    .content("Memory lookup results: " + memoryResult.toString())
                                                    .build()
                                            );
                                            log.info("Memory lookup completed, results added to context");
                                        }
                                    }
                                } catch (Exception e) {
                                    log.warn("Memory lookup failed: {}", e.getMessage());
                                    agentExecutionContext.addMessages(
                                        Message.builder()
                                            .role("system")
                                            .content("Memory lookup failed: " + e.getMessage())
                                            .build()
                                    );
                                }
                            }
                            
                            if (response.getNextOperation() != null && !response.getNextOperation().isEmpty()) {
                                var executionResponse = verbRegistry.execute(
                                    agentExecution,
                                    agentExecutionContext,
                                    lastVerbResponse,
                                    response.getNextOperation(), arguments
                                );
                                verbResponses.add(executionResponse);
                                lastVerbResponse = executionResponse;

                                var responses = agentExecutionContext.getAgentDataList();
                                var planResponse =
                                    responses.isEmpty() ? "" :
                                        responses.get(responses.size() - 1).toString();
                                if (planResponse.isEmpty()) {
                                    var respName =
                                        agentExecutionContext.getAgentShortTermMemory().get( executionResponse.getReturnName() );
                                    if (respName != null) {
                                        planResponse = respName.toString();
                                    }
                                }
                                log.info("Plan response: {} from {}", planResponse, responses);
                                nextResponse = chatVerbs.interpret_plan_response(
                                    agentExecution,
                                    agentExecutionContext,
                                    verbRegistry.getVerbs().get(response.getNextOperation()),
                                    planResponse
                                );
                                agentExecutionContext.addToPersistentMemory(
                                    "agent_response_" + System.currentTimeMillis(),
                                    nextResponse.getResponseForUser(),
                                    "PRIVATE",
                                    new String[]{"CONVERSATION"}
                                );


                                var memory = agentExecutionContext.flushPersistentMemory();
                                if (memory != null && !memory.isEmpty()) {
                                    for(var memoryEntry : memory.entrySet()){
                                        JsonNode memoryMeta = memoryEntry.getValue();
                                        
                                        // Extract metadata from the memory node
                                        String classification = memoryMeta.has("classification") ? 
                                            memoryMeta.get("classification").asText() : "PRIVATE";
                                        String markings = memoryMeta.has("markings") ? 
                                            memoryMeta.get("markings").asText() : null;
                                        JsonNode value = memoryMeta.has("value") ? 
                                            memoryMeta.get("value") : memoryMeta;
                                        
                                        // Add userId to markings for privacy scoping
                                        String userId = agentExecution.getUser().getUserId();
                                        String enhancedMarkings = markings != null 
                                            ? markings + ",USER:" + userId 
                                            : "USER:" + userId;
                                        
                                        agentClientService.storeMemory(agentExecution,
                                            agentExecutionContext.getAgentContext().getName(),
                                            io.sentrius.sso.core.dto.agents.AgentMemoryDTO.builder()
                                                .agentName(agentExecutionContext.getAgentContext().getName())
                                                .memoryKey(memoryEntry.getKey())
                                                .memoryValue(value.toString())
                                                .classification(classification)
                                                .markings(enhancedMarkings.split(","))
                                                .conversationId(agentExecution.getCommunicationId())
                                                .build());
                                        log.info("Stored memory: {} with classification: {} and markings: {}", 
                                            memoryEntry.getKey(), classification, enhancedMarkings);
                                    }
                                } else {
                                    log.info("No persistent memory to store at this time.");
                                }


                                response = nextResponse;
                            }

                        }else {
                            response = chatVerbs.promptAgent(agentExecution, agentExecutionContext, prompt);

                        }

                        continue;
                    }
                    allowedFailures = 20; // Reset allowed failures on successful heartbeat
                } catch (ZtatException | Exception ex) {
                    // Build a more informative error message for the LLM
                    StringBuilder errorMsg = new StringBuilder();
                    errorMsg.append("Error executing operation");
                    
                    if (response != null && response.getNextOperation() != null) {
                        errorMsg.append(" '").append(response.getNextOperation()).append("'");
                        
                        // Add verb signature if available
                        var verb = verbRegistry.getVerbs().get(response.getNextOperation());
                        if (verb != null) {
                            errorMsg.append(".\n\nExpected format for this operation:\n");
                            errorMsg.append("- Operation name: ").append(verb.getName()).append("\n");
                            if (verb.getArgName() != null && !verb.getArgName().isEmpty()) {
                                errorMsg.append("- Argument name: ").append(verb.getArgName()).append("\n");
                            }
                            if (verb.getExampleJson() != null && !verb.getExampleJson().isEmpty()) {
                                errorMsg.append("- Example format: ").append(verb.getExampleJson()).append("\n");
                            }
                            errorMsg.append("\nYour arguments were: ").append(
                                response.getArguments() != null ? response.getArguments().toString() : "null"
                            );
                        }
                    }
                    
                    errorMsg.append("\n\nError details: ").append(ex.getMessage());
                    errorMsg.append("\n\nPlease adjust your arguments to match the expected format and try again OR " +
                        "try a different verb if you don't have the correct arguments at all." +
                        ".");
                    
                    agentExecutionContext.addMessages(Message.builder().role("system").content(
                        errorMsg.toString()
                    ).build());

                    ex.printStackTrace();
                    if (allowedFailures-- <= 0) {
                        log.error("Failed to heartbeat agent after multiple attempts, shutting down...");
                        throw new RuntimeException(ex);
                    } else {
                        log.warn("Heartbeat failed, retrying... Remaining attempts: {}", allowedFailures);
                    }

                }

        }

    }

    /**
     * Pause the agent's autonomous operations.
     * Preserves the current state including execution context and ztats.
     */
    public void pauseAgent() {
        synchronized (pauseLock) {
            if (!paused) {
                paused = true;
                log.info("Agent paused - state preserved");
                
                // Submit provenance event for pause
                try {
                    agentClientService.submitProvenance(
                        agentExecution,
                        io.sentrius.sso.provenance.ProvenanceEvent.builder()
                            .eventType(io.sentrius.sso.provenance.ProvenanceEvent.EventType.AGENT_PAUSED)
                            .actor(agentExecution.getUser().getUsername())
                            .triggeringUser(agentExecution.getUser().getUsername())
                            .outputSummary("Agent autonomous operations paused by user")
                            .build()
                    );
                } catch (Exception e) {
                    log.error("Failed to submit pause provenance event", e);
                }
            }
        }
    }

    /**
     * Resume the agent's autonomous operations.
     * Continues from the previously saved state.
     */
    public void resumeAgent() {
        synchronized (pauseLock) {
            if (paused) {
                paused = false;
                pauseLock.notifyAll();
                log.info("Agent resumed - continuing operations");
                
                // Submit provenance event for resume
                try {
                    agentClientService.submitProvenance(
                        agentExecution,
                        io.sentrius.sso.provenance.ProvenanceEvent.builder()
                            .eventType(io.sentrius.sso.provenance.ProvenanceEvent.EventType.AGENT_RESUMED)
                            .actor(agentExecution.getUser().getUsername())
                            .triggeringUser(agentExecution.getUser().getUsername())
                            .outputSummary("Agent autonomous operations resumed by user")
                            .build()
                    );
                } catch (Exception e) {
                    log.error("Failed to submit resume provenance event", e);
                }
            }
        }
    }

    /**
     * Check if the agent is currently paused.
     */
    public boolean isPaused() {
        return paused;
    }

    /**
     * Execute a context modification if the agent is paused.
     * This method ensures thread-safe modification of agent context.
     * 
     * @param modifier The runnable that performs the context modification
     * @return true if the modification was performed, false if agent is not paused
     */
    public boolean modifyContextIfPaused(Runnable modifier) {
        synchronized (pauseLock) {
            if (paused) {
                modifier.run();
                return true;
            }
            return false;
        }
    }

    /**
     * Get the current agent execution context.
     * This includes all state, messages, and execution data.
     */
    public AgentExecution getAgentExecution() {
        return agentExecution;
    }

    /**
     * Set the agent execution context.
     * Public method primarily for testing purposes.
     * Should not be used in production code.
     */
    public void setAgentExecution(AgentExecution agentExecution) {
        this.agentExecution = agentExecution;
    }

    @PreDestroy
    public void shutdown() {
        log.info("Shutting down ChatAgent...");
        running = false;
        
        // Wake up any paused threads
        synchronized (pauseLock) {
            paused = false;
            pauseLock.notifyAll();
        }
        
        if (workerThread != null) {
            workerThread.interrupt();
        }
    }
}
