package io.sentrius.agent.analysis.agents.verbs;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import io.sentrius.agent.analysis.agents.agents.AgentConfig;
import io.sentrius.sso.core.dto.agents.AgentContextDTO;
import io.sentrius.sso.core.dto.ztat.AgentExecution;
import io.sentrius.sso.core.exceptions.ZtatException;
import io.sentrius.sso.core.services.agents.AgentClientService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;

@Slf4j
public abstract class VerbBase {
    @Value("${agent.ai.config}")
    protected String agentConfigFile;

    @Value("${agent.ai.context.db.id:none}")
    protected String agentDatabaseContext;


    protected final AgentClientService agentClientService;

    protected VerbBase(@Value("${agent.ai.config}") String agentConfigFile,
                       @Value("${agent.ai.context.db.id:none}") String agentDatabaseContext,
                       AgentClientService agentClientService) {
        this.agentClientService = agentClientService;
        this.agentConfigFile = agentConfigFile;
        this.agentDatabaseContext = agentDatabaseContext;
    }

    protected AgentConfig getAgentConfig(AgentExecution execution) throws IOException, ZtatException {
        AgentConfig config = null;
        if (agentDatabaseContext != null && !agentDatabaseContext.equals("none")) {
            AgentContextDTO agentContext = agentClientService.getAgentContext(execution,
                agentDatabaseContext);
            config = AgentConfig.builder().description(agentContext.getDescription())
                .context(agentContext.getContext()).build();
            log.info("Agent context loaded: {}", agentContext);
        }else {

            InputStream is = getStream(agentConfigFile);
            if (is == null) {
                throw new RuntimeException(agentConfigFile + " not found on classpath");
            }

            config = new ObjectMapper(new YAMLFactory()).readValue(is, AgentConfig.class);
        }
        return config;
    }

    private InputStream getStream(String requestedPath) throws IOException {
        Path path = Paths.get(requestedPath); // 🔁 Replace with your actual path

        if (!Files.exists(path)) {
            throw new RuntimeException("File not found at path: " + path.toAbsolutePath());
        }

        return Files.newInputStream(path);

    }
}
