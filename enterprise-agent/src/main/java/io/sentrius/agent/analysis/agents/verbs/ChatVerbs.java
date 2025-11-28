package io.sentrius.agent.analysis.agents.verbs;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.ListIterator;
import java.util.Optional;
import java.util.stream.Collectors;
import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.node.ArrayNode;
import io.sentrius.agent.analysis.agents.agents.AgentConfig;
import io.sentrius.agent.analysis.agents.agents.AgentVerb;
import io.sentrius.agent.analysis.agents.agents.PromptBuilder;
import io.sentrius.agent.analysis.agents.agents.VerbRegistry;
import io.sentrius.agent.analysis.model.LLMResponse;
import io.sentrius.agent.analysis.model.WebSocky;
import io.sentrius.sso.core.dto.agents.AgentExecution;
import io.sentrius.sso.core.dto.agents.AgentExecutionContextDTO;
import io.sentrius.sso.core.exceptions.ZtatException;
import io.sentrius.sso.core.model.verbs.Verb;
import io.sentrius.sso.core.services.agents.AgentClientService;
import io.sentrius.sso.core.services.agents.AgentExecutionService;
import io.sentrius.sso.core.services.agents.LLMService;
import io.sentrius.sso.core.services.agents.ZeroTrustClientService;
import io.sentrius.sso.core.utils.JsonUtil;
import io.sentrius.sso.genai.Message;
import io.sentrius.sso.genai.Response;
import io.sentrius.sso.genai.model.LLMRequest;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
@Slf4j

public class ChatVerbs extends VerbBase{

    private final AgentExecutionService agentExecutionService;

    final ZeroTrustClientService zeroTrustClientService;
    final LLMService llmService;
    final VerbRegistry verbRegistry;
    final AgentClientService agentClientService;

    protected ChatVerbs(@Value("${agent.ai.config}") String agentConfigFile,
                        @Value("${agent.ai.context.db.id:none}") String agentDatabaseContext,
                        AgentClientService agentClientService, AgentExecutionService agentExecutionService,
                        ZeroTrustClientService zeroTrustClientService, LLMService llmService, VerbRegistry verbRegistry,
                        AgentClientService agentClientService1
    ) {
        super(agentConfigFile, agentDatabaseContext, agentClientService);
        this.agentExecutionService = agentExecutionService;
        this.zeroTrustClientService = zeroTrustClientService;
        this.llmService = llmService;
        this.verbRegistry = verbRegistry;
        this.agentClientService = agentClientService1;
    }
    
    /**
     * Ensures the LLMResponse has executed operations initialized.
     * If not present, initializes with the provided default operations.
     * 
     * @param response The LLM response to update
     * @param defaultOps The default operations to use if none are present (can be null)
     */
    private void ensureExecutedOperationsInitialized(LLMResponse response, List<String> defaultOps) {
        if (response.getExecutedOperations() == null || response.getExecutedOperations().isEmpty()) {
            response.setExecutedOperations(defaultOps != null ? new ArrayList<>(defaultOps) : new ArrayList<>());
        }
    }
    
    /**
     * Builds the strict mode execution protocol for the LLM.
     * This includes plan state awareness to prevent re-executing completed plans.
     * 
     * @param executedOperations List of operations that have been executed
     * @param currentPlanStatus The current status of the plan
     * @return The strict mode protocol string
     */
    private String buildStrictModeProtocol(List<String> executedOperations, String currentPlanStatus) {
        return buildStrictModeProtocol(executedOperations, currentPlanStatus, false);
    }
    
    /**
     * Builds the strict mode execution protocol for the LLM.
     * This includes plan state awareness to prevent re-executing completed plans.
     * 
     * @param executedOperations List of operations that have been executed
     * @param currentPlanStatus The current status of the plan
     * @param isAutonomous Whether this is an autonomous agent (not chat-driven)
     * @return The strict mode protocol string
     */
    private String buildStrictModeProtocol(List<String> executedOperations, String currentPlanStatus, boolean isAutonomous) {
        StringBuilder protocol = new StringBuilder();
        
        protocol.append("EXECUTION PROTOCOL — STRICT MODE\n\n");
        protocol.append("You are a deterministic execution planner operating inside a machine-enforced protocol.\n\n");
        
        // Plan state awareness section
        protocol.append("PLAN STATE AWARENESS:\n");
        protocol.append("- Current plan status: ").append(currentPlanStatus != null ? currentPlanStatus : "idle").append("\n");
        if (executedOperations != null && !executedOperations.isEmpty()) {
            protocol.append("- Operations already executed in this session: ").append(String.join(", ", executedOperations)).append("\n");
            protocol.append("- DO NOT re-execute operations that have already been completed.\n");
            protocol.append("- If the task has been fulfilled, set planStatus to 'completed' and leave nextOperation empty.\n");
        }
        protocol.append("\n");
        
        protocol.append("RULES:\n");
        protocol.append("- You MUST respond with EXACTLY ONE valid JSON object.\n");
        protocol.append("- You MUST NOT include conversational text, explanations, markdown, or prose.\n");
        protocol.append("- You MUST NOT ask the user any questions.\n");
        protocol.append("- You MUST NOT request clarification.\n");
        protocol.append("- You MUST select and execute the next operation using the available verbs ONLY if action is needed.\n");
        protocol.append("- If required information is missing, you MUST infer conservatively OR return an executable fallback.\n");
        protocol.append("- Returning free-form text is a protocol violation.\n\n");
        
        if (isAutonomous) {
            // Autonomous agent mode - encourage plan creation and execution
            protocol.append("AUTONOMOUS AGENT MODE:\n");
            protocol.append("- You are operating autonomously without user interaction.\n");
            protocol.append("- Analyze the available verbs and create a plan to accomplish your configured task.\n");
            protocol.append("- Execute one verb at a time, using nextOperation to specify the verb to run.\n");
            protocol.append("- After each verb execution, evaluate the results and determine the next step.\n");
            protocol.append("- Continue executing verbs until your task is complete.\n");
            protocol.append("- Set planStatus to 'in_progress' while working, 'completed' when done.\n\n");
        } else {
            // Chat-driven mode - handle conversational inputs
            protocol.append("CONVERSATIONAL INPUT HANDLING:\n");
            protocol.append("- If the user input is purely conversational (e.g., 'Thanks!', 'hello', 'great', 'ok', 'bye'),\n");
            protocol.append("  you MUST include a brief response ONLY inside the responseForUser field.\n");
            protocol.append("- For conversational inputs, set nextOperation to empty string and planStatus to 'completed' or 'idle'.\n");
            protocol.append("- DO NOT execute any verbs for simple acknowledgments or greetings.\n");
            protocol.append("- Conversational text MUST NOT trigger new operations.\n");
            protocol.append("- Always include a user response even for conversational inputs.\n\n");
        }
        
        protocol.append("MEMORY RULES:\n");
        protocol.append("- If required information is NOT in the current context, you MUST populate the memoryLookup field.\n");
        protocol.append("- memoryLookup is executed BEFORE nextOperation.\n");
        protocol.append("- Leave memoryLookup empty ONLY if the information is already present.\n\n");
        
        protocol.append("MANDATORY RESPONSE FORMAT (LLMResponse):\n");
        protocol.append("{\n");
        protocol.append("  \"previousOperation\": \"<last executed operation or empty>\",\n");
        protocol.append("  \"nextOperation\": \"<verb name or empty if no action needed>\",\n");
        protocol.append("  \"planStatus\": \"<idle|in_progress|completed|awaiting_input>\",\n");
        protocol.append("  \"executedOperations\": [\"<list of operations executed in this session>\"],\n");
        protocol.append("  \"memoryLookup\": \"<lookup query or empty>\",\n");
        protocol.append("  \"arguments\": { <arguments for nextOperation> },\n");
        protocol.append("  \"summaryForLLM\": \"<concise machine summary>\",\n");
        protocol.append("  \"responseForUser\": \"<user-visible output>\"\n");
        protocol.append("}\n\n");
        
        protocol.append("PLAN STATUS VALUES:\n");
        protocol.append("- 'idle': No plan is being executed (use for conversational responses)\n");
        protocol.append("- 'in_progress': A plan is actively being executed\n");
        protocol.append("- 'completed': The requested task has been completed\n");
        protocol.append("- 'awaiting_input': The plan requires user input to continue\n\n");
        
        protocol.append("FAILURE MODE:\n");
        protocol.append("- If NO operation is valid or needed, return nextOperation as an empty string.\n");
        protocol.append("- NEVER invent verbs.\n");
        protocol.append("- NEVER emit partial JSON.\n");
        protocol.append("- NEVER produce more than one JSON object.\n");
        protocol.append("- NEVER re-execute already completed operations.\n");
        
        return protocol.toString();
    }
    
    /**
     * Extracts executed operations from WebSocky communication responses.
     * Uses LinkedHashSet for efficient uniqueness while maintaining insertion order.
     */
    private List<String> getExecutedOperations(WebSocky socketConnection) {
        if (socketConnection == null || socketConnection.getCommunicationResponses() == null) {
            return new ArrayList<>();
        }
        LinkedHashSet<String> uniqueOps = new LinkedHashSet<>();
        for (LLMResponse r : socketConnection.getCommunicationResponses()) {
            if (r.getPreviousOperation() != null && !r.getPreviousOperation().isEmpty()) {
                uniqueOps.add(r.getPreviousOperation());
            }
        }
        return new ArrayList<>(uniqueOps);
    }
    
    /**
     * Gets the current plan status from the last response.
     */
    private String getCurrentPlanStatus(WebSocky socketConnection) {
        if (socketConnection == null || socketConnection.getCommunicationResponses() == null 
            || socketConnection.getCommunicationResponses().isEmpty()) {
            return "idle";
        }
        LLMResponse lastResponse = socketConnection.getCommunicationResponses()
            .get(socketConnection.getCommunicationResponses().size() - 1);
        return lastResponse.getPlanStatus() != null ? lastResponse.getPlanStatus() : "idle";
    }

    /**
     * Prompts the agent for workload based on the provided arguments.
     *
     * @return An `ArrayNode` containing the plan generated by the agent.
     * @throws io.sentrius.sso.core.exceptions.ZtatException If there is an error during the operation.
     * @throws java.io.IOException If there is an error reading the configuration file.
     */
    @Verb(name = "interpret_user_request", returnType = ArrayNode.class, description = "Queries the LLM using the " +
        "user input.",
        isAiCallable = false, requiresTokenManagement = true)
    public LLMResponse interpretUserData(
        AgentExecution execution, AgentExecutionContextDTO executionContext, @NonNull WebSocky socketConnection,
        @NonNull Message userMessage) throws ZtatException,
        IOException {

        var lastMessage = socketConnection.getCommunicationResponses().stream().reduce((prev, next) -> next).orElse(null);
        
        // Extract executed operations and current plan status for context
        List<String> executedOperations = getExecutedOperations(socketConnection);
        String currentPlanStatus = getCurrentPlanStatus(socketConnection);
        
        if (socketConnection.getCommunicationResponses().isEmpty()) {

            InputStream terminalHelperStream = getClass().getClassLoader().getResourceAsStream("terminal-helper.json");
            if (terminalHelperStream == null) {
                throw new RuntimeException("assessor-config.yaml not found on classpath");

            }

            String terminalResponse = new String(terminalHelperStream.readAllBytes());

            AgentConfig config = getAgentConfig(execution);
            log.info("Agent config loaded: {}", config);
            PromptBuilder promptBuilder = new PromptBuilder(verbRegistry, config);
            var prompt = promptBuilder.buildPrompt(false
            );
            List<Message> messages = new ArrayList<>();
            var context = Message.builder().role("system").content(prompt).build();
            messages.add(context);

            messages.add(Message.builder().role("system").content("You have executed verbs for the previous user " +
                "messages. Please generate a user response that summarizes the last message.").build());
            int size = getMessageSize(context);

            var history = getContextWindow(executionContext.getMessages(), 1024*96 - (size));
            messages.addAll(history);
            
            // Use the new strict mode protocol with plan state awareness (chat mode, not autonomous)
            messages.add(
                Message.builder()
                    .role("system")
                    .content(buildStrictModeProtocol(executedOperations, currentPlanStatus, false))
                    .build()
            );
            messages.add(Message.builder().role("user").content(userMessage.getContentAsString()).build());
            LLMRequest chatRequest = LLMRequest.builder().model("gpt-4o-mini").messages(messages).build();
            var resp = llmService.askQuestion(execution, chatRequest);
            
            // Only add user message to context, not the full messages array (avoids context duplication)
            executionContext.addMessages(userMessage);
            
            Response response = JsonUtil.MAPPER.readValue(resp, Response.class);
            log.info("Response is {}", resp);
            for (Response.OutputItem choice : response.getOutputItems()) {
                var content = choice.getContent().stream().filter(c -> "output_text".equals(c.getType()) || "text".equals(c.getType())).map(c -> c.getText()).findFirst().orElse("");
                if (content.startsWith("```json")) {
                    content = content.substring(7, content.length() - 3);
                } else if (content.startsWith("```")) {
                    content = content.substring(3, content.length() - 3);
                }
                log.info("+ {}", content);
                if (null != content && !content.isEmpty()) {
                    // Add assistant response to context
                    executionContext.addMessages(Message.builder().role("assistant").content(content).build());
                    try {
                        var newResponse = JsonUtil.MAPPER.enable(JsonParser.Feature.ALLOW_COMMENTS).readValue(
                            content,
                            LLMResponse.class
                        );
                        // Update executed operations list using helper
                        ensureExecutedOperationsInitialized(newResponse, executedOperations);
                        return newResponse;
                    }catch (JsonParseException e) {
                        log.error("Failed to parse terminal response: {}", e.getMessage());
                        return LLMResponse.builder()
                            .responseForUser(content)
                            .planStatus("idle")
                            .executedOperations(executedOperations)
                            .build();
                    }
                }
            }
        } else {
            InputStream terminalHelperStream = getClass().getClassLoader().getResourceAsStream("terminal-helper.json");
            if (terminalHelperStream == null) {
                throw new RuntimeException("assessor-config.yaml not found on classpath");

            }
            String terminalResponse = new String(terminalHelperStream.readAllBytes());

            AgentConfig config = getAgentConfig(execution);

            log.info("Agent config loaded: {}", config);
            PromptBuilder promptBuilder = new PromptBuilder(verbRegistry, config);
            var prompt = promptBuilder.buildPrompt(false);
            List<Message> messages = new ArrayList<>();
            
            // Always include the prompt with verb operations as the first system message
            // This ensures the agent knows what verbs are available for follow-up requests
            var context = Message.builder().role("system").content(prompt).build();
            messages.add(context);
            int size = getMessageSize(context);

            var history = getContextWindow(executionContext.getMessages(), 1024*96 - size);
            messages.addAll(history);
            
            // Add the strict mode protocol with plan state awareness for follow-on messages (chat mode)
            messages.add(
                Message.builder()
                    .role("system")
                    .content(buildStrictModeProtocol(executedOperations, currentPlanStatus, false))
                    .build()
            );

            // Add user message to context (just the user message, not the full messages array)
            executionContext.addMessages( userMessage );
            messages.add(Message.builder().role("user").content(userMessage.getContentAsString()).build());

            LLMRequest chatRequest = LLMRequest.builder().model("gpt-4o-mini").messages(messages).build();
            var resp = llmService.askQuestion(execution, chatRequest);

            Response response = JsonUtil.MAPPER.readValue(resp, Response.class);
            log.info("Response is {}", resp);
            Optional<LLMResponse> convertedResponse = LLMResponse.extractStructuredResponse(response);

            // Only add the LLM's response to context (avoids context duplication)
            String stringResponse = LLMResponse.extractStructuredResponseString(response);
            if (!stringResponse.isEmpty()) {
                executionContext.addMessages(Message.builder().role("assistant").content(stringResponse).build());
            }
            
            // Update the response with executed operations context
            LLMResponse finalResponse = convertedResponse.orElseGet(() -> 
                LLMResponse.builder()
                    .planStatus("idle")
                    .executedOperations(executedOperations)
                    .build()
            );
            
            ensureExecutedOperationsInitialized(finalResponse, executedOperations);
            
            return finalResponse;

            /*

            for (Response.OutputItem choice : response.getOutputItems()) {
                var content = choice.getContent().stream().filter(c -> "output_text".equals(c.getType()) || "text".equals(c.getType())).map(c -> c.getText()).findFirst().orElse("");
                if (content.startsWith("```json")) {
                    content = content.substring(7, content.length() - 3);
                } else if (content.startsWith("```")) {
                    content = content.substring(3, content.length() - 3);
                }
                log.info("content is {}", content);
                if (null != content && !content.isEmpty()) {

                    executionContext.addMessages( Message.builder().role(choice.getRole()).content(content).build() );
                    try {
                        var newResponse = JsonUtil.MAPPER.enable(JsonParser.Feature.ALLOW_COMMENTS).readValue(
                            content,
                            LLMResponse.class
                        );
                        return newResponse;
                    }catch (JsonParseException e) {
                        log.error("Failed to parse terminal response: {}", e.getMessage());
                        return LLMResponse.builder().responseForUser(content).summaryForLLM(lastMessage.getSummaryForLLM()).build();
                    }

                }
            }*/
        }

        return null;
    }

    public LLMResponse promptAgent(
        AgentExecution execution, AgentExecutionContextDTO executionContext,
        String prompt) throws ZtatException,
        IOException {
        return promptAgent(execution, executionContext, prompt, new ArrayList<>(), "idle", false);
    }
    
    /**
     * Prompts the agent for workload with plan state awareness.
     * 
     * @param execution The agent execution context
     * @param executionContext The execution context DTO
     * @param prompt The prompt to send to the LLM
     * @param executedOperations List of operations already executed
     * @param currentPlanStatus The current plan status
     * @return The LLM response
     */
    public LLMResponse promptAgent(
        AgentExecution execution, AgentExecutionContextDTO executionContext,
        String prompt, List<String> executedOperations, String currentPlanStatus) throws ZtatException,
        IOException {
        return promptAgent(execution, executionContext, prompt, executedOperations, currentPlanStatus, false);
    }
    
    /**
     * Prompts the agent for workload with plan state awareness.
     * 
     * @param execution The agent execution context
     * @param executionContext The execution context DTO
     * @param prompt The prompt to send to the LLM
     * @param executedOperations List of operations already executed
     * @param currentPlanStatus The current plan status
     * @param isAutonomous Whether this is an autonomous agent (not chat-driven)
     * @return The LLM response
     */
    public LLMResponse promptAgent(
        AgentExecution execution, AgentExecutionContextDTO executionContext,
        String prompt, List<String> executedOperations, String currentPlanStatus, boolean isAutonomous) throws ZtatException,
        IOException {


            InputStream terminalHelperStream = getClass().getClassLoader().getResourceAsStream("terminal-helper.json");
            if (terminalHelperStream == null) {
                throw new RuntimeException("assessor-config.yaml not found on classpath");

            }

            String terminalResponse = new String(terminalHelperStream.readAllBytes());

            AgentConfig config = getAgentConfig(execution);
            log.info("Agent config loaded: {}", config);

            List<Message> messages = new ArrayList<>();
            var context = Message.builder().role("system").content(prompt).build();
            messages.add(context);

            if (isAutonomous) {
                messages.add(Message.builder().role("system").content("You are operating in autonomous mode. " +
                    "Analyze your available verbs and create a plan to accomplish your configured task. " +
                    "Execute one operation at a time using nextOperation.").build());
            } else {
                messages.add(Message.builder().role("system").content("You have executed verbs for the previous user " +
                    "messages. Please generate a user response that summarizes the last message.").build());
            }
            int size = getMessageSize(context);

            var history = getContextWindow(executionContext.getMessages(), 1024*96 - (size));
            messages.addAll(history);
            
        // Use the new strict mode protocol with plan state awareness
        messages.add(
            Message.builder()
                .role("system")
                .content(buildStrictModeProtocol(executedOperations, currentPlanStatus, isAutonomous))
                .build()
        );

        LLMRequest chatRequest = LLMRequest.builder().model("gpt-4o-mini").messages(messages).build();
            var resp = llmService.askQuestion(execution, chatRequest);
            
            // Only add the LLM response to context, not the entire prompt/messages array
            // This prevents context duplication
            String stringResponse = LLMResponse.extractStructuredResponseString(
                JsonUtil.MAPPER.readValue(resp, Response.class));
            if (!stringResponse.isEmpty()) {
                executionContext.addMessages(Message.builder().role("assistant").content(stringResponse).build());
            }
            
            Response response = JsonUtil.MAPPER.readValue(resp, Response.class);
            log.info("Response is {}", resp);
            Optional<LLMResponse> convertedResponse = LLMResponse.extractStructuredResponse(response);

            LLMResponse finalResponse = convertedResponse.orElseGet(() -> 
                LLMResponse.builder()
                    .planStatus("idle")
                    .executedOperations(executedOperations)
                    .build()
            );
            
            ensureExecutedOperationsInitialized(finalResponse, executedOperations);
            
            return finalResponse;
            /*
            for (Response.OutputItem choice : response.getOutputItems()) {
                var content = choice.getContent().stream().filter(c -> "output_text".equals(c.getType()) || "text".equals(c.getType())).map(c -> c.getText()).findFirst().orElse("");
                if (content.startsWith("```json")) {
                    content = content.substring(7, content.length() - 3);
                } else if (content.startsWith("```")) {
                    content = content.substring(3, content.length() - 3);
                }
                log.info("+ {}", content);
                if (null != content && !content.isEmpty()) {
                    try {
                        var newResponse = JsonUtil.MAPPER.enable(JsonParser.Feature.ALLOW_COMMENTS).readValue(
                            content,
                            LLMResponse.class
                        );
                        return newResponse;
                    }catch (JsonParseException e) {
                        log.error("Failed to parse terminal response: {}", e.getMessage());
                        return LLMResponse.builder().responseForUser(content).build();
                    }
                }
            }*/
    }


    public List<Message> getContextWindow(List<Message> allMessages, int maxContextSize) {
        List<Message> systemMessages = new ArrayList<>();
        List<Message> window = new ArrayList<>();
        int totalSize = 0;

        // First: collect system messages (or other required ones)
        for (Message msg : allMessages) {
            if ("system".equals(msg.role)) {
                systemMessages.add(msg);
                totalSize += getMessageSize(msg);
            }
        }

        // If system messages already exceed max context, return only those
        if (totalSize >= maxContextSize) {
            return systemMessages;
        }

        int remainingSize = maxContextSize - totalSize;

        // Then: collect non-system messages from the end, up to remainingSize
        ListIterator<Message> iter = allMessages.listIterator(allMessages.size());
        while (iter.hasPrevious()) {
            Message msg = iter.previous();

            if ("system".equals(msg.role)) continue; // already added

            int messageSize = getMessageSize(msg);
            if (messageSize > remainingSize) break;

            window.add(0, msg); // prepend
            remainingSize -= messageSize;
        }

        // Combine system + selected recent messages
        List<Message> result = new ArrayList<>();
        result.addAll(systemMessages);
        result.addAll(window);

        return result;
    }


    private int getMessageSize(Message msg) {
        int size = 0;
        if (msg.role != null) size += msg.role.length();
        if (msg.content != null) {
            String contentStr = msg.getContentAsString();
            if (contentStr != null) size += contentStr.length();
        }
        if (msg.refusal != null) size += msg.refusal.length();
        return size;
    }

    public LLMResponse interpret_plan_response(
        AgentExecution execution, AgentExecutionContextDTO executionContext,
        AgentVerb agentVerb, String planExecutionOutput) throws ZtatException,
        IOException {
        return interpret_plan_response(execution, executionContext, agentVerb, planExecutionOutput, 
            new ArrayList<>(), "in_progress", false);
    }
    
    /**
     * Interprets the response from a plan execution with full plan state awareness.
     * This method determines if the plan is complete or if more operations are needed.
     * 
     * @param execution The agent execution context
     * @param executionContext The execution context DTO
     * @param agentVerb The verb that was just executed
     * @param planExecutionOutput The output from the verb execution
     * @param executedOperations List of operations already executed
     * @param currentPlanStatus The current status of the plan
     * @return The LLM response indicating next steps or completion
     */
    public LLMResponse interpret_plan_response(
        AgentExecution execution, AgentExecutionContextDTO executionContext,
        AgentVerb agentVerb, String planExecutionOutput,
        List<String> executedOperations, String currentPlanStatus) throws ZtatException,
        IOException {
        return interpret_plan_response(execution, executionContext, agentVerb, planExecutionOutput,
            executedOperations, currentPlanStatus, false);
    }
    
    /**
     * Interprets the response from a plan execution with full plan state awareness.
     * This method determines if the plan is complete or if more operations are needed.
     * 
     * @param execution The agent execution context
     * @param executionContext The execution context DTO
     * @param agentVerb The verb that was just executed
     * @param planExecutionOutput The output from the verb execution
     * @param executedOperations List of operations already executed
     * @param currentPlanStatus The current status of the plan
     * @param isAutonomous Whether this is an autonomous agent
     * @return The LLM response indicating next steps or completion
     */
    public LLMResponse interpret_plan_response(
        AgentExecution execution, AgentExecutionContextDTO executionContext,
        AgentVerb agentVerb, String planExecutionOutput,
        List<String> executedOperations, String currentPlanStatus, boolean isAutonomous) throws ZtatException,
        IOException {


        log.info("interpret_plan_response {}", planExecutionOutput);

            InputStream terminalHelperStream = getClass().getClassLoader().getResourceAsStream("terminal-helper.json");
            if (terminalHelperStream == null) {
                throw new RuntimeException("assessor-config.yaml not found on classpath");

            }
            String terminalResponse = new String(terminalHelperStream.readAllBytes());

            AgentConfig config = getAgentConfig(execution);

            log.info("Agent config loaded: {}", config);
            PromptBuilder promptBuilder = new PromptBuilder(verbRegistry, config);
            var prompt = promptBuilder.buildPrompt(false);
            List<Message> messages = new ArrayList<>();
            
            // Track the verb we just executed
            List<String> updatedExecutedOps = new ArrayList<>(executedOperations);
            if (agentVerb != null && !updatedExecutedOps.contains(agentVerb.getName())) {
                updatedExecutedOps.add(agentVerb.getName());
            }


            // Always include the prompt with verb operations as the first system message
            // This ensures the agent knows what verbs are available for plan responses
            var context = Message.builder().role("system").content(prompt).build();
            messages.add(context);
            int contextSize = getMessageSize(context);

            if (executionContext.getMessages().isEmpty()) {
                log.info("*** Adding Prompt instruction");

                messages.add(Message.builder().role("system").content("You have executed verbs for the previous user " +
                    "messages. Please generate a user response that summarizes the last message. Keep all responses in " +
                    "LLMResponse format" +
                    ".").build());
            } else {
                // Get a window of conversation history, accounting for the context size
                var history = getContextWindow(executionContext.getMessages(), 1024*96 - contextSize);
                messages.addAll(history);
            }


            if (null != agentVerb) {
                messages.add(
                    Message.builder().role("system").content("You have just executed verb: " + agentVerb.getName() +
                        " with the following description: " + agentVerb.getDescription() + 
                        ". Evaluate if the task is now complete.").build());
            }


        if (!planExecutionOutput.isEmpty()) {
            messages.add(Message.builder().role("system").content("Execution result: " + planExecutionOutput).build());
        }
        
        // Add the strict mode protocol with updated plan state
        String taskCompleteInstructions = isAutonomous ?
            "\nIMPORTANT: After executing a verb, evaluate if your autonomous task is complete. " +
            "If the task is complete, set planStatus to 'completed' and nextOperation to empty string. " +
            "Only continue with another operation if truly necessary to complete your task." :
            "\nIMPORTANT: After executing a verb, evaluate if the user's request has been fulfilled. " +
            "If the task is complete, set planStatus to 'completed' and nextOperation to empty string. " +
            "Only continue with another operation if truly necessary to fulfill the original request.";
            
        messages.add(
            Message.builder()
                .role("system")
                .content(buildStrictModeProtocol(updatedExecutedOps, currentPlanStatus, isAutonomous) + taskCompleteInstructions)
                .build()
        );
        
            LLMRequest chatRequest = LLMRequest.builder().model("gpt-4o-mini").messages(messages).build();
            var resp = llmService.askQuestion(execution, chatRequest);

            Response response = JsonUtil.MAPPER.readValue(resp, Response.class);
            log.info("Response is {}", resp);
        Optional<LLMResponse> convertedResponse = LLMResponse.extractStructuredResponse(response);
        String stringResponse = LLMResponse.extractStructuredResponseString(response);
        
        // Only add the LLM's response to context, not all the prompts (prevents context duplication)
        if (!stringResponse.isEmpty()) {
            executionContext.addMessages(Message.builder().role("assistant").content(stringResponse).build());
        }
        
        LLMResponse finalResponse = convertedResponse.orElseGet(() -> 
            LLMResponse.builder()
                .planStatus("completed")
                .executedOperations(updatedExecutedOps)
                .build()
        );
        
        // Ensure executed operations are tracked using helper, then merge any additional
        ensureExecutedOperationsInitialized(finalResponse, updatedExecutedOps);
        
        // Merge with any new operations from LLM that weren't already tracked
        for (String op : updatedExecutedOps) {
            if (!finalResponse.getExecutedOperations().contains(op)) {
                finalResponse.getExecutedOperations().add(op);
            }
        }
        
        // Update the previousOperation to the verb we just executed
        if (agentVerb != null) {
            finalResponse.setPreviousOperation(agentVerb.getName());
        }
        
        return finalResponse;
    }



}
