package io.sentrius.agent.analysis.agents.verbs;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.common.collect.Maps;
import io.sentrius.agent.analysis.agents.interpreters.TerminalListInterpreter;
import io.sentrius.agent.analysis.agents.interpreters.TerminalOutputInterpreter;
import io.sentrius.sso.core.dto.HostSystemDTO;
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

    /**
     * Constructs a `TerminalVerbs` instance with the required services.
     *
     * @param zeroTrustClientService The service for interacting with Zero Trust APIs.
     * @param llmService The service for interacting with the LLM (Large Language Model).
     */
    public TerminalVerbs(ZeroTrustClientService zeroTrustClientService, LLMService llmService) {
        this.zeroTrustClientService = zeroTrustClientService;
        this.llmService = llmService;
    }

    /**
     * Retrieves a list of currently open terminals.
     *
     * @param args A map of arguments for the operation (currently unused).
     * @return An `ArrayNode` containing the list of open terminals.
     * @throws ZtatException If there is an error during the operation.
     */
    @Verb(name = "list_open_terminals", description = "Retrieves a list of currently open terminals.",
        outputInterpreter = TerminalListInterpreter.class)
    public ArrayNode listTerminals(Map<String, Object> args) throws ZtatException {
        try {
            var response = zeroTrustClientService.callGetOnApi("/ssh/terminal/list/all");
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
        outputInterpreter = TerminalOutputInterpreter.class, inputInterpreter = TerminalListInterpreter.class)
    public List<ObjectNode> fetchTerminalOutput(List<HostSystemDTO> dtos) throws ZtatException {
        try {
            List<ObjectNode> responses = new ArrayList<>();
            log.info("Terminal list response: {}", dtos);
            for (HostSystemDTO dto : dtos) {
                var response = zeroTrustClientService.callGetOnApi("/sessions/audit/attach", Maps.immutableEntry(
                    "sessionId", List.of(dto.getHostConnection())));

                if (response != null) {
                    // Successfully retrieved logs
                    log.info("Terminal output response: {}", response);
                    var obj = JsonUtil.MAPPER.createObjectNode();
                    obj.put("id", dto.getId());
                    obj.put("terminalOutput", response);
                    responses.add(obj);
                }
            }
            return responses;
        } catch (Exception e) {
            throw new RuntimeException("Failed to retrieve terminal list", e);
        }
    }
}