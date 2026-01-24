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
        protocol.append("- You MUST use verb lookup verbs when you don't know which verb to use.\n");
        protocol.append("- You MUST select and execute the next operation using discovered verbs ONLY if action is needed.\n");
        protocol.append("- If required information is missing, you MUST infer conservatively OR return an executable fallback.\n");
        protocol.append("- Returning free-form text is a protocol violation.\n");
        protocol.append("- CRITICAL: NEVER invent verb names. If a verb doesn't exist, use verb lookup to find it.\n");
        protocol.append("- Analysis/thinking happens in summaryForLLM, NOT as a verb execution.\n\n");

        if (isAutonomous) {
            // Autonomous agent mode - encourage plan creation and execution
            protocol.append("AUTONOMOUS AGENT MODE:\n");
            protocol.append("- You are operating autonomously without user interaction.\n");
            protocol.append("- CRITICAL: Read your Context carefully and identify ALL steps required before starting.\n");
            protocol.append("- If context says 'do A and do B', you must complete BOTH A and B.\n");
            protocol.append("- If context says 'do A if B', you must: (1) check B, (2) decide, (3) conditionally do A.\n");
            protocol.append("- Set planStatus to 'in_progress' while working, 'completed' ONLY when ALL steps done.\n\n");

            protocol.append("TASK ANALYSIS (REQUIRED FIRST STEP):\n");
            protocol.append("- Before executing ANY verb, mentally break down your Context into discrete steps.\n");
            protocol.append("- Example context: 'Scan terminals and kill if no GitHub ticket'\n");
            protocol.append("  Required steps: (1) list terminals, (2) get logs, (3) query GitHub, (4) analyze, (5) conditionally kill\n");
            protocol.append("- Example context: 'Monitor pods and alert on failures'\n");
            protocol.append("  Required steps: (1) list pods, (2) check status, (3) detect failures, (4) send alert\n");
            protocol.append("- DO NOT mark complete until ALL steps in your mental breakdown are done.\n\n");

            protocol.append("EXECUTION SEQUENCE:\n");
            protocol.append("- Execute verbs one at a time in logical order.\n");
            protocol.append("- Each verb should produce data needed for the next step.\n");
            protocol.append("- Use verb lookup to discover verbs as needed.\n");
            protocol.append("- After each verb execution, evaluate results and determine next step.\n");
            protocol.append("- If results indicate you need different verbs, use verb lookup again.\n");
            protocol.append("- Continue executing verbs until ALL task objectives are complete.\n\n");

            protocol.append("TASK COMPLETION CRITERIA:\n");
            protocol.append("- A task is ONLY complete when ALL objectives from the context have been fulfilled.\n");
            protocol.append("- Gathering data is NOT completion - you must analyze and act on that data.\n");
            protocol.append("- Setting up integrations or discovering verbs is NOT completion - these are preparatory steps.\n");
            protocol.append("- If your context says to 'scan X and query Y', you must: (1) scan X, (2) query Y, (3) analyze results, (4) take action.\n");
            protocol.append("- If your context says to 'do X if Y not found', you must: (1) do X, (2) check Y, (3) conditionally act based on result.\n");
            protocol.append("- Before setting planStatus to 'completed', verify in summaryForLLM that ALL task objectives are done.\n");
            protocol.append("- Example: 'Scan terminals and kill if no GitHub ticket' means:\n");
            protocol.append("    1. List open terminals (verb: list_open_terminals)\n");
            protocol.append("    2. Fetch terminal logs (verb: fetch_terminal_logs)\n");
            protocol.append("    3. Query GitHub for tickets (verb: search_verbs, then github_mcp_proxy)\n");
            protocol.append("    4. Analyze internally (summaryForLLM: 'Checking if GitHub results match terminal activity...')\n");
            protocol.append("    5. Kill terminals if no match (verb: kill_terminal_session)\n");
            protocol.append("- NEVER mark complete after just discovering verbs or listing integrations.\n\n");

            protocol.append("ERROR HANDLING:\n");
            protocol.append("- If a verb execution fails with an error, READ THE ERROR MESSAGE carefully.\n");
            protocol.append("- Error messages tell you what's wrong and how to fix it (e.g., missing arguments).\n");
            protocol.append("- Adjust your nextOperation based on the error - don't retry the same invalid operation.\n");
            protocol.append("- If you tried to skip a required step (like calling a verb without discovering it first), use verb lookup.\n");
            protocol.append("- If you used placeholder/example data (e.g., 'encrypted-session-id'), go back and get real data first.\n\n");

            protocol.append("USING EXECUTION RESULTS:\n");
            protocol.append("- After each verb execution, the results are available in your context.\n");
            protocol.append("- Extract actual values from previous results to use in subsequent operations.\n");
            protocol.append("- NEVER use placeholder values like 'encrypted-session-id', 'host-id-123', 'example-value'.\n");
            protocol.append("- Example: If list_open_terminals returns [{\"hostConnection\": \"abc123\"}], use \"abc123\" not \"encrypted-session-id\".\n");
            protocol.append("- If you don't see the data you need, the verb likely hasn't been executed yet - do it first.\n\n");

            protocol.append("ANALYSIS vs VERB EXECUTION:\n");
            protocol.append("- VERBS are for actions: list, fetch, query, kill, send, create, update, delete.\n");
            protocol.append("- ANALYSIS is internal reasoning - it happens in your summaryForLLM, NOT as a verb.\n");
            protocol.append("- WRONG: {\"nextOperation\": \"analyze_terminal_logs\"} - 'analyze' is not a verb!\n");
            protocol.append("- RIGHT: {\"nextOperation\": \"search_verbs\", \"summaryForLLM\": \"Need to query GitHub...\"}\n");
            protocol.append("- After fetching data, analyze it internally, then execute the NEXT action verb.\n");
            protocol.append("- Common analysis tasks that are NOT verbs:\n");
            protocol.append("    * analyze_logs → Do internally, then query GitHub or kill terminal\n");
            protocol.append("    * check_for_ticket → Query GitHub verb instead\n");
            protocol.append("    * evaluate_results → Do internally, then decide next verb\n");
            protocol.append("    * determine_action → Do internally in summaryForLLM\n");
            protocol.append("- If you need to analyze, set nextOperation to the NEXT ACTION verb or empty string.\n\n");

            protocol.append("MULTI-STEP EXECUTION:\n");
            protocol.append("- Break complex tasks into discrete verb operations.\n");
            protocol.append("- Each operation should produce data needed for the next step.\n");
            protocol.append("- Use the execution results to inform your next operation choice.\n");
            protocol.append("- NEVER use placeholder or example values - always use real data from previous verb results.\n");
            protocol.append("- Example workflow for 'scan terminals and check GitHub':\n");
            protocol.append("    Step 1: list_open_terminals → Get actual terminal list\n");
            protocol.append("    Step 2: fetch_terminal_logs → Get actual logs from terminals in Step 1\n");
            protocol.append("    Step 3: Query GitHub → Use actual data from Step 2 to search\n");
            protocol.append("    Step 4: Analyze → Check if GitHub results match terminal activity\n");
            protocol.append("    Step 5: Take action → Kill terminals based on Step 4 analysis\n");
            protocol.append("- If you don't have the required data yet, go back and get it first.\n\n");

            protocol.append("VERB DISCOVERY WORKFLOW (RECOMMENDED PROCESS):\n");
            protocol.append("- STEP 1: If you don't know what verbs are available for a task, use verb lookup.\n");
            protocol.append("  Examples:\n");
            protocol.append("  * search_verbs with arguments: { \"keywords\": \"slack send message\", \"maxResults\": 5 }\n");
            protocol.append("  * find_verbs_by_intent with arguments: { \"intent\": \"send notification to Slack\", \"maxResults\": 5 }\n");
            protocol.append("  * get_verbs_by_category with arguments: { \"category\": \"slack\" }\n");
            protocol.append("- STEP 2: Review the discovered verbs in the execution results.\n");
            protocol.append("- STEP 3: Use get_verb_details to get full information about the verb you want to use.\n");
            protocol.append("  Example: get_verb_details with arguments: { \"verbName\": \"send_slack_message\" }\n");
            protocol.append("- STEP 4: Execute the discovered verb with proper arguments.\n");
            protocol.append("  Example: send_slack_message with arguments: { \"channel\": \"#general\", \"message\": \"Hello\" }\n");
            protocol.append("- If verb lookup returns no results, report this in responseForUser and mark complete.\n\n");
        } else {
            // Chat-driven mode - handle conversational inputs
            protocol.append("CONVERSATIONAL INPUT HANDLING:\n");
            protocol.append("- If the user input is purely conversational (e.g., 'Thanks!', 'hello', 'great', 'ok', 'bye'),\n");
            protocol.append("  you MUST include a brief response ONLY inside the responseForUser field.\n");
            protocol.append("- For conversational inputs, set nextOperation to empty string and planStatus to 'completed' or 'idle'.\n");
            protocol.append("- DO NOT execute any verbs for simple acknowledgments or greetings.\n");
            protocol.append("- Conversational text MUST NOT trigger new operations.\n");
            protocol.append("- Always include a user response even for conversational inputs.\n");
            protocol.append("- If new user input changes requirements, use verb lookup to find appropriate verbs.\n\n");
        }
        
        protocol.append("VERB DISCOVERY RULES:\n");
        protocol.append("- The system has 75+ verbs organized by category (slack, k8s, llm, mcp, jira, teams, etc.).\n");
        protocol.append("- DO NOT assume verb names. Use verb lookup verbs to discover the correct verb:\n");
        protocol.append("  * search_verbs: Search by keywords (e.g., {\"keywords\": \"slack send message\", \"maxResults\": 5})\n");
        protocol.append("  * get_verbs_by_category: Browse by category (e.g., {\"category\": \"slack\"})\n");
        protocol.append("  * get_verb_summary: Get overview of all categories\n");
        protocol.append("  * get_verb_details: Get full details about a specific verb (e.g., {\"verbName\": \"send_slack_message\"})\n");
        protocol.append("  * find_verbs_by_intent: Natural language search (e.g., {\"intent\": \"send message to Slack\", \"maxResults\": 5})\n");
        protocol.append("- Verb discovery workflow:\n");
        protocol.append("  1. Use search_verbs or find_verbs_by_intent to discover relevant verbs\n");
        protocol.append("  2. Review the search results in the execution output\n");
        protocol.append("  3. Use get_verb_details to get full information about the verb you want to use\n");
        protocol.append("  4. Execute the discovered verb with proper arguments\n");
        protocol.append("- If new information arrives that changes your plan, repeat the discovery process.\n");
        protocol.append("- All verbs (except verb lookup verbs themselves) should be discovered through this mechanism.\n\n");

        protocol.append("MEMORY RULES:\n");
        protocol.append("- If required information is NOT in the current context, you MUST populate the memoryLookup field.\n");
        protocol.append("- memoryLookup is executed BEFORE nextOperation.\n");
        protocol.append("- Leave memoryLookup empty ONLY if the information is already present.\n\n");
        
        protocol.append("VERB CHAINING & OUTPUT PASSING:\n");
        protocol.append("- Verb outputs are automatically stored in memory with their returnName.\n");
        protocol.append("- To use output from a previous verb as input to the next verb:\n");
        protocol.append("  1. First verb executes and stores output (e.g., 'verb_search_results')\n");
        protocol.append("  2. Output is available in memory and in execution results\n");
        protocol.append("  3. Next verb can reference this data in its arguments\n");
        protocol.append("- Example workflow:\n");
        protocol.append("  Step 1: {\"nextOperation\": \"search_verbs\", \"arguments\": {\"keywords\": \"slack send\", \"maxResults\": 5}}\n");
        protocol.append("  Step 2: Review search results in execution output\n");
        protocol.append("  Step 3: {\"nextOperation\": \"get_verb_details\", \"arguments\": {\"verbName\": \"send_slack_message\"}}\n");
        protocol.append("  Step 4: {\"nextOperation\": \"send_slack_message\", \"arguments\": {\"channel\": \"#general\", \"message\": \"Hi\"}}\n");
        protocol.append("- Always check execution results for verb outputs before planning next steps.\n\n");

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
        protocol.append("- NEVER invent verbs like 'analyze_terminal_logs', 'check_for_ticket', 'evaluate_results'.\n");
        protocol.append("- If unsure what verb to use, use search_verbs or find_verbs_by_intent to discover it.\n");
        protocol.append("- If a verb doesn't exist for your intended action, do the analysis internally and execute the next real verb.\n");
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
            LLMRequest chatRequest = LLMRequest.builder().model("gpt-4.1").messages(messages).build();
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

            LLMRequest chatRequest = LLMRequest.builder().model("gpt-4.1").messages(messages).build();
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
                    "Use verb lookup verbs (search_verbs, get_verbs_by_category, get_verb_details) to discover available operations. " +
                    "Analyze discovered verbs and create a plan to accomplish your configured task. " +
                    "Execute one operation at a time using nextOperation. " +
                    "Verb outputs are stored in memory and available for subsequent verb calls.").build());
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

        LLMRequest chatRequest = LLMRequest.builder().model("gpt-4.1").messages(messages).build();
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
        
            LLMRequest chatRequest = LLMRequest.builder().model("gpt-4.1").messages(messages).build();
            var resp = llmService.askQuestion(execution, chatRequest);

            Response response = JsonUtil.MAPPER.readValue(resp, Response.class);
            log.trace("Response is {}", resp);
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
