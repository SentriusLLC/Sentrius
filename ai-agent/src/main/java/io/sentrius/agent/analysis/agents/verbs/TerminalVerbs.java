package io.sentrius.agent.analysis.agents.verbs;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.google.common.collect.Maps;
import io.sentrius.agent.analysis.agents.agents.AgentConfig;
import io.sentrius.agent.analysis.agents.agents.PromptBuilder;
import io.sentrius.agent.analysis.agents.interpreters.TerminalListInterpreter;
import io.sentrius.agent.analysis.agents.interpreters.TerminalOutputInterpreter;
import io.sentrius.sso.core.dto.HostSystemDTO;
import io.sentrius.sso.core.exceptions.ZtatException;
import io.sentrius.sso.core.services.agents.LLMService;
import io.sentrius.sso.core.services.agents.ZeroTrustClientService;
import io.sentrius.sso.core.model.verbs.Verb;
import io.sentrius.sso.core.utils.JsonUtil;
import io.sentrius.sso.genai.Message;
import io.sentrius.sso.genai.Response;
import io.sentrius.sso.genai.model.ChatRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class TerminalVerbs {


    final ZeroTrustClientService zeroTrustClientService;
    final LLMService llmService;


    public TerminalVerbs(ZeroTrustClientService zeroTrustClientService, LLMService llmService) {
        this.zeroTrustClientService = zeroTrustClientService;
        this.llmService = llmService;
    }



    @Verb(name = "list_open_terminals", description = "Retrieves a list of currently open terminals.",
        outputInterpreter = TerminalListInterpreter.class)
    public ArrayNode listTerminals(Map<String, Object> args) throws ZtatException {
        try {

            var response = zeroTrustClientService.callGetOnApi("/ssh/terminal/list/all");
            if (response == null) {
                throw new RuntimeException("Failed to retrieve terminal list");
            }
            log.info("Terminal list response: {}", response);
            return (ArrayNode)JsonUtil.MAPPER.readTree(response);



        } catch (Exception e) {
            throw new RuntimeException("Failed to retrieve terminal list", e);
        }
    }

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
                    // we were able to get logs
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
