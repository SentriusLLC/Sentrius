package io.sentrius.agent.analysis.agents.verbs;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import io.sentrius.agent.analysis.agents.agents.AgentConfig;
import io.sentrius.agent.analysis.agents.agents.PromptBuilder;
import io.sentrius.agent.analysis.agents.agents.VerbRegistry;
import io.sentrius.agent.analysis.agents.agents.VerbLookupService;
import io.sentrius.agent.analysis.model.AssessedTerminal;
import io.sentrius.agent.analysis.model.Assessment;
import io.sentrius.agent.analysis.model.LLMResponse;
import io.sentrius.agent.analysis.model.ZtatAsessment;
import io.sentrius.agent.analysis.model.ZtatResponse;
import io.sentrius.agent.services.EndpointRegistry;
import io.sentrius.agent.services.EndpointSearcher;
import io.sentrius.sso.core.dto.AgentCommunicationDTO;
import io.sentrius.sso.core.dto.AgentRegistrationDTO;
import io.sentrius.sso.core.dto.ZtatDTO;
import io.sentrius.sso.core.dto.agents.AgentContextDTO;
import io.sentrius.sso.core.dto.agents.AgentContextRequestDTO;
import io.sentrius.sso.core.dto.agents.AgentExecutionContextDTO;
import io.sentrius.sso.core.dto.agents.AgentExecution;
import io.sentrius.sso.core.dto.capabilities.EndpointDescriptor;
import io.sentrius.sso.core.dto.ztat.AtatRequest;
import io.sentrius.sso.core.dto.ztat.TokenDTO;
import io.sentrius.sso.core.dto.ztat.ZtatRequestDTO;
import io.sentrius.sso.core.exceptions.ZtatException;
import io.sentrius.sso.core.model.verbs.Verb;
import io.sentrius.sso.core.services.agents.AgentClientService;
import io.sentrius.sso.core.services.agents.AgentExecutionService;
import io.sentrius.sso.core.services.agents.LLMService;
import io.sentrius.sso.core.services.agents.ZeroTrustClientService;
import io.sentrius.sso.core.trust.ATPLPolicy;
import io.sentrius.sso.core.trust.Capability;
import io.sentrius.sso.core.trust.CapabilitySet;
import io.sentrius.sso.core.utils.JsonUtil;
import io.sentrius.sso.core.utils.ListUtils;
import io.sentrius.sso.genai.Message;
import io.sentrius.sso.genai.Response;
import io.sentrius.sso.genai.model.LLMRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * The `AgentVerbs` class provides implementations for various agent-related verbs.
 * These verbs interact with AI models and configurations to perform tasks such as
 * prompting agents, justifying operations, and assessing data.
 */
@Service
@Slf4j
public class AgentVerbs extends VerbBase {

    final ZeroTrustClientService zeroTrustClientService;
    final LLMService llmService;
    final VerbRegistry verbRegistry;
    final VerbLookupService verbLookupService;
    final EndpointRegistry endpointRegistry;
    final EndpointSearcher endpointSearcher;

    final ObjectMapper mapper = new ObjectMapper(new YAMLFactory()); // Jackson ObjectMapper for YAML parsing
    private final AgentExecutionService agentExecutionService;
    
    // Field names to search when extracting query strings from nested JSON structures
    private static final String[] QUERY_FIELD_NAMES = {"arg1", "field", "context", "query", "text", "value"};


    /**
     * Constructs an `AgentVerbs` instance with the required services and registry.
     *
     * @param zeroTrustClientService The service for Zero Trust client interactions.
     * @param llmService             The service for interacting with the LLM (Large Language Model).
     * @param verbRegistry           The registry containing available verbs and their metadata.
     * @throws com.fasterxml.jackson.core.JsonProcessingException If there is an error processing JSON during
     *                                                            initialization.
     */
    public AgentVerbs(
        @Value("${agent.ai.config}") String agentConfigFile,
        @Value("${agent.ai.context.db.id:none}") String agentDatabaseContext,
        ZeroTrustClientService zeroTrustClientService, LLMService llmService, VerbRegistry verbRegistry,
        VerbLookupService verbLookupService, AgentClientService agentService, EndpointRegistry endpointRegistry, 
        EndpointSearcher endpointSearcher, AgentExecutionService agentExecutionService
    ) throws JsonProcessingException {
        super(agentConfigFile, agentDatabaseContext, agentService);
        this.zeroTrustClientService = zeroTrustClientService;
        this.llmService = llmService;
        this.verbRegistry = verbRegistry;
        this.verbLookupService = verbLookupService;
        this.endpointRegistry = endpointRegistry;
        this.endpointSearcher = endpointSearcher;

        log.info("Loading agent config from {}", agentConfigFile);
        this.agentExecutionService = agentExecutionService;
    }

    /**
     * Prompts the agent for workload based on the provided arguments.
     *
     * @return An `ArrayNode` containing the plan generated by the agent.
     * @throws io.sentrius.sso.core.exceptions.ZtatException If there is an error during the operation.
     * @throws java.io.IOException                           If there is an error reading the configuration file.
     */
    @Verb(
        name = "prompt_agent", returnType = ArrayNode.class, description = "Prompts for agent workload.",
        isAiCallable = false, requiresTokenManagement = true
    )
    public ArrayNode promptAgent(AgentExecution execution, AgentExecutionContextDTO context) throws ZtatException,
        IOException {

        AgentConfig config = getAgentConfig(execution);

        log.info("Agent config loaded: {}", config);
        PromptBuilder promptBuilder = new PromptBuilder(verbRegistry, config);
        var prompt = promptBuilder.buildPrompt();
        List<Message> messages = new ArrayList<>();

        messages.add(Message.builder().role("system").content(prompt).build());

        LLMRequest chatRequest = LLMRequest.builder().model("gpt-4.1").messages(messages).build();
        var resp = llmService.askQuestion(execution, chatRequest);
        if (null != context ) {
            context.addMessages(messages);
        }
        Response response = JsonUtil.MAPPER.readValue(resp, Response.class);
        //log.info("Response is {}", resp);
        for (Response.OutputItem choice : response.getOutputItems()) {
            var content = choice.getContent().stream().filter(c -> "output_text".equals(c.getType()) || "text".equals(c.getType())).map(c -> c.getText()).findFirst().orElse("");
            if (content.startsWith("```json")) {
                content = content.substring(7, content.length() - 3);
            } else if (content.startsWith("```")) {
                content = content.substring(3, content.length() - 3);
            }
            log.info("content is {}", content);
            if (null != content && !content.isEmpty()) {
                JsonNode node = JsonUtil.MAPPER.enable(JsonParser.Feature.ALLOW_COMMENTS).readTree(content);
                log.info("Node is {}", node);
                if (node.get("plan") != null) {
                    ArrayNode plan = (ArrayNode) node.get("plan");
                    log.info("Plan is {}", plan);
                    return plan;
                }
            }
        }
        log.info("ahhh");
        return JsonUtil.MAPPER.createArrayNode();
    }


    /**
     * Chats with an agent to justify operations based on the provided arguments.
     *
     * @return A string response from the agent.
     * @throws io.sentrius.sso.core.exceptions.ZtatException If there is an error during the operation.
     * @throws java.io.IOException                           If there is an error reading the configuration file.
     */
    @Verb(
        name = "justify_operations", description = "Chats with an agent to justify operations.", isAiCallable =
        false, requiresTokenManagement = true
    )
    public String justifyAgent(
        AgentExecution execution, AgentExecutionContextDTO context, ZtatRequestDTO ztatRequest,
        AssessedTerminal reason
    ) throws ZtatException,
        IOException, InterruptedException, TimeoutException {


        var status = zeroTrustClientService.getTokenStatus(execution, execution.getUser(), ztatRequest.getRequestId());
        log.info("Status: {} for {} ", status, ztatRequest);
        if ("approved".equals(status.get("status").asText())) {
            return status.get("ztat_token").asText();
        }

        InputStream assessZtatStream = getClass().getClassLoader().getResourceAsStream("respond-ztat.json");
        if (assessZtatStream == null) {
            throw new RuntimeException("assessor-config.yaml not found on classpath");

        }
        AtatRequest atat =
            AtatRequest.builder().requestId(ztatRequest.getRequestId()).requestedAction(ztatRequest.getCommand())
                .build();
        String respondZtat = new String(assessZtatStream.readAllBytes());

        while (!status.equals("approved")) {

            Thread.sleep(5_000);

            status = zeroTrustClientService.getTokenStatus(execution, execution.getUser(), ztatRequest.getRequestId());
            log.info("Status: {} for {} ", status, ztatRequest);
            if ("approved".equals(status.get("status").asText())) {
                return status.get("ztat_token").asText();
            }

            Set<String> commsIds = agentClientService.getCommunicationIds(execution, ztatRequest);

            commsIds.remove(execution.getCommunicationId());

            if (commsIds.isEmpty()) {
                continue;
            }

            if (commsIds.size() > 1) {
                // get the first one
                log.info("CommsIds is {}", commsIds);
            }


            var commsId = commsIds.iterator().next();

            AgentExecution newExecution =
                AgentExecution.builder().executionId(execution.getExecutionId()).ztatToken(execution.getZtatToken())
                    .communicationId(commsId).build();

            var nextMessaged = agentClientService.getResponse(newExecution, ztatRequest, 1, TimeUnit.MINUTES);
            Set<String> otherAgents = Sets.newHashSet();
            Set<UUID> communicationIds = new HashSet<>();
            if (!nextMessaged.isEmpty()) {
                List<Message> messages = new ArrayList<>();
                messages.add(Message.builder().role("system").content("The following messages are " +
                    "communications between two agents. One agent is interpreting data from another and may " +
                    "ask questions. Please respond to the questions using the initial guidance layed out in " +
                    "the next messages").build());
                messages.addAll(reason.getMessages());
                for (AgentCommunicationDTO agentCommunicationDTO : nextMessaged) {
                    if (agentCommunicationDTO.getTargetAgent().equals(execution.getUser().getUsername())) {
                        otherAgents.add(agentCommunicationDTO.getSourceAgent());
                    }
                    communicationIds.add(agentCommunicationDTO.getCommunicationId());
                }
                messages.add(Message.builder().role("system").content("please respond in the following json " +
                    "format: " + respondZtat).build());

                LLMRequest chatRequest = LLMRequest.builder().model("gpt-4.1").messages(messages).build();
                context.addMessages(messages);
                var resp = llmService.askQuestion(execution, chatRequest);
                Response response = JsonUtil.MAPPER.readValue(resp, Response.class);
                //log.info("Response is {}", resp);
                for (Response.OutputItem choice : response.getOutputItems()) {
                    var content = choice.getContent().stream().filter(c -> "output_text".equals(c.getType()) || "text".equals(c.getType())).map(c -> c.getText()).findFirst().orElse("");
                    if (content.startsWith("```json")) {
                        content = content.substring(7, content.length() - 3);
                    }


                    var ztatResponse = JsonUtil.MAPPER.readValue(
                        content,
                        ZtatResponse.class
                    );

                    for (var agent : otherAgents) {
                        for (var commId : communicationIds) {
                            AgentCommunicationDTO myResponse = AgentCommunicationDTO.builder()
                                .communicationId(commId)
                                .payload(JsonUtil.MAPPER.writeValueAsString(ztatResponse))
                                .messageType("atat_chat_respond")
                                .sourceAgent(execution.getUser().getUsername())
                                .targetAgent(agent)
                                .build();

                            agentClientService.sendResponse(execution, myResponse, ztatRequest);
                        }
                    }
                    log.info("content is {}", content);
                }
            }

            // check for messages


        }

        return null;
        //   return llmService.askQuestion(chatRequest);
    }

    /**
     * Assesses data based on the provided object list and the agent's context.
     *
     * @return An `ArrayNode` containing the assessment results.
     * @throws io.sentrius.sso.core.exceptions.ZtatException If there is an error during the operation.
     * @throws java.io.IOException                           If there is an error reading the configuration file.
     */
    @Verb(
        name = "assess_api_data", returnType = ArrayNode.class, description = "Accepts api server data based on the" +
        " " +
        "context and seeks" +
        " to perform the assessment of risk by prompting the LLM. Can be used to assess data or request information " +
        "from " +
        "users and/or agents, but not for assessing ztat requests.", requiresTokenManagement = true
    )
    public List<AssessedTerminal> assessData(AgentExecution execution, AgentExecutionContextDTO agentContext)
        throws ZtatException, IOException {
        AgentConfig config = getAgentConfig(execution);

        log.info("Agent config loaded: {}", config);
        List<?> objectList = agentContext.getExecutionArgumentScoped("objectList", List.class)
            .orElseThrow(() -> new RuntimeException("objectList is required to assess data"));

        List<AssessedTerminal> responses = new ArrayList<>();
        log.info("Object list is {}", objectList);
        if (null != objectList) {
            for (var obj : objectList) {
                List<Message> messages = new ArrayList<>();
                var context = config.getContext();

                var userMessage = Message.builder().role("user").content(obj.toString()).build();
                agentContext.addMessages(userMessage);
                messages.add(userMessage);


                LLMRequest chatRequest = LLMRequest.builder().model("gpt-4.1").messages(messages).build();

                var resp = llmService.askQuestion(execution, chatRequest);
                Response response = JsonUtil.MAPPER.readValue(resp, Response.class);
                //log.info("Response is {}", resp);
                for (Response.OutputItem choice : response.getOutputItems()) {
                    var content = choice.getContent().stream().filter(c -> "output_text".equals(c.getType()) || "text".equals(c.getType())).map(c -> c.getText()).findFirst().orElse("");
                    if (content.startsWith("```json")) {
                        content = content.substring(7, content.length() - 3);
                    }


                    responses.add(AssessedTerminal.builder().assessment(JsonUtil.MAPPER.readValue(
                        content,
                        Assessment.class
                    )).messages(messages).build());
                    log.info("content is {}", content);
                }
                log.info("Object is {}", obj);
            }
        } else {
            List<Message> messages = new ArrayList<>();
            var context = config.getContext();


            messages.addAll(agentContext.getMessages());

            var assistantMessage =
                Message.builder().role("assistant").content("Assess the previous data, but respond with the " +
                    "following format { \"assessment\"{ sessionId, risk, description} } where description is your " +
                    "assessment, sessionId is a random UUID or a previously found sessionId, and risk is a measure of" +
                    " low, medium, and high of the previous data").build();
            agentContext.addMessages(assistantMessage);


            LLMRequest chatRequest = LLMRequest.builder().model("gpt-4.1").messages(messages).build();
            agentContext.addMessages(messages);
            var resp = llmService.askQuestion(execution, chatRequest);
            Response response = JsonUtil.MAPPER.readValue(resp, Response.class);
            //log.info("Response is {}", resp);
            for (Response.OutputItem choice : response.getOutputItems()) {
                var content = choice.getContent().stream().filter(c -> "output_text".equals(c.getType()) || "text".equals(c.getType())).map(c -> c.getText()).findFirst().orElse("");
                if (content.startsWith("```json")) {
                    content = content.substring(7, content.length() - 3);
                }


                responses.add(AssessedTerminal.builder().assessment(JsonUtil.MAPPER.readValue(
                    content,
                    Assessment.class
                )).messages(messages).build());
                log.info("content is {}", content);
            }
        }
        return responses;
    }

    @Verb(
        name = "list_ztat_requests", returnType = ArrayNode.class, description = "Lists zero trust access token " +
        "requests (ztats)" +
        " " +
        "to" +
        " review from API. Does not review ztats.",
        requiresTokenManagement = true
    )
    public List<AtatRequest> getWork(AgentExecution token, Map<String, Object> args) throws ZtatException, IOException {
        List<AtatRequest> requests = new ArrayList<>();

        var atatRequests = agentClientService.getAtatRequests(token);
        log.info("Atat requests: {}", atatRequests);
        List<ZtatDTO> dtos = JsonUtil.MAPPER.readValue(
            atatRequests, new TypeReference<>() {
            }
        );

        for (var dto : dtos) {
            Set<String> communicationIds = Sets.newHashSet(dto.getCommunicationIds());
            dto.setCommunicationIds(communicationIds.stream().toList());
            var request = new AtatRequest();
            request.setUserName(dto.getUserName());
            request.setRequestId(dto.getId().toString());
            // get messages
            request.setRequestedAction(dto.getSummary());

            log.info("Request is {}", dto);
            List<Message> communicationMessages = new ArrayList<>();
            for (String commsId : dto.getCommunicationIds()) {
                var communications = zeroTrustClientService.callGetOnApi(
                    token, "/agent/communications/id",
                    Maps.immutableEntry("communicationId", List.of(commsId))
                );
                var messages = JsonUtil.MAPPER.readTree(communications);
                for (JsonNode message : messages) {
                    if (message.has("payload") && message.has("messageType")) {
                        var type = message.get("messageType").asText();

                        if (type.equalsIgnoreCase("chat_request")) {
                            try {
                                LLMRequest msg =
                                    JsonUtil.MAPPER.readValue(message.get("payload").asText(), LLMRequest.class);
                                log.info("Message is {} from {}", msg, message.get("payload").asText());

                                communicationMessages.addAll(msg.getMessages());

                            } catch (JsonProcessingException e) {
                                log.error(e.getMessage());
                            }
                        }
                    }
                }
            }
            request.setMessages(communicationMessages);
            requests.add(request);
        }

        log.info("Requests is {}", requests);

        return requests;
    }

    @Verb(
        name = "assess_ztat_requests", returnType = ArrayNode.class, description = "Analyzes ztats " +
        "requests according to the by prompting the LLM. ",
        requiresTokenManagement = true
    )
    public List<ZtatAsessment> analyzeAtatRequests(AgentExecution execution, List<AtatRequest> requests)
        throws ZtatException,
        IOException, TimeoutException {
        // set up context

        InputStream assessZtatStream = getClass().getClassLoader().getResourceAsStream("assess-ztat.json");
        if (assessZtatStream == null) {
            throw new RuntimeException("assessor-config.yaml not found on classpath");

        }
        String assessZtat = new String(assessZtatStream.readAllBytes());

        AgentConfig config = getAgentConfig(execution);
        log.info("Agent config loaded: {}", config);
        List<ZtatAsessment> responses = new ArrayList<>();
        log.info("Size of requests {}", requests.size());
        for (var request : requests) {
            var originalMessages = request.getMessages().stream().map(message -> {
                message.setRole("user");
                return message;
            }).toList();
            List<Message> messages = new ArrayList<>(originalMessages);
            var context = config.getContext();

            messages.add(Message.builder().role("system").content(context).build());
            messages.add(Message.builder().role("system").content("Ensure your response adheres to the following " +
                "json format. If asking question keep denied as false and approved as false. Only set one or the " +
                "other to true when sure:" + assessZtat).build());
            messages.add(Message.builder().role("system").content(
                "The user's ztat request ID is " + request.getRequestId() + ", and their requested action is " +
                    request.getRequestedAction()).build());
            //messages.addAll(execution.getMessages());


            log.info("Messages is {}", messages);

            LLMRequest chatRequest = LLMRequest.builder().model("gpt-4.1-mini").messages(messages).build();
            var resp = llmService.askQuestion(execution, chatRequest);
            Response response = JsonUtil.MAPPER.readValue(resp, Response.class);
            //log.info("Assess Response is {}", resp);
            List<ZtatAsessment> assessments = new ArrayList<>();
            if (response.getOutputItems().isEmpty()) {
                log.info("No choices in response");
                return responses;
            }
            var choice = response.getOutputItems().get(0);

            var content = choice.getContent().stream().filter(c -> "output_text".equals(c.getType()) || "text".equals(c.getType())).map(c -> c.getText()).findFirst().orElse("");
            if (content.startsWith("```json")) {
                content = content.substring(7, content.length() - 3);
            }
            log.info("content is {}", content);
            var ztat = JsonUtil.MAPPER.readValue(content, ZtatAsessment.class);
            if (ztat.isApproved()) {
                log.info("Ztat is approved");
                zeroTrustClientService.approveZtat(execution, request.getRequestId());
                responses.add(ztat);
            } else if (ztat.isDenied()) {
                break;
            } else {
                // only allow 100 back and forths
                int max = 2;
                do {
                    if (null != ztat.getQuestionToAgent() &&
                        !ztat.getQuestionToAgent().isEmpty()) {
                        log.info("We have a question");
                        // ask a question of the user
                        String payload = JsonUtil.MAPPER.writeValueAsString(ztat);
                        var comm = agentClientService.askAgent(execution, request, payload);
                        log.info("Question is {}", comm);
                        var newComms = agentClientService.getResponse(
                            execution, request, comm, 60,
                            TimeUnit.SECONDS
                        );

                        messages = new ArrayList<>(originalMessages);
                        for (var newComm : newComms) {
                            if (newComm.getMessageType().equalsIgnoreCase("atat_chat_ask")) {
                                var msg = JsonUtil.MAPPER.readValue(newComm.getPayload(), ZtatAsessment.class);
                                var newMessage =
                                    Message.builder().role("assistant").content(msg.getQuestionToAgent()).build();
                                messages.add(newMessage);
                            } else if (newComm.getMessageType().equalsIgnoreCase("atat_chat_response")) {
                                var msg = JsonUtil.MAPPER.readValue(newComm.getPayload(), ZtatResponse.class);
                                var newMessage =
                                    Message.builder().role("user").content(msg.getJustificationToAgent()).build();
                                messages.add(newMessage);
                            }
                        }


                        messages.add(Message.builder().role("system").content(context).build());
                        messages.add(
                            Message.builder().role("system").content("Ensure your response adheres to the following " +
                                "json format:" + assessZtat).build());
                        messages.add(Message.builder().role("system").content(
                            "The user's ztat request ID is " + request.getRequestId() +
                                ", and their requested action is " + request.getRequestedAction()).build());
                        //messages.addAll(execution.getMessages());


                        log.info("Messages is {}", messages);

                        chatRequest = LLMRequest.builder().model("gpt-4.1-mini").messages(messages).build();
                        resp = llmService.askQuestion(execution, chatRequest);
                        response = JsonUtil.MAPPER.readValue(resp, Response.class);
                        if (response.getOutputItems().isEmpty()) {
                            return responses;
                        }
                        choice = response.getOutputItems().get(0);

                        content = choice.getContent().stream().filter(c -> "output_text".equals(c.getType()) || "text".equals(c.getType())).map(c -> c.getText()).findFirst().orElse("");
                        if (content.startsWith("```json")) {
                            content = content.substring(7, content.length() - 3);
                        }
                        log.info("content is {}", content);
                        ztat = JsonUtil.MAPPER.readValue(content, ZtatAsessment.class);
                        if (ztat.isApproved()) {
                            zeroTrustClientService.approveZtat(execution, request.getRequestId());
                            responses.add(ztat);
                            break;
                        }

                    }

                } while (--max > 0);

            }


        }
        return responses;
    }


    private String normalize(String s) {
        if (s == null) return null;
        if (s.startsWith("\"") && s.endsWith("\"")) {
            return s.substring(1, s.length() - 1);
        }
        return s;
    }

    @Verb(
        name = "summarize_agent_status", returnType = AgentExecutionContextDTO.class, description =
        "Summarizes agent status. Used when user asks for agent status.",
        requiresTokenManagement = true
    )
    public JsonNode getAgentExecutionStatus(AgentExecution execution, AgentExecutionContextDTO context)
        throws ZtatException, JsonProcessingException {
        var status = agentExecutionService.getExecutionContextDTO(execution.getExecutionId());

        if (status == null) {
            ObjectNode errorNode = JsonUtil.MAPPER.createObjectNode();
            errorNode.put("error", "No execution context found for this agent");
            return errorNode;
        }

        var lastTen = ListUtils.getLastNElements(status.getMessages(),10);
        var messages = new ArrayList<Message>();

        messages.add(Message.builder().role("system").content("All of the next messages are history between the " +
            "system, assistant, and user" +
            ". Your job is to" +
            " " +
            "summarize them. return { \"summary\" : \"summary text\" }").build());
        messages.addAll(status.getMessages());
        LLMRequest chatRequest = LLMRequest.builder().model("gpt-4.1").messages(messages).build();
        var resp = llmService.askQuestion(execution, chatRequest);
        context.addMessages(messages);
        Response response = JsonUtil.MAPPER.readValue(resp, Response.class);
        //log.info("Response is {}", resp);
        for (Response.OutputItem choice : response.getOutputItems()) {
            var content = choice.getContent().stream().filter(c -> "output_text".equals(c.getType()) || "text".equals(c.getType())).map(c -> c.getText()).findFirst().orElse("");
            if (content.startsWith("```json")) {
                content = content.substring(7, content.length() - 3);
            } else if (content.startsWith("```")) {
                content = content.substring(3, content.length() - 3);
            }
            log.info("content is {}", content);
            if (null != content && !content.isEmpty()) {
                JsonNode node = JsonUtil.MAPPER.enable(JsonParser.Feature.ALLOW_COMMENTS).readTree(content);
                log.info("Node is {}", node);
                if (node.get("summary") != null) {
                    ArrayNode plan = (ArrayNode) node.get("summary");
                    log.info("summary is {}", plan);
                    return plan;
                }
            }
        }

        return JsonUtil.MAPPER.createObjectNode();
    }

    @Verb(
        name = "get_current_agent_status", returnType = ObjectNode.class, description =
        "Queries and summarizes questions against the current agent's memory, history, and context. " +
        "Provides detailed information about agent state including messages, short-term memory, and execution context.",
        requiresTokenManagement = true,
        argName = "query",
        exampleJson = "{ \"query\": \"What tasks has the agent completed?\" }"
    )
    public ObjectNode getCurrentAgentStatus(AgentExecution execution, AgentExecutionContextDTO context)
        throws ZtatException, JsonProcessingException {
        var status = agentExecutionService.getExecutionContextDTO(execution.getExecutionId());
        
        if (status == null) {
            ObjectNode errorNode = JsonUtil.MAPPER.createObjectNode();
            errorNode.put("error", "No execution context found for this agent");
            return errorNode;
        }

        // Get the user's query if provided
        Optional<JsonNode> queryNode = context.getExecutionArgument("query");
        String userQuery = queryNode.map(JsonNode::asText).orElse("Provide a summary of the agent's current status");

        // Build context information
        ObjectNode statusInfo = JsonUtil.MAPPER.createObjectNode();
        statusInfo.put("executionId", execution.getExecutionId());
        statusInfo.put("messageCount", status.getMessages().size());
        statusInfo.put("shortTermMemorySize", status.getAgentShortTermMemory().size());
        statusInfo.put("persistentMemorySize", status.getPersistentMemoryItems().size());
        statusInfo.put("dataListSize", status.getAgentDataList().size());
        
        // Add agent context if available
        if (status.getAgentContext() != null) {
            ObjectNode agentContextInfo = JsonUtil.MAPPER.createObjectNode();
            agentContextInfo.put("name", status.getAgentContext().getName());
            if (status.getAgentContext().getContextId() != null) {
                agentContextInfo.put("contextId", status.getAgentContext().getContextId().toString());
            }
            agentContextInfo.put("description", status.getAgentContext().getDescription());
            statusInfo.set("agentContext", agentContextInfo);
        }

        // Add short-term memory keys
        ArrayNode memoryKeys = JsonUtil.MAPPER.createArrayNode();
        status.getAgentShortTermMemory().keySet().forEach(memoryKeys::add);
        statusInfo.set("memoryKeys", memoryKeys);

        // Add persistent memory keys
        ArrayNode persistentMemoryKeys = JsonUtil.MAPPER.createArrayNode();
        status.getPersistentMemoryItems().keySet().forEach(persistentMemoryKeys::add);
        statusInfo.set("persistentMemoryKeys", persistentMemoryKeys);

        // Prepare messages for LLM query
        List<Message> messages = new ArrayList<>();
        
        messages.add(Message.builder().role("system").content(
            "You are analyzing the current state of an AI agent. " +
            "You have access to the agent's execution history, memory, and context. " +
            "Answer the user's query based on this information. " +
            "Provide a clear, concise response in JSON format with the following structure: " +
            "{ \"answer\": \"your answer\", \"details\": \"additional details if relevant\" }"
        ).build());

        messages.add(Message.builder().role("system").content(
            "Agent Status Information: " + statusInfo.toString()
        ).build());

        // Include recent message history (last 20 messages)
        List<Message> recentMessages = ListUtils.getLastNElements(status.getMessages(), 20);
        if (!recentMessages.isEmpty()) {
            messages.add(Message.builder().role("system").content(
                "Recent message history (last " + recentMessages.size() + " messages):"
            ).build());
            messages.addAll(recentMessages);
        }

        messages.add(Message.builder().role("user").content(userQuery).build());

        // Query LLM
        LLMRequest chatRequest = LLMRequest.builder().model("gpt-4.1").messages(messages).build();
        var resp = llmService.askQuestion(execution, chatRequest);
        context.addMessages(messages);
        Response response = JsonUtil.MAPPER.readValue(resp, Response.class);
        
        for (Response.OutputItem choice : response.getOutputItems()) {
            var content = choice.getContent().stream().filter(c -> "output_text".equals(c.getType()) || "text".equals(c.getType())).map(c -> c.getText()).findFirst().orElse("");
            if (content.startsWith("```json")) {
                content = content.substring(7, content.length() - 3);
            } else if (content.startsWith("```")) {
                content = content.substring(3, content.length() - 3);
            }
            
            log.info("LLM response content: {}", content);
            
            if (null != content && !content.isEmpty()) {
                try {
                    JsonNode responseNode = JsonUtil.MAPPER.enable(JsonParser.Feature.ALLOW_COMMENTS).readTree(content);
                    if (responseNode.isObject()) {
                        ObjectNode result = (ObjectNode) responseNode;
                        result.set("statusInfo", statusInfo);
                        return result;
                    }
                } catch (Exception e) {
                    log.warn("Failed to parse LLM response as JSON, returning as plain text", e);
                    ObjectNode result = JsonUtil.MAPPER.createObjectNode();
                    result.put("answer", content);
                    result.set("statusInfo", statusInfo);
                    return result;
                }
            }
        }

        // Fallback response
        ObjectNode result = JsonUtil.MAPPER.createObjectNode();
        result.put("answer", "Unable to generate a response");
        result.set("statusInfo", statusInfo);
        return result;
    }

    /**
     * Creates an agent with its context, trust policy, and endpoint discovery in a single call.
     * This is the primary method for agent creation, handling context creation, 
     * LLM-driven endpoint discovery, trust policy generation, and agent registration.
     *
     * @param execution The agent execution context containing authentication and execution details
     * @param context The execution context DTO containing agentName, context, and agentType parameters
     * @return ObjectNode containing the created agent's ID and context information
     * @throws ZtatException If there is an error during API communication
     * @throws Exception If there is an error during LLM communication or endpoint discovery
     */
    @Verb(
        name = "create_agent_with_context",
        returnType = ObjectNode.class,
        description = "Creates an agent with context, trust policy, and endpoint discovery in a single call. " +
            "This handles context creation, endpoint discovery via LLM, trust policy generation, and agent creation. " +
            "Agent type can be 'chat' (chat only) or 'chat-autonomous' (chat and autonomous). " +
            "Determine agent type based on whether the workload requires autonomous operation.",
        exampleJson = "{ \"agentName\": \"my-agent\", \"context\": \"Notify when a new user is added\", " +
            "\"agentType\": \"chat-autonomous\" }",
        requiresTokenManagement = true,
        returnName = "created_agent"
    )
    public ObjectNode createAgentWithContext(AgentExecution execution, AgentExecutionContextDTO context)
        throws ZtatException, Exception {
        log.info("Creating agent with context in a single call");

        // Step 1: Extract and validate parameters
        var contextArgs = context.getExecutionArgs();
        if (contextArgs == null || contextArgs.isEmpty()) {
            throw new RuntimeException("Arguments are required to create an agent. Expected: agentName, context, agentType");
        }

        var nameArg = context.getExecutionArgument("agentName");
        String agentName = nameArg.isPresent() ? nameArg.get().asText() : "agent-" + UUID.randomUUID().toString().substring(0, 8);
        if (!agentName.isEmpty()) {
            agentName = agentName.replaceAll("_", "-");
        }

        // Check for both 'context' and 'agentContext' parameter names (LLM may use either)
        var originalContext = context.getExecutionArgument("context");
        if (originalContext.isEmpty()) {
            originalContext = context.getExecutionArgument("agentContext");
        }
        if (originalContext.isEmpty()) {
            throw new RuntimeException("Context is required to create an agent. Please provide a description of what the agent should do. Use parameter name 'context' or 'agentContext'.");
        }

        var agentTypeArg = context.getExecutionArgument("agentType");
        String agentType = agentTypeArg.isPresent() ? agentTypeArg.get().asText() : "chat-autonomous";

        log.info("Creating agent '{}' with type '{}' and context: {}", agentName, agentType, originalContext.get().asText());

        // Step 2: Create the agent context via API
        var requestDtoContext = normalize(originalContext.get().asText());
        requestDtoContext += ". Please request endpoints to perform your work.";
        AgentContextRequestDTO dto = AgentContextRequestDTO.builder()
            .context(requestDtoContext)
            .description(requestDtoContext)
            .name(agentName)
            .build();
        var createdContext = agentClientService.createAgentContext(execution, dto);

        if (createdContext == null || createdContext.getContextId() == null) {
            throw new RuntimeException("Failed to create agent context");
        }

        context.setAgentContext(AgentContextDTO.builder()
            .contextId(createdContext.getContextId())
            .name(createdContext.getName())
            .context(createdContext.getContext())
            .description(createdContext.getDescription())
            .build());

        log.info("Created agent context with ID: {}", createdContext.getContextId());

        // Step 3: Use LLM to discover endpoints based on context
        var messages = new ArrayList<Message>();
        messages.add(Message.builder().role("system").content("The user will provide the context of what an agent to " +
            "be created will do. Respond with a json response { \"endpoints_like\" : [ array ] } where array is the " +
            "features " +
            "or tools to be called. Do not put endpoints in there, just text and explanation of the endpoint. " +
            "We'll perform a text " +
            "search to find" +
            " endpoints").build());
        messages.add(Message.builder().role("user").content(originalContext.get().asText()).build());

        LLMRequest chatRequest = LLMRequest.builder().model("gpt-4.1").messages(messages).build();
        var resp = llmService.askQuestion(execution, chatRequest);

        Response response = JsonUtil.MAPPER.readValue(resp, Response.class);
        log.info("LLM response for endpoint discovery: {}", resp);

        ArrayNode endpointsLikeList = JsonUtil.MAPPER.createArrayNode();
        for (Response.OutputItem choice : response.getOutputItems()) {
            var content = choice.getContent().stream()
                .filter(c -> "output_text".equals(c.getType()) || "text".equals(c.getType()))
                .map(c -> c.getText())
                .findFirst()
                .orElse("");
            if (content.startsWith("```json")) {
                content = content.substring(7, content.length() - 3);
            } else if (content.startsWith("```")) {
                content = content.substring(3, content.length() - 3);
            }

            if (null != content && !content.isEmpty()) {
                var node = JsonUtil.MAPPER.enable(JsonParser.Feature.ALLOW_COMMENTS).readTree(content);

                if (node.get("endpoints_like") == null || !node.get("endpoints_like").isArray()) {
                    log.info("No endpoints_like found in response");
                    continue;
                }
                var arrayNode = (ArrayNode) node.get("endpoints_like");
                for (JsonNode localNode : arrayNode) {
                    if (localNode.isNull() || localNode.asText().isEmpty()) {
                        continue;
                    }
                    if (localNode.has("method") && localNode.has("endpoint")) {
                        if (localNode.get("endpoint").asText().isEmpty() || localNode.get("method").asText().isEmpty()) {
                            log.info("Skipping empty endpoint or method");
                            continue;
                        }
                        endpointsLikeList.add(localNode.asText());
                    } else {
                        // Handle simple text entries (common LLM response format)
                        endpointsLikeList.add(localNode.asText());
                    }
                }
            }
        }

        // Step 4: Discover actual endpoints based on LLM suggestions
        ObjectNode discoveredEndpoints = null;
        if (endpointsLikeList.size() > 0) {
            ObjectNode endpointsLike = JsonUtil.MAPPER.createObjectNode();
            endpointsLike.put("context", originalContext.get().asText());
            endpointsLike.put("endpoints_like", endpointsLikeList);
            context.setExecutionArgs(endpointsLike);
            discoveredEndpoints = getEndpointsLike(execution, context);
            log.info("Discovered endpoints: {}", discoveredEndpoints);
            context.addToMemory("endpoints", discoveredEndpoints);
        }

        // Step 5: Build trust policy from discovered endpoints
        String policyId = "";
        if (discoveredEndpoints != null && discoveredEndpoints.has("endpoints")) {
            var endpoints = discoveredEndpoints.get("endpoints");
            if (endpoints.isArray() && endpoints.size() > 0) {
                var policyBuilder = ATPLPolicy.builder()
                    .version("v0")
                    .description("Policy for agent " + agentName)
                    .policyId(UUID.randomUUID().toString());

                List<Capability> capabilities = new ArrayList<>();
                for (JsonNode endpoint : endpoints) {
                    if (endpoint.has("endpoint") && endpoint.has("name")) {
                        var endpointStr = endpoint.get("endpoint").asText();
                        Capability capability = Capability.builder()
                            .description(endpoint.get("name").asText())
                            .endpoints(List.of(extractNormalizedPath(endpointStr)))
                            .build();
                        capabilities.add(capability);
                    }
                }

                if (!capabilities.isEmpty()) {
                    CapabilitySet capabilitySet = CapabilitySet.builder()
                        .primitives(capabilities)
                        .build();
                    policyBuilder.capabilities(capabilitySet);

                    ATPLPolicy policy = policyBuilder.build();
                    policyId = savePolicy(execution, true, policy);
                    log.info("Created trust policy with ID: {}", policyId);
                }
            }
        }

        // Step 6: Create the agent with context and policy
        var agentBuilder = AgentRegistrationDTO.builder()
            .agentContextId(createdContext.getContextId().toString())
            .clientId(UUID.randomUUID().toString())
            .agentType(agentType)
            .agentName(agentName);

        if (!policyId.isEmpty()) {
            log.info("Using policyId {}", policyId);
            agentBuilder.agentPolicyId(policyId);
        } else {
            log.info("No policy created, using default policy");
        }

        AgentRegistrationDTO agentRegistration = agentBuilder.build();
        var agentResponse = agentClientService.createAgent(execution, agentRegistration);
        log.info("Agent creation response: {}", agentResponse);

        // Step 7: Build and return the result
        ObjectNode resultNode = JsonUtil.MAPPER.createObjectNode();
        resultNode.put("agentId", agentName);
        resultNode.put("agentName", agentName);
        resultNode.put("agentType", agentType);
        resultNode.put("contextId", createdContext.getContextId().toString());
        resultNode.put("policyId", policyId.isEmpty() ? "default" : policyId);
        
        if (discoveredEndpoints != null && discoveredEndpoints.has("endpoints")) {
            resultNode.put("endpointCount", discoveredEndpoints.get("endpoints").size());
        } else {
            resultNode.put("endpointCount", 0);
        }

        log.info("Successfully created agent '{}' with context and trust policy", agentName);
        return resultNode;
    }

    @Verb(name = "get_agent_status", returnType = AgentExecutionContextDTO.class, description = "Queries the agent " +
        "status for other agents. Not to be used internally. Can" +
        " be Running, pending, NotFound, or Failed." ,
        exampleJson = "{ \"agentName\": \"agentName\" }",
        requiresTokenManagement = true )
    public ObjectNode getAgentStatus(AgentExecution execution, AgentExecutionContextDTO agentIdentifier)
        throws ZtatException, JsonProcessingException {
        log.info("Getting agent status");

        var response = agentClientService.getCreatedAgentStatus(execution,agentIdentifier.getAgentContext().getName());
        //log.info("Response is {}", response);
        JsonNode node = JsonUtil.MAPPER.readTree(response);

        ObjectNode contextNode = JsonUtil.MAPPER.createObjectNode();
        contextNode.put("agentId", agentIdentifier.getAgentContext().getName());
        contextNode.put("agentName", agentIdentifier.getAgentContext().getName());
        contextNode.put("status", node.get("status").asText());

        log.info("Agent status is {}", node.get("status").asText());
        return contextNode;
    }



    @Verb(name = "get_endpoints_like", returnType = AgentExecutionContextDTO.class, description = "Queries for endpoints in " +
        "the system that match the input text." ,
        returnName = "endpoints",
        argName = "endpoints_like",
        exampleJson = "[ \"listing users\", \"deleting users\" ]",
         requiresTokenManagement = true )
    public ObjectNode getEndpointsLike(AgentExecution execution,
                                                     AgentExecutionContextDTO executionContextDTO)
        throws ZtatException, Exception {

        var queryInput = executionContextDTO.getExecutionArgs();
        log.info("Querying for endpoints like: {}", queryInput);

        var parsedQuery = queryInput.get("endpoints_like");

        if (null == parsedQuery) {
            throw new IllegalArgumentException("Missing 'endpoints_like' argument. Expected format: " +
                "{ \"endpoints_like\": [\"query text 1\", \"query text 2\"] }");
        }
        
        ObjectNode contextNode = JsonUtil.MAPPER.createObjectNode();
        ArrayNode endpoints = JsonUtil.MAPPER.createArrayNode();
        
        // Handle different input formats from the LLM
        List<String> queryStrings = new ArrayList<>();
        
        if (parsedQuery.isArray()) {
            // Expected format: ["text1", "text2"]
            for (JsonNode node : parsedQuery) {
                String queryText = extractQueryString(node);
                if (queryText != null && !queryText.isEmpty()) {
                    queryStrings.add(queryText);
                }
            }
        } else if (parsedQuery.isObject()) {
            // Handle nested object format: {"endpoints_like": ["text1", "text2"]} or {"arg1": "text"}
            // First check if this object contains an array (common when VerbRegistry wraps arguments)
            Iterator<Map.Entry<String, JsonNode>> fields = parsedQuery.fields();
            boolean foundArray = false;
            while (fields.hasNext()) {
                Map.Entry<String, JsonNode> entry = fields.next();
                JsonNode value = entry.getValue();
                if (value.isArray()) {
                    // Extract strings from the array
                    for (JsonNode arrayElement : value) {
                        String queryText = extractQueryString(arrayElement);
                        if (queryText != null && !queryText.isEmpty()) {
                            queryStrings.add(queryText);
                        }
                    }
                    foundArray = true;
                    break;
                }
            }
            
            // If no array found, try to extract a single string
            if (!foundArray) {
                String queryText = extractQueryString(parsedQuery);
                if (queryText != null && !queryText.isEmpty()) {
                    queryStrings.add(queryText);
                }
            }
        } else if (parsedQuery.isTextual()) {
            // Simple string format
            queryStrings.add(parsedQuery.asText());
        }
        
        if (queryStrings.isEmpty()) {
            throw new IllegalArgumentException("Could not extract any valid query strings from 'endpoints_like'. " +
                "Expected format: { \"endpoints_like\": [\"query text 1\", \"query text 2\"] }. " +
                "Received: " + parsedQuery.toString());
        }
        
        // Query endpoints for each search string
        for (String queryText : queryStrings) {
            log.info("Searching endpoints for: {}", queryText);
            var endpointList = endpointSearcher.getEndpointsLike(execution, queryText);
            for (EndpointDescriptor endpoint : endpointList) {
                ObjectNode endpointNode = JsonUtil.MAPPER.createObjectNode();
                endpointNode.put("name", endpoint.getName());
                endpointNode.put("description", endpoint.getDescription());
                endpointNode.put("method", endpoint.getHttpMethod());

                // Include serviceUrl if available - this is crucial for routing to integration proxy
                String serviceUrl = endpoint.getServiceUrl();
                if (serviceUrl != null && !serviceUrl.isEmpty()) {
                    // Combine serviceUrl with path to give the LLM the complete URL
                    String fullUrl = serviceUrl;
                    if (!fullUrl.endsWith("/") && endpoint.getPath() != null && !endpoint.getPath().startsWith("/")) {
                        fullUrl += "/";
                    }
                    fullUrl += endpoint.getPath();
                    endpointNode.put("endpoint", fullUrl);
                    endpointNode.put("serviceUrl", serviceUrl); // Also include separately for reference
                } else {
                    // No serviceUrl, just use the path (will be called against current API server)
                    endpointNode.put("endpoint", endpoint.getPath());
                }

                endpointNode.put("searchQuery", queryText);
                endpoints.add(endpointNode);
            }
        }
        
        contextNode.put("endpoints", endpoints);

        return contextNode;
    }
    
    /**
     * Recursively extracts a query string from a JsonNode, handling various nested structures.
     * Tries common patterns like {"arg1": "text"}, {"field": "text"}, {"context": "text"}, etc.
     */
    private String extractQueryString(JsonNode node) {
        if (node == null) {
            return null;
        }
        
        if (node.isTextual()) {
            return node.asText();
        }
        
        if (node.isObject()) {
            // Try common field names that might contain the query
            for (String fieldName : QUERY_FIELD_NAMES) {
                if (node.has(fieldName)) {
                    log.debug("Extracting query string from field: {}", fieldName);
                    return extractQueryString(node.get(fieldName));
                }
            }
            
            // If no known fields, try the first field as fallback
            var fields = node.fields();
            if (fields.hasNext()) {
                var entry = fields.next();
                log.warn("No recognized query field found in object, using first field: {}", entry.getKey());
                return extractQueryString(entry.getValue());
            }
        }
        
        return null;
    }

    @Verb(name = "call_endpoint",
        returnType = AgentExecutionContextDTO.class,
        description = "Executes an endpoint at the service. " +
            "Supports both query parameters and path parameters (URL templates). " +
            "For URLs with path parameters like '/repos/{owner}/{repo}/issues', provide values in params object. " +
            "Path parameters will be substituted into the URL, remaining params become query parameters.",
        exampleJson = "{ \"endpoint\": \"/repos/{owner}/{repo}/issues\", \"method\": \"GET\", " +
            "\"params\": { \"owner\": \"myorg\", \"repo\": \"myrepo\", \"state\": \"open\" } }",
        argName = "endpointToCall",
        requiresTokenManagement = true )
    public ObjectNode callEndpoint(AgentExecution execution, AgentExecutionContextDTO queryInput)
        throws ZtatException, JsonProcessingException {
        log.info("Querying for endpoint with input: {}", queryInput);
        ObjectNode contextNode = JsonUtil.MAPPER.createObjectNode();

        var method = queryInput.getSafeLabel("endpointToCall","method");
        var endpoint = queryInput.getLabel("endpointToCall","endpoint");
        var serverUrl = "";

        if (endpoint.startsWith("http")) {
            int index = endpoint.indexOf("://");
            int pathStart = endpoint.indexOf("/", index + 3); // first slash after the host:port

            if (pathStart != -1) {
                serverUrl = endpoint.substring(0, pathStart + 1); // include the trailing slash
                endpoint = endpoint.substring(pathStart);         // keep leading slash
            } else {
                // no path — treat full URL as base
                serverUrl = endpoint.endsWith("/") ? endpoint : endpoint + "/";
                endpoint = "/";
            }
        }
        endpoint = endpoint.replace("//","/");
        var paramsNode = queryInput.getExecutionArgument("endpointToCall", "params");

        List<Map.Entry<String, List<String>>> entries = new ArrayList<>();
        Map<String, String> pathParams = new HashMap<>();

        if (paramsNode.isPresent() && paramsNode.get().isObject()) {
            ObjectNode paramObject = (ObjectNode) paramsNode.get();
            Iterator<Map.Entry<String, JsonNode>> fields = paramObject.fields();

            while (fields.hasNext()) {
                Map.Entry<String, JsonNode> field = fields.next();
                String key = field.getKey();
                JsonNode valueNode = field.getValue();

                List<String> valueList = new ArrayList<>();
                if (valueNode.isArray()) {
                    for (JsonNode item : valueNode) {
                        valueList.add(item.asText());
                    }
                } else if (valueNode.isTextual()) {
                    valueList.add(valueNode.asText());
                } else if (!valueNode.isNull()) {
                    valueList.add(valueNode.toString());
                }

                // Check if this parameter is a path variable (used in URL template)
                String placeholder = "{" + key + "}";
                if (endpoint.contains(placeholder)) {
                    // This is a path parameter - store for URL substitution
                    pathParams.put(key, valueList.isEmpty() ? "" : valueList.get(0));
                } else {
                    // This is a query parameter - add to entries
                    entries.add(new AbstractMap.SimpleEntry<>(key, valueList));
                }
            }
        }

        // Substitute path parameters in the endpoint URL
        for (Map.Entry<String, String> pathParam : pathParams.entrySet()) {
            String placeholder = "{" + pathParam.getKey() + "}";
            endpoint = endpoint.replace(placeholder, pathParam.getValue());
        }

        // Check if there are still unresolved placeholders
        if (endpoint.contains("{") && endpoint.contains("}")) {
            log.warn("Endpoint still contains unresolved path parameters: {}", endpoint);
            throw new IllegalArgumentException(
                "Endpoint URL contains unresolved path parameters: " + endpoint +
                ". Please provide values for all path parameters in the 'params' object."
            );
        }

// Determine params and payload for POST
        Map.Entry<String, List<String>>[] paramArray = entries.toArray(new Map.Entry[0]);
        JsonNode postPayload = paramsNode.orElse(JsonUtil.MAPPER.createObjectNode());

        String response;

        if ("GET".equalsIgnoreCase(method)) {
            if (entries.isEmpty()) {
                response = serverUrl.isEmpty()
                    ? zeroTrustClientService.callGetOnApi(execution, endpoint)
                    : zeroTrustClientService.callGetOnApi(execution, serverUrl, endpoint, null);
            } else {
                Map.Entry<String, List<String>> first = entries.get(0);
                Map.Entry<String, List<String>>[] rest = entries.size() > 1
                    ? entries.subList(1, entries.size()).toArray(new Map.Entry[0])
                    : new Map.Entry[0];

                response = serverUrl.isEmpty()
                    ? zeroTrustClientService.callGetOnApi(execution, endpoint, first, rest)
                    : zeroTrustClientService.callGetOnApi(execution, serverUrl, endpoint, first, rest);
            }
        } else if ("POST".equalsIgnoreCase(method)) {

            response = serverUrl.isEmpty()
                ? zeroTrustClientService.callPostOnApi(execution, endpoint, postPayload, paramArray)
                : zeroTrustClientService.callPostOnApi(execution, serverUrl, endpoint, postPayload, paramArray);


        } else {
            throw new IllegalArgumentException("Unsupported method: " + method);
        }

        if (!isHtml(response)) {


            queryInput.addMessages(
                Message.builder().role("system").content("response from endpoint call: " + response).build());

            contextNode.put("response", response);
        } else {
            throw new RuntimeException("Received HTML response, likely an error page from " + endpoint);
        }
        return contextNode;
    }

    private boolean isHtml(String response){
        return response != null && (response.trim().startsWith("<!DOCTYPE html>") || response.trim().startsWith("<html"));
    }

    public String savePolicy(TokenDTO token, boolean includeDefault, ATPLPolicy policy) throws ZtatException {
        try {

            log.info("policy is : {}", policy);
            Map.Entry<String, List<String>> param = Maps.immutableEntry("includeDefault", List.of(String.valueOf(includeDefault)));
            String response = zeroTrustClientService.callPostOnApi(token,"/api/v1/policies", policy, param);
            if (response == null) {
                throw new RuntimeException("Failed to retrieve terminal list");
            }
            log.info("PolicyId: {}", response);
            return response;
        } catch (Exception e) {
            e.printStackTrace();
            throw new RuntimeException("Failed to retrieve terminal list", e);
        }
    }

    public String extractNormalizedPath(String urlOrPath) {
        try {
            URI uri = URI.create(urlOrPath);
            String path = uri.getPath();
            return path != null ? path.replaceAll("/{2,}", "/") : "/";
        } catch (IllegalArgumentException e) {
            // If it’s not a valid URI (unlikely), fallback to manual
            return urlOrPath.replaceAll("^(https?:)?//[^/]+", "")  // strip domain
                .replaceAll("/{2,}", "/");             // normalize slashes
        }
    }

    /**
     * Lookup agent memories using text-based search with optional filters.
     * Provides access to agent memory history for context in plan execution.
     * 
     * @param execution The agent execution context
     * @param executionContextDTO The execution context with query parameters
     * @return ObjectNode containing array of matched memories
     * @throws ZtatException If there is an error during the operation
     */
    @Verb(
        name = "lookup_agent_memory",
        returnName = "memories",
        returnType = ObjectNode.class,
        description = "Searches agent memories. Do not use this directly, always populate memoryLookup in response " +
            "instead.",
        requiresTokenManagement = true,
        argName = "memory_query",
        exampleJson = "{ \"query\": \"user name\", \"agentId\": \"my-agent\", \"markings\": \"PUBLIC\", \"limit\": 10 }",
        skipMemoryStorage = true
    )
    public ObjectNode lookupAgentMemory(AgentExecution execution, AgentExecutionContextDTO executionContextDTO)
        throws ZtatException, JsonProcessingException {
        
        log.info("Looking up agent memories");
        
        // Extract query parameters
        String query = "";
        String agentId = null;
        String markings = "";
        int limit = 10;
        
        Optional<JsonNode> queryNode = executionContextDTO.getExecutionArgument("memory_query", "query");
        query = queryNode
            .filter(node -> !node.isNull())  // Filter out null JsonNodes
            .map(JsonNode::asText)
            .filter(s -> s != null && !s.isBlank() && !"null".equals(s))
            .orElse("");
        log.debug("Query parameter extracted: '{}'", query);
        
        Optional<JsonNode> agentIdNode = executionContextDTO.getExecutionArgument("memory_query", "agentId");
        agentId = agentIdNode
            .filter(node -> !node.isNull())
            .map(JsonNode::asText)
            .filter(s -> s != null && !s.isBlank() && !"null".equals(s))
            .orElse(null);
        log.debug("AgentId parameter extracted: {}", agentId);
        
        Optional<JsonNode> markingsNode = executionContextDTO.getExecutionArgument("memory_query", "markings");
        markings = markingsNode
            .filter(node -> !node.isNull())
            .map(JsonNode::asText)
            .filter(s -> s != null && !s.isBlank() && !"null".equals(s))
            .orElse("");
        log.debug("Markings parameter extracted: '{}'", markings);
        
        Optional<JsonNode> limitNode = executionContextDTO.getExecutionArgument("memory_query", "limit");
        limit = limitNode.map(JsonNode::asInt).orElse(10);
        log.debug("Limit parameter extracted: {}", limit);
        
        // If query is still empty, try to infer from recent conversation messages
        if (query.isEmpty()) {
            log.debug("Query parameter not provided, attempting to infer from recent messages");
            List<Message> messages = executionContextDTO.getMessages();
            if (messages != null && !messages.isEmpty()) {
                // Get the most recent user message (check last 3 messages)
                int lowerBound = Math.max(0, messages.size() - 3);
                for (int i = messages.size() - 1; i >= lowerBound; i--) {
                    Message msg = messages.get(i);
                    String contentStr = msg.getContentAsString();
                    if ("user".equals(msg.getRole()) && contentStr != null && !contentStr.isBlank()) {
                        query = contentStr;
                        log.info("Inferred query from recent user message: '{}'", query);
                        break;
                    }
                }
            }
        }
        
        if (query.isEmpty()) {
            log.error("Query parameter is required for memory lookup but was not provided and could not be inferred");
            throw new IllegalArgumentException("Query parameter is required for memory lookup. Please provide a search query.");
        }
        
        // Include private user conversations by adding USER:<userId> marking
        // This allows agents to search both PUBLIC and user-specific PRIVATE conversations
        String effectiveMarkings = markings;
        if (execution.getUser() != null && execution.getUser().getUserId() != null) {
            String userMarking = "USER:" + execution.getUser().getUserId();
            if (markings == null || markings.isEmpty()) {
                // If no markings specified, search both PUBLIC and user-specific private conversations
                effectiveMarkings = "PUBLIC," + userMarking;
            } else if (!markings.contains("USER:")) {
                // If markings specified but don't include USER marking, append it
                effectiveMarkings = markings + "," + userMarking;
            }
        }
        
        log.info("Memory lookup - query: '{}', agentId: {}, markings: {}, effectiveMarkings: {}, limit: {}", 
            query, agentId, markings, effectiveMarkings, limit);
        
        // Call the memory API endpoint
        List<Map.Entry<String, List<String>>> params = new ArrayList<>();
        if (agentId != null && !agentId.isEmpty()) {
            params.add(Maps.immutableEntry("agent", List.of(agentId)));
        }
        params.add(Maps.immutableEntry("content", List.of(query)));
        params.add(Maps.immutableEntry("size", List.of(String.valueOf(limit))));
        if (effectiveMarkings != null && !effectiveMarkings.isEmpty()) {
            params.add(Maps.immutableEntry("markings", List.of(effectiveMarkings)));
        }
        
        String response;
        if (params.isEmpty()) {
            response = zeroTrustClientService.callGetOnApi(execution, "/api/v1/agents/memory/search");
        } else {
            Map.Entry<String, List<String>> first = params.get(0);
            Map.Entry<String, List<String>>[] rest = params.size() > 1
                ? params.subList(1, params.size()).toArray(new Map.Entry[0])
                : new Map.Entry[0];
            response = zeroTrustClientService.callGetOnApi(execution, "/api/v1/agents/memory/search", first, rest);
        }
        
        if (isHtml(response)) {
            throw new RuntimeException("Received HTML response from memory search endpoint");
        }
        //log.info("Memory search response: {}", response);
        // Parse the response
        JsonNode responseNode = JsonUtil.MAPPER.readTree(response);
        
        ObjectNode result = JsonUtil.MAPPER.createObjectNode();
        result.put("query", query);
        result.put("count", responseNode.path("totalElements").asInt(0));
        
        // Extract and format memories
        ArrayNode memories = JsonUtil.MAPPER.createArrayNode();
        JsonNode content = responseNode.path("content");
        
        if (content.isArray()) {
            for (JsonNode memoryNode : content) {
                ObjectNode memory = JsonUtil.MAPPER.createObjectNode();
                memory.put("memoryKey", memoryNode.path("memoryKey").asText());
                memory.put("memoryValue", memoryNode.path("memoryValue").asText());
                memory.put("agentId", memoryNode.path("agentId").asText());
                memory.put("classification", memoryNode.path("classification").asText());
                memory.put("createdAt", memoryNode.path("createdAt").asText());
                memories.add(memory);
            }
        }
        
        result.set("memories", memories);
        
        log.info("Found {} memories for query: '{}'", memories.size(), query);
        
        return result;
    }

    /**
     * Search agent memories using semantic vector similarity.
     * Provides advanced memory lookup using embeddings for semantic matching.
     * 
     * @param execution The agent execution context
     * @param executionContextDTO The execution context with query parameters
     * @return ObjectNode containing array of semantically similar memories
     * @throws ZtatException If there is an error during the operation
     */
    @Verb(
        name = "search_agent_memory_semantic",
        returnType = ObjectNode.class,
        description = "Searches agent memories using semantic vector similarity. " +
            "Use this to find information related to concepts, even if the exact words don't match. " +
            "For example, searching for 'user preferences' can find memories about 'settings', 'configuration', or 'favorites'. " +
            "Useful when you need to recall context but aren't sure of the exact terms used. " +
            "Always prefer this over lookup_agent_memory when searching for conceptual information across sessions.",
        requiresTokenManagement = true,
        argName = "semantic_query",
        exampleJson = "{ \"query\": \"user personal information\", \"agentId\": \"my-agent\", \"limit\": 5, \"threshold\": 0.75 }",
        skipMemoryStorage = true
    )
    public ObjectNode searchAgentMemorySemantic(AgentExecution execution, AgentExecutionContextDTO executionContextDTO)
        throws ZtatException, JsonProcessingException {
        
        log.info("Semantic search for agent memories");
        
        // Extract query parameters - handle Optional.of() that throws on null
        String query = "";
        String agentId = null;
        int limit = 10;
        double threshold = 0.7;
        
        try {
            Optional<JsonNode> queryNode = executionContextDTO.getExecutionArgument("semantic_query", "query");
            query = queryNode.map(JsonNode::asText).orElse("");
        } catch (NullPointerException e) {
            log.debug("Query parameter not found or null");
        }
        
        try {
            Optional<JsonNode> agentIdNode = executionContextDTO.getExecutionArgument("semantic_query", "agentId");
            agentId = agentIdNode.map(JsonNode::asText).orElse(null);
        } catch (NullPointerException e) {
            log.debug("AgentId parameter not found or null");
        }
        
        try {
            Optional<JsonNode> limitNode = executionContextDTO.getExecutionArgument("semantic_query", "limit");
            limit = limitNode.map(JsonNode::asInt).orElse(10);
        } catch (NullPointerException e) {
            log.debug("Limit parameter not found or null, using default");
        }
        
        try {
            Optional<JsonNode> thresholdNode = executionContextDTO.getExecutionArgument("semantic_query", "threshold");
            threshold = thresholdNode.map(JsonNode::asDouble).orElse(0.7);
        } catch (NullPointerException e) {
            log.debug("Threshold parameter not found or null, using default");
        }
        
        if (query.isEmpty()) {
            throw new IllegalArgumentException("Query parameter is required for semantic memory search");
        }
        
        log.info("Semantic memory search - query: '{}', agentId: {}, limit: {}, threshold: {}", 
            query, agentId, limit, threshold);
        
        // Build the API endpoint path
        String endpoint = agentId != null && !agentId.isEmpty()
            ? "/api/v1/agents/memory/search/semantic/" + agentId
            : "/api/v1/agents/memory/search/semantic";
        
        // Build request body
        ObjectNode requestBody = JsonUtil.MAPPER.createObjectNode();
        requestBody.put("query", query);
        requestBody.put("limit", limit);
        requestBody.put("threshold", threshold);
        
        String response = zeroTrustClientService.callPostOnApi(
            execution,
            endpoint,
            requestBody
        );
        
        if (isHtml(response)) {
            throw new RuntimeException("Received HTML response from semantic memory search endpoint");
        }
        
        // Parse the response
        JsonNode responseArray = JsonUtil.MAPPER.readTree(response);
        
        ObjectNode result = JsonUtil.MAPPER.createObjectNode();
        result.put("query", query);
        result.put("searchType", "semantic");
        result.put("threshold", threshold);
        result.put("count", responseArray.size());
        
        // Extract and format memories
        ArrayNode memories = JsonUtil.MAPPER.createArrayNode();
        
        if (responseArray.isArray()) {
            for (JsonNode memoryNode : responseArray) {
                ObjectNode memory = JsonUtil.MAPPER.createObjectNode();
                memory.put("memoryKey", memoryNode.path("memoryKey").asText());
                memory.put("memoryValue", memoryNode.path("memoryValue").asText());
                memory.put("agentId", memoryNode.path("agentId").asText());
                memory.put("agentName", memoryNode.path("agentName").asText());
                memory.put("classification", memoryNode.path("classification").asText());
                memory.put("createdAt", memoryNode.path("createdAt").asText());
                memory.put("hasEmbedding", memoryNode.path("hasEmbedding").asBoolean());
                memories.add(memory);
            }
        }
        
        result.set("memories", memories);
        
        log.info("Found {} semantically similar memories for query: '{}'", memories.size(), query);
        
        return result;
}
    /**
     * Search for verbs by keywords without loading all verbs into context.
     * This enables efficient verb discovery at scale.
     *
     * @param contextDTO The execution context containing 'keywords' and optional 'maxResults'
     * @return JSON with matching verb descriptors
     */
    @Verb(
        name = "search_verbs",
        description = "Search for verbs by keywords in name or description. " +
                     "Requires 'keywords' parameter. Optional: 'maxResults' (default: 10).",
        returnType = ObjectNode.class,
        returnName = "verb_search_results",
        argName = "search_params",
        exampleJson = "{\"keywords\": \"slack send message\", \"maxResults\": 5}",
        isAiCallable = true,
        requiresTokenManagement = false,
        skipMemoryStorage = true
    )
    public ObjectNode searchVerbs(AgentExecutionContextDTO contextDTO) {
        String keywords = contextDTO.getExecutionArgumentScoped("keywords", String.class)
            .orElseThrow(() -> new IllegalArgumentException("keywords parameter is required"));
        int maxResults = contextDTO.getExecutionArgumentScoped("maxResults", Integer.class)
            .orElse(10);

        log.info("Searching for verbs with keywords: '{}', maxResults: {}", keywords, maxResults);

        List<VerbLookupService.VerbDescriptor> results = verbLookupService.searchVerbs(keywords, maxResults);

        ObjectNode response = JsonUtil.MAPPER.createObjectNode();
        response.put("query", keywords);
        response.put("found", results.size());

        ArrayNode verbsArray = response.putArray("verbs");
        results.forEach(verb -> {
            ObjectNode verbNode = verbsArray.addObject();
            verbNode.put("name", verb.getName());
            verbNode.put("description", verb.getDescription());
            verbNode.put("category", verb.getCategory());
            if (verb.getExampleJson() != null && !verb.getExampleJson().isEmpty()) {
                verbNode.put("exampleJson", verb.getExampleJson());
            }
        });

        log.info("Verb search completed. Found {} matching verbs", results.size());
        return response;
    }

    /**
     * Get verbs by category (e.g., slack, k8s, mcp).
     * This allows agents to explore related verbs together.
     *
     * @param contextDTO The execution context containing 'category'
     * @return JSON with verbs in that category
     */
    @Verb(
        name = "get_verbs_by_category",
        description = "Get all verbs in a specific category. " +
                     "Requires 'category' parameter (e.g., 'slack', 'k8s', 'llm', 'mcp').",
        returnType = ObjectNode.class,
        returnName = "category_verbs",
        argName = "category_param",
        exampleJson = "{\"category\": \"slack\"}",
        isAiCallable = true,
        requiresTokenManagement = false,
        skipMemoryStorage = true
    )
    public ObjectNode getVerbsByCategory(AgentExecutionContextDTO contextDTO) {
        String category = contextDTO.getExecutionArgumentScoped("category", String.class)
            .orElseThrow(() -> new IllegalArgumentException("category parameter is required"));

        log.info("Getting verbs in category: {}", category);

        List<VerbLookupService.VerbDescriptor> results = verbLookupService.getVerbsByCategory(category);

        ObjectNode response = JsonUtil.MAPPER.createObjectNode();
        response.put("category", category);
        response.put("count", results.size());

        ArrayNode verbsArray = response.putArray("verbs");
        results.forEach(verb -> {
            ObjectNode verbNode = verbsArray.addObject();
            verbNode.put("name", verb.getName());
            verbNode.put("description", verb.getDescription());
        });

        log.info("Found {} verbs in category '{}'", results.size(), category);
        return response;
    }

    /**
     * Get a summary of all available verb categories.
     * This provides a high-level view without loading all verb details.
     *
     * @param contextDTO The execution context
     * @return JSON summary of verb categories
     */
    @Verb(
        name = "get_verb_summary",
        description = "Get a summary of all available verb categories and counts. " +
                     "This provides an overview of what verbs are available without loading all details.",
        returnType = JsonNode.class,
        returnName = "verb_summary",
        isAiCallable = true,
        requiresTokenManagement = false,
        skipMemoryStorage = true
    )
    public JsonNode getVerbSummary(AgentExecutionContextDTO contextDTO) {
        log.info("Getting verb summary");
        JsonNode summary = verbLookupService.getVerbSummary();
        log.info("Verb summary retrieved successfully");
        return summary;
    }

    /**
     * Get detailed information about a specific verb.
     * Use this after finding a verb via search to get full details before calling it.
     *
     * @param contextDTO The execution context containing 'verbName'
     * @return JSON with verb details
     */
    @Verb(
        name = "get_verb_details",
        description = "Get detailed information about a specific verb. " +
                     "Requires 'verbName' parameter. Use this after finding a verb via search.",
        returnType = ObjectNode.class,
        returnName = "verb_details",
        argName = "verb_param",
        exampleJson = "{\"verbName\": \"send_slack_message\"}",
        isAiCallable = true,
        requiresTokenManagement = false,
        skipMemoryStorage = true
    )
    public ObjectNode getVerbDetails(AgentExecutionContextDTO contextDTO) {
        String verbName = contextDTO.getExecutionArgumentScoped("verbName", String.class)
            .orElseThrow(() -> new IllegalArgumentException("verbName parameter is required"));

        log.info("Getting details for verb: {}", verbName);

        VerbLookupService.VerbDescriptor verb = verbLookupService.getVerbDetails(verbName);
        if (verb == null) {
            throw new IllegalArgumentException("Verb not found: " + verbName);
        }

        ObjectNode response = JsonUtil.MAPPER.createObjectNode();
        response.put("name", verb.getName());
        response.put("description", verb.getDescription());
        response.put("category", verb.getCategory());
        response.put("argName", verb.getArgName());
        response.put("returnName", verb.getReturnName());
        response.put("returnType", verb.getReturnType());
        response.put("requiresTokenManagement", verb.isRequiresTokenManagement());
        
        if (verb.getExampleJson() != null && !verb.getExampleJson().isEmpty()) {
            response.put("exampleJson", verb.getExampleJson());
        }

        log.info("Retrieved details for verb: {}", verbName);
        return response;
    }

    /**
     * Find verbs by describing what you want to do (intent-based search).
     * This is similar to search_verbs but optimized for natural language queries.
     *
     * @param contextDTO The execution context containing 'intent' description
     * @return JSON with matching verbs
     */
    @Verb(
        name = "find_verbs_by_intent",
        description = "Find verbs by describing what you want to do in natural language. " +
                     "Requires 'intent' parameter. Optional: 'maxResults' (default: 10). " +
                     "Example: 'I want to send a message to Slack'",
        returnType = ObjectNode.class,
        returnName = "intent_results",
        argName = "intent_params",
        exampleJson = "{\"intent\": \"send a message to Slack\", \"maxResults\": 5}",
        isAiCallable = true,
        requiresTokenManagement = false,
        skipMemoryStorage = true
    )
    public ObjectNode findVerbsByIntent(AgentExecutionContextDTO contextDTO) {
        String intent = contextDTO.getExecutionArgumentScoped("intent", String.class)
            .orElseThrow(() -> new IllegalArgumentException("intent parameter is required"));
        int maxResults = contextDTO.getExecutionArgumentScoped("maxResults", Integer.class)
            .orElse(10);

        log.info("Finding verbs by intent: '{}', maxResults: {}", intent, maxResults);

        List<VerbLookupService.VerbDescriptor> results = verbLookupService.findVerbsByIntent(intent, maxResults);

        ObjectNode response = JsonUtil.MAPPER.createObjectNode();
        response.put("intent", intent);
        response.put("found", results.size());

        ArrayNode verbsArray = response.putArray("verbs");
        results.forEach(verb -> {
            ObjectNode verbNode = verbsArray.addObject();
            verbNode.put("name", verb.getName());
            verbNode.put("description", verb.getDescription());
            verbNode.put("category", verb.getCategory());
            verbNode.put("compactSummary", verb.toCompactString());
        });

        log.info("Intent-based verb search completed. Found {} matching verbs", results.size());
        return response;
    }
}
