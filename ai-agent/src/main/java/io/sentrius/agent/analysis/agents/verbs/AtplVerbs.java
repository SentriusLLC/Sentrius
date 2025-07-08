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
import io.sentrius.agent.analysis.agents.interpreters.TerminalListInterpreter;
import io.sentrius.agent.analysis.agents.interpreters.TerminalOutputInterpreter;
import io.sentrius.agent.analysis.model.AssessedTerminal;
import io.sentrius.sso.core.dto.HostSystemDTO;
import io.sentrius.sso.core.dto.ztat.AgentExecution;
import io.sentrius.sso.core.dto.ztat.TokenDTO;
import io.sentrius.sso.core.dto.ztat.ZtatRequestDTO;
import io.sentrius.sso.core.exceptions.ZtatException;
import io.sentrius.sso.core.model.verbs.DefaultInterpreter;
import io.sentrius.sso.core.model.verbs.Verb;
import io.sentrius.sso.core.services.agents.LLMService;
import io.sentrius.sso.core.services.agents.ZeroTrustClientService;
import io.sentrius.sso.core.utils.JsonUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * The `TerminalVerbs` class provides methods to interact with terminal-related operations.
 * It includes functionality to list open terminals and fetch terminal logs.
 */
@Slf4j
@Service
public class AtplVerbs {

    final ZeroTrustClientService zeroTrustClientService;
    final LLMService llmService;
    final AgentVerbs agentVerbs;

    /**
     * Constructs a `TerminalVerbs` instance with the required services.
     *
     * @param zeroTrustClientService The service for interacting with Zero Trust APIs.
     * @param llmService The service for interacting with the LLM (Large Language Model).
     */
    public AtplVerbs(ZeroTrustClientService zeroTrustClientService, LLMService llmService, AgentVerbs agentVerbs) {
        this.zeroTrustClientService = zeroTrustClientService;
        this.llmService = llmService;
        this.agentVerbs = agentVerbs;
    }

    /**
     * Retrieves a list of currently open terminals.
     *
     * @param args A map of arguments for the operation (currently unused).
     * @return An `ArrayNode` containing the list of open terminals.
     * @throws io.sentrius.sso.core.exceptions.ZtatException If there is an error during the operation.
     */
    @Verb(name = "save_policy", description = "Saves an ATPL policy.",
        outputInterpreter = DefaultInterpreter.class, requiresTokenManagement = true)
    public ArrayNode savePolicy(TokenDTO token, Map<String, Object> args) throws ZtatException {
        try {
            String response = zeroTrustClientService.callGetOnApi(token, "/ssh/terminal/list/all");
            if (response == null) {
                throw new RuntimeException("Failed to retrieve terminal list");
            }
            log.info("Terminal list response: {}", response);
            return (ArrayNode) JsonUtil.MAPPER.readTree(response);
        } catch (Exception e) {
            throw new RuntimeException("Failed to retrieve terminal list", e);
        }
    }

}