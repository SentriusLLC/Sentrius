package io.sentrius.agent.analysis.agents.verbs;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import com.fasterxml.jackson.databind.JsonNode;
import io.sentrius.agent.analysis.agents.interpreters.TerminalListInterpreter;
import io.sentrius.agent.analysis.api.UserCommunicationService;
import io.sentrius.sso.core.dto.HostSystemDTO;
import io.sentrius.sso.core.dto.ztat.TokenDTO;
import io.sentrius.sso.core.exceptions.ZtatException;
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
public class UserConnectionVerbs {

    final ZeroTrustClientService zeroTrustClientService;
    final LLMService llmService;
    final AgentVerbs agentVerbs;
    final UserCommunicationService userCommunicationService;

    /**
     * Constructs a `TerminalVerbs` instance with the required services.
     *
     * @param zeroTrustClientService The service for interacting with Zero Trust APIs.
     * @param llmService The service for interacting with the LLM (Large Language Model).
     */
    public UserConnectionVerbs(ZeroTrustClientService zeroTrustClientService, LLMService llmService, AgentVerbs agentVerbs,
                               UserCommunicationService userCommunicationService
    ) {
        this.zeroTrustClientService = zeroTrustClientService;
        this.llmService = llmService;
        this.agentVerbs = agentVerbs;
        this.userCommunicationService = userCommunicationService;
    }

    /**
     * Retrieves a list of currently open terminals.
     *
     * @param args A map of arguments for the operation (currently unused).
     * @return An `ArrayNode` containing the list of open terminals.
     * @throws io.sentrius.sso.core.exceptions.ZtatException If there is an error during the operation.
     */
    @Verb(name = "list_hosts", description = "Retrieves a list of available hosts.",
        outputInterpreter = TerminalListInterpreter.class, requiresTokenManagement = true)
    public List<HostSystemDTO> listHosts(TokenDTO token, Map<String, Object> args) throws ZtatException {
        try {
            var response = zeroTrustClientService.callGetOnApi(token, "/sso/v1/enclaves/hosts/list");
            if (response == null) {
                throw new RuntimeException("Failed to retrieve terminal list");
            }
            var messages = JsonUtil.MAPPER.readTree(response);
            List<HostSystemDTO> hostSystemDTOS = new ArrayList<>();
            for(JsonNode message : messages) {
                HostSystemDTO dto = JsonUtil.MAPPER.readValue(message.toString(), HostSystemDTO.class);
                hostSystemDTOS.add(dto);
            }
            log.info("Terminal list response: {}", response);
            return hostSystemDTOS;
        } catch (Exception e) {
            throw new RuntimeException("Failed to retrieve terminal list", e);
        }
    }



}