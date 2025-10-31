package io.sentrius.agent.config;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Builder
@Slf4j
@ConfigurationProperties(prefix = "agent")
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class AgentConfigOptions {


    private String namePrefix;
    private String clientId;
    private String type;
    private List<String> endpoints;
}
