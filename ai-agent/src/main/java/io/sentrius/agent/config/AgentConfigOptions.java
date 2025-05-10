package io.sentrius.agent.config;

import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Slf4j
@ConfigurationProperties(prefix = "agent")
@Getter
@Setter
public class AgentConfigOptions {


    private String name;
}
