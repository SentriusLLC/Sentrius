package io.sentrius.agent.analysis.agents.verbs;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.common.collect.Maps;
import io.sentrius.agent.analysis.agents.interpreters.AsessmentListInterpreter;
import io.sentrius.agent.analysis.agents.interpreters.ObjectListInterpreter;
import io.sentrius.agent.analysis.agents.interpreters.TerminalListInterpreter;
import io.sentrius.agent.analysis.agents.interpreters.TerminalOutputInterpreter;
import io.sentrius.agent.analysis.model.AssessedTerminal;
import io.sentrius.agent.analysis.model.Assessment;
import io.sentrius.sso.core.dto.HostSystemDTO;
import io.sentrius.sso.core.dto.ztat.AgentExecution;
import io.sentrius.sso.core.dto.ztat.TokenDTO;
import io.sentrius.sso.core.dto.ztat.ZtatRequestDTO;
import io.sentrius.sso.core.exceptions.ZtatException;
import io.sentrius.sso.core.services.agents.LLMService;
import io.sentrius.sso.core.services.agents.ZeroTrustClientService;
import io.sentrius.sso.core.model.verbs.Verb;
import io.sentrius.sso.core.utils.JsonUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * The `TerminalVerbs` class provides methods to interact with terminal-related operations.
 * It includes functionality to list open terminals and fetch terminal logs.
 */
@Slf4j
@Service
public class TerminalVerbs {

    final ZeroTrustClientService zeroTrustClientService;
    final LLMService llmService;
    final AgentVerbs agentVerbs;

    /**
     * Constructs a `TerminalVerbs` instance with the required services.
     *
     * @param zeroTrustClientService The service for interacting with Zero Trust APIs.
     * @param llmService The service for interacting with the LLM (Large Language Model).
     */
    public TerminalVerbs(ZeroTrustClientService zeroTrustClientService, LLMService llmService, AgentVerbs agentVerbs) {
        this.zeroTrustClientService = zeroTrustClientService;
        this.llmService = llmService;
        this.agentVerbs = agentVerbs;
    }

    /**
     * Retrieves a list of currently open terminals.
     *
     * @param args A map of arguments for the operation (currently unused).
     * @return An `ArrayNode` containing the list of open terminals.
     * @throws ZtatException If there is an error during the operation.
     */
    @Verb(name = "list_open_terminals", description = "Retrieves a list of currently open terminals.",
        outputInterpreter = TerminalListInterpreter.class, requiresTokenManagement = true)
    public ArrayNode listTerminals(TokenDTO token, Map<String, Object> args) throws ZtatException {
        try {
            var response = zeroTrustClientService.callGetOnApi(token, "/ssh/terminal/list/all");
            if (response == null) {
                throw new RuntimeException("Failed to retrieve terminal list");
            }
            log.info("Terminal list response: {}", response);
            return (ArrayNode) JsonUtil.MAPPER.readTree(response);
        } catch (Exception e) {
            throw new RuntimeException("Failed to retrieve terminal list", e);
        }
    }

    /**
     * Retrieves a list of terminal output logs for the given open terminals.
     *
     * @param dtos A list of `HostSystemDTO` objects representing the terminals.
     * @return A list of `ObjectNode` objects containing terminal output logs.
     * @throws ZtatException If there is an error during the operation.
     */
    @Verb(name = "fetch_terminal_logs", description = "Retrieves a list of terminal output from a given open terminal.",
        outputInterpreter = TerminalOutputInterpreter.class, inputInterpreter = TerminalListInterpreter.class,
        returnType = List.class, requiresTokenManagement = true)
    public List<ObjectNode> fetchTerminalOutput(TokenDTO token, List<HostSystemDTO> dtos) throws ZtatException {
        try {
            List<ObjectNode> responses = new ArrayList<>();
            log.info("Terminal list response: {}", dtos);
            for (HostSystemDTO dto : dtos) {
                var sessionId = URLEncoder.encode(dto.getHostConnection(), StandardCharsets.UTF_8);
                var response = zeroTrustClientService.callGetOnApi(token,"/sessions/audit/attach", Maps.immutableEntry(
                    "sessionId", List.of(sessionId)));

                if (response != null) {
                    // Successfully retrieved logs
                    log.info("Terminal output response: {}", response);
                    var obj = JsonUtil.MAPPER.createObjectNode();
                    obj.put("id", dto.getHostConnection());
                    obj.put("terminalOutput", response);
                    responses.add(obj);
                }
            }
            return responses;
        } catch (Exception e) {
            throw new RuntimeException("Failed to retrieve terminal list", e);
        }
    }

    @Verb(name = "kill_session_with_assessment", description = "Kills a terminal session using a terminal assessment." +
        " Requires sessionId, risk, and description in a json object.",
        requiresTokenManagement = true,
        outputInterpreter = TerminalOutputInterpreter.class, inputInterpreter = AsessmentListInterpreter.class)
    public List<ObjectNode> killTerminalSessionWithTerminalAssessment(AgentExecution execution, List<AssessedTerminal> dtos)
        throws ZtatException, IOException {
        try {
            List<ObjectNode> responses = new ArrayList<>();
            log.info("Terminal list response: {}", dtos);
            for (AssessedTerminal dto : dtos) {

                // submit the kill
                if (dto != null){
                    log.info("Terminal list response2: {}", dto);
                }
                else {
                    log.info("Terminal list response: {}", dto.toString());
                }


                    var risk =dto.getAssessment().getRisk();
                    var description = dto.getAssessment().getDescription();
                    if (null != risk && null != description) {
                        switch(risk) {
                            case "low":
                                // skip and do nothing
                                continue;
                            case "medium":
                            case "high":
                                // kill the session
                                log.info("Killing terminal session: {}", dto.getAssessment().getSessionId());
                                break;
                            default:
                                throw new RuntimeException("Unknown risk level: " + risk);
                        }
                        try {
                            var sessionId = URLEncoder.encode(dto.getAssessment().getSessionId(), StandardCharsets.UTF_8);
                            var response = zeroTrustClientService.callPutOnApi(
                                execution, "/ssh/terminal/kill",
                                Maps.immutableEntry("sessionId", List.of(sessionId))
                            );
                            if (response != null) {
                                // Successfully retrieved logs
                                log.info("Terminal output response: {}", response);
                                var obj = JsonUtil.MAPPER.createObjectNode();
                                obj.put("id", dto.getAssessment().getSessionId());
                                obj.put("terminalOutput", response);
                                responses.add(obj);
                            }
                        }catch (ZtatException e) {
                            log.error("Cannot kill session without justification");
                            var endpoint = zeroTrustClientService.createEndPointRequest("kill Terminal session`",
                                "/ssh" +
                                    "/terminal/kill");
                            ZtatRequestDTO ztatRequestDTO = ZtatRequestDTO.builder()
                                .user(execution.getUser())
                                .command(endpoint.toString())
                                .justification(description)
                                .summary("Kill a Terminal session because it is high risk")
                                .build();
                            log.info("Obtaining approval. Justification: {} {}", description, ztatRequestDTO);
                            var request = zeroTrustClientService.requestZtatToken(execution, execution.getUser()
                                ,ztatRequestDTO);

                            ztatRequestDTO.setRequestId(request);

                            var token = agentVerbs.justifyAgent(execution, ztatRequestDTO, dto);
                            execution.setZtatToken(token);
                            var sessionId = URLEncoder.encode(dto.getAssessment().getSessionId(), StandardCharsets.UTF_8);
                            var response = zeroTrustClientService.callPutOnApi(
                                execution, "/ssh/terminal/kill",
                                Maps.immutableEntry("sessionId", List.of(sessionId))
                            );
                        }
                    }




            }
            return responses;
        } catch (Exception | ZtatException e) {
            throw new RuntimeException("Failed to retrieve terminal list", e);
        }
    }


}