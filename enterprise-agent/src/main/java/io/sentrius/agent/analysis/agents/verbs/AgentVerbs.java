package io.sentrius.agent.analysis.agents.verbs;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.util.AbstractMap;
import java.util.ArrayList;
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
        AgentClientService agentService, EndpointRegistry endpointRegistry, EndpointSearcher endpointSearcher,
        AgentExecutionService agentExecutionService
    ) throws JsonProcessingException {
        super(agentConfigFile, agentDatabaseContext, agentService);
        this.zeroTrustClientService = zeroTrustClientService;
        this.llmService = llmService;
        this.verbRegistry = verbRegistry;
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

        LLMRequest chatRequest = LLMRequest.builder().model("gpt-4o-mini").messages(messages).build();
        var resp = llmService.askQuestion(execution, chatRequest);
        if (null != context ) {
            context.addMessages(messages);
        }
        Response response = JsonUtil.MAPPER.readValue(resp, Response.class);
        //log.info("Response is {}", resp);
        for (Response.Choice choice : response.getChoices()) {
            var content = choice.getMessage().getContentAsString();
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

                LLMRequest chatRequest = LLMRequest.builder().model("gpt-4o-mini").messages(messages).build();
                context.addMessages(messages);
                var resp = llmService.askQuestion(execution, chatRequest);
                Response response = JsonUtil.MAPPER.readValue(resp, Response.class);
                //log.info("Response is {}", resp);
                for (Response.Choice choice : response.getChoices()) {
                    var content = choice.getMessage().getContentAsString();
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


                LLMRequest chatRequest = LLMRequest.builder().model("gpt-4o-mini").messages(messages).build();

                var resp = llmService.askQuestion(execution, chatRequest);
                Response response = JsonUtil.MAPPER.readValue(resp, Response.class);
                //log.info("Response is {}", resp);
                for (Response.Choice choice : response.getChoices()) {
                    var content = choice.getMessage().getContentAsString();
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


            LLMRequest chatRequest = LLMRequest.builder().model("gpt-4o-mini").messages(messages).build();
            agentContext.addMessages(messages);
            var resp = llmService.askQuestion(execution, chatRequest);
            Response response = JsonUtil.MAPPER.readValue(resp, Response.class);
            //log.info("Response is {}", resp);
            for (Response.Choice choice : response.getChoices()) {
                var content = choice.getMessage().getContentAsString();
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

            LLMRequest chatRequest = LLMRequest.builder().model("gpt-4o-mini-mini").messages(messages).build();
            var resp = llmService.askQuestion(execution, chatRequest);
            Response response = JsonUtil.MAPPER.readValue(resp, Response.class);
            //log.info("Assess Response is {}", resp);
            List<ZtatAsessment> assessments = new ArrayList<>();
            if (response.getChoices().isEmpty()) {
                log.info("No choices in response");
                return responses;
            }
            var choice = response.getChoices().get(0);

            var content = choice.getMessage().getContentAsString();
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

                        chatRequest = LLMRequest.builder().model("gpt-4o-mini-mini").messages(messages).build();
                        resp = llmService.askQuestion(execution, chatRequest);
                        response = JsonUtil.MAPPER.readValue(resp, Response.class);
                        if (response.getChoices().isEmpty()) {
                            return responses;
                        }
                        choice = response.getChoices().get(0);

                        content = choice.getMessage().getContentAsString();
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


    @Verb(
        name = "create_agent_context", returnType = AgentContextDTO.class, description = "Creates an agent Context." +
        " must be done before creating an agent.",
        requiresTokenManagement = true,
        returnName = "created_context",
        exampleJson = "{ \"context\": \"Notify when a new user is added\" }"
    )
    public AgentContextDTO createAgentContext(AgentExecution execution, AgentExecutionContextDTO context)
        throws ZtatException, Exception {
        log.info("Creating agent context");
        var contextArgs = context.getExecutionArgs();
        if (contextArgs == null || contextArgs.isEmpty()) {
            throw new RuntimeException("Context is required to create an agent context");
        }
        var name = context.getExecutionArgument("agentName");

        String agentName = name.isPresent() ? name.get().toString() : "name";
        if (!agentName.isEmpty()) {
            agentName = agentName.replaceAll("_", "-");
        }

        var originalContext = context.getExecutionArgument("context");

        var requestDtoContext = originalContext.orElseThrow().toString();
        requestDtoContext += ". Please request endpoints to perform your work.";
        AgentContextRequestDTO dto = AgentContextRequestDTO.builder().context(requestDtoContext).
            description(requestDtoContext).name(agentName).build();
        var createdContext = agentClientService.createAgentContext(execution, dto);
        // Here you would typically create a context in your system, e.g., store it in a database or cache.

        context.setAgentContext(AgentContextDTO.builder()
            .contextId(createdContext.getContextId())
            .name(createdContext.getName())
            .context(createdContext.getContext())
            .description(createdContext.getDescription())
            .build());

        // load the endpoints
        var messages = new ArrayList<Message>();

        messages.add(Message.builder().role("system").content("The user will provide the context of what an agent to " +
            "be created will do. Respond with a json response { \"endpoints_like\" : [ array ] } where array is the " +
            "features " +
                "or tools to be called. Do not put endpoints in there, just text and explanation of the endpoint. " +
            "We'll perform a text " +
            "search to find" +
            " endpoints").build());
        messages.add(Message.builder().role("user").content(originalContext.get().asText()).build());

        LLMRequest chatRequest = LLMRequest.builder().model("gpt-4o-mini").messages(messages).build();
        var resp = llmService.askQuestion(execution, chatRequest);

        Response response = JsonUtil.MAPPER.readValue(resp, Response.class);
//        log.info("Response is {}", resp);
        ArrayNode endpointsLikeList = JsonUtil.MAPPER.createArrayNode();
        for (Response.Choice choice : response.getChoices()) {
            var content = choice.getMessage().getContentAsString();
            if (content.startsWith("```json")) {
                content = content.substring(7, content.length() - 3);
            } else if (content.startsWith("```")) {
                content = content.substring(3, content.length() - 3);
            }
            log.info("content is {}", content);
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
                    }

                }

            }
        }

        if (endpointsLikeList.size() > 0) {

            ObjectNode endpointsLike = JsonUtil.MAPPER.createObjectNode();
            endpointsLike.put("context", originalContext.orElseThrow().toString());
            endpointsLike.put("endpoints_like", endpointsLikeList);
            context.setExecutionArgs(endpointsLike);
            var endpoints = getEndpointsLike(execution, context);
            log.info("Endpoints like {}", endpoints);

            context.addToMemory("endpoints", endpoints);
        }

        return createdContext;
    }

    @Verb(
        name = "summarize_agent_status", returnType = AgentExecutionContextDTO.class, description =
        "Summarizes agent status. Used when user asks for agent status.",
        requiresTokenManagement = true
    )
    public JsonNode getAgentExecutionStatus(AgentExecution execution, AgentExecutionContextDTO context)
        throws ZtatException, JsonProcessingException {
        var status =  agentExecutionService.getExecutionContextDTO(execution.getExecutionId());


        var lastTen = ListUtils.getLastNElements(status.getMessages(),10);
        var messages = new ArrayList<Message>();

        messages.add(Message.builder().role("system").content("All of the next messages are history between the " +
            "system, assistant, and user" +
            ". Your job is to" +
            " " +
            "summarize them. return { \"summary\" : \"summary text\" }").build());
        messages.addAll(status.getMessages());
        LLMRequest chatRequest = LLMRequest.builder().model("gpt-4o-mini").messages(messages).build();
        var resp = llmService.askQuestion(execution, chatRequest);
        context.addMessages(messages);
        Response response = JsonUtil.MAPPER.readValue(resp, Response.class);
        //log.info("Response is {}", resp);
        for (Response.Choice choice : response.getChoices()) {
            var content = choice.getMessage().getContentAsString();
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
        LLMRequest chatRequest = LLMRequest.builder().model("gpt-4o-mini").messages(messages).build();
        var resp = llmService.askQuestion(execution, chatRequest);
        context.addMessages(messages);
        Response response = JsonUtil.MAPPER.readValue(resp, Response.class);
        
        for (Response.Choice choice : response.getChoices()) {
            var content = choice.getMessage().getContentAsString();
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

    @Verb(name = "create_agent", returnType = AgentExecutionContextDTO.class, description = "Creates an agent who has the " +
        "context. a previously defined contextId is required. previously defined endpoints can be used to build a " +
        "trust policy. must call create_agent_context before this verb. agent type is chat or chat-autonomous. chat is chat only, chat-autonomous is chat and autonomous. determine based on workload.",
        exampleJson = "{  \"agentName\": \"agentName\", \"agentType\": \"agentType\" }",
        requiresTokenManagement = true )
    public ObjectNode createAgent(AgentExecution execution, AgentExecutionContextDTO context)
        throws ZtatException, JsonProcessingException {
        log.info("Creating agent with context: {}", context);

        var contextId=context.getSafeLabel("created_context", "contextId");
        var agentName = context.getSafeLabel("agentName");
        var agentType = context.getSafeLabel("agentType");
        Optional<ObjectNode> optEndpoints = context.getExecutionArgumentScoped("endpoints", ObjectNode.class);
        var policyId = "";
        log.info("Context ID is {}, agentName is {}", contextId, agentName);
        if (null != optEndpoints && optEndpoints.isPresent()) {
            var policyBuilder  = ATPLPolicy.builder()
                .version("v0")
                .description("Policy for agent " + agentName)
                .policyId(UUID.randomUUID().toString());

            var endpoints = optEndpoints.get().get("endpoints");
            log.info("Endpoints are {}", endpoints);
            List<Capability> capabilities = new ArrayList<>();
            for(JsonNode endpoint : endpoints) {
                    var endpointStr = endpoint.get("endpoint").asText();

                    Capability capability = Capability.builder()
                        .description(endpoint.get("name").asText())
                        .endpoints(List.of(extractNormalizedPath(endpointStr)))
                        .build();
                    capabilities.add(capability);


            }
            CapabilitySet capabilitySet = CapabilitySet.builder()
                .primitives(capabilities)
                .build();
            policyBuilder.capabilities(capabilitySet);

            ATPLPolicy policy = policyBuilder.build();

            policyId = savePolicy(execution, true, policy);

        } else {
            log.info("No endpoints provided, using default");
        }



        var agentBuilder =  AgentRegistrationDTO.builder()
            .agentContextId(contextId)
            .clientId(UUID.randomUUID().toString())
            .agentType(agentType)
            .agentName(agentName);
        if (!policyId.isEmpty()){
            log.info("Using policyId {}", policyId);
            agentBuilder.agentPolicyId(policyId);
        } else {
            log.info("No policyId provided, using default");
        }

        AgentRegistrationDTO agentRegistration = agentBuilder.build();
        var response = agentClientService.createAgent(execution, agentRegistration);
        ObjectNode contextNode = JsonUtil.MAPPER.createObjectNode();
        contextNode.put("agentId", agentRegistration.getAgentName());

        return contextNode;
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
                endpointNode.put("endpoint", endpoint.getPath());
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

    @Verb(name = "call_endpoint", returnType = AgentExecutionContextDTO.class, description = "Executes an endpoint at the " +
        "service. Input ", exampleJson = "{ \"endpoint\": \"<url>\", \"method\": \"httpMethod\", \"params\": { " +
        "\"param1\": " +
        "\"param1Value\", " +
        "\"param2\": " +
        "\"param2Value\"" +
        " } }",
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

                entries.add(new AbstractMap.SimpleEntry<>(key, valueList));
            }
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
        
        log.info("Memory lookup - query: '{}', agentId: {}, markings: {}, limit: {}", 
            query, agentId, markings, limit);
        
        // Call the memory API endpoint
        List<Map.Entry<String, List<String>>> params = new ArrayList<>();
        if (agentId != null && !agentId.isEmpty()) {
            params.add(Maps.immutableEntry("agent", List.of(agentId)));
        }
        params.add(Maps.immutableEntry("content", List.of(query)));
        params.add(Maps.immutableEntry("size", List.of(String.valueOf(limit))));
        if (markings != null && !markings.isEmpty()) {
            params.add(Maps.immutableEntry("markings", List.of(markings)));
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
}