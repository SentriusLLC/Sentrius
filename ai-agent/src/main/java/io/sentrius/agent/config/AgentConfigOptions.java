package io.sentrius.agent.config;

import java.util.List;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Slf4j
@ConfigurationProperties(prefix = "agent")
@Getter
@Setter
public class AgentConfigOptions {


    private String namePrefix;
    private String clientId;
    private String type;
    private List<String> endpoints;
}
