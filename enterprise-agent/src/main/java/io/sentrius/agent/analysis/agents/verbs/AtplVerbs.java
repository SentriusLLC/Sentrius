package io.sentrius.agent.analysis.agents.verbs;

import java.io.IOException;
import java.io.InputStream;
import java.util.Map;
import com.fasterxml.jackson.databind.node.ArrayNode;
import io.sentrius.sso.core.dto.agents.AgentExecutionContextDTO;
import io.sentrius.sso.core.dto.ztat.TokenDTO;
import io.sentrius.sso.core.exceptions.ZtatException;
import io.sentrius.sso.core.model.verbs.Verb;
import io.sentrius.sso.core.services.agents.AgentClientService;
import io.sentrius.sso.core.services.agents.LLMService;
import io.sentrius.sso.core.services.agents.ZeroTrustClientService;
import io.sentrius.sso.core.trust.ATPLPolicy;
import io.sentrius.sso.core.utils.JsonUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * The `TerminalVerbs` class provides methods to interact with terminal-related operations.
 * It includes functionality to list open terminals and fetch terminal logs.
 */
@Slf4j
@Service
public class AtplVerbs extends VerbBase {

    final ZeroTrustClientService zeroTrustClientService;
    final LLMService llmService;
    final AgentVerbs agentVerbs;

    /**
     * Constructs a `TerminalVerbs` instance with the required services.
     *
     * @param zeroTrustClientService The service for interacting with Zero Trust APIs.
     * @param llmService The service for interacting with the LLM (Large Language Model).
     */
    public AtplVerbs(@Value("${agent.ai.config}") String agentConfigFile,
                     @Value("${agent.ai.context.db.id:none}") String agentDatabaseContext,
                     ZeroTrustClientService zeroTrustClientService, LLMService llmService, AgentVerbs agentVerbs,
                     AgentClientService agentClientService) {
        super(agentConfigFile, agentDatabaseContext, agentClientService);
        this.zeroTrustClientService = zeroTrustClientService;
        this.llmService = llmService;
        this.agentVerbs = agentVerbs;
    }

    @Verb(name = "qry_policy_id", description = "Queries trust policy by policyId.", requiresTokenManagement = true)
    public ArrayNode queryPolicyById(TokenDTO token, String policyId) throws ZtatException {
        try {

            log.info("policy is : {}", policyId);
            String response = zeroTrustClientService.callGetOnApi(token, "/api/v1/policies/" + policyId);
            if (response == null) {
                throw new RuntimeException("Failed to retrieve terminal list");
            }
            log.info("Terminal list response: {}", response);
            return (ArrayNode) JsonUtil.MAPPER.readTree(response);
        } catch (Exception e) {
            throw new RuntimeException("Failed to retrieve terminal list", e);
        }
    }

    @Verb(name = "get_atpl_schema", description = "Gets Schema. No argument required. Returns JSON Schema.",
        
         requiresTokenManagement = true)
    public String getAtplSchema(TokenDTO token, AgentExecutionContextDTO context) throws ZtatException, IOException {
        InputStream schema = getClass().getClassLoader().getResourceAsStream("atpl-schema.json");
        if (schema == null) {
            throw new RuntimeException("atpl-schema.json not found on classpath");

        }
        return new String(schema.readAllBytes());
    }

    /**
     * Retrieves a list of currently open terminals.
     *
     * @return An `ArrayNode` containing the list of open terminals.
     * @throws io.sentrius.sso.core.exceptions.ZtatException If there is an error during the operation.
     */
    @Verb(name = "save_policy", description = "Saves an ATPL policy. Accepts ATPL policy in JSON format.",
        
         requiresTokenManagement = true)
    public ArrayNode savePolicy(TokenDTO token, AgentExecutionContextDTO context) throws ZtatException {
        try {

            var policy = context.getExecutionArgumentScoped("policy" , ATPLPolicy.class);
            log.info("policy is : {}", policy);
            String response = zeroTrustClientService.callPostOnApi("/api/v1/policies", policy);
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