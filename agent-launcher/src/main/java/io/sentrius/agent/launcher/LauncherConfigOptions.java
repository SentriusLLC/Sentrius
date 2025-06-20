package io.sentrius.agent.launcher;

import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Slf4j
@ConfigurationProperties(prefix = "agent.launcher")
@Getter
@Setter
public class LauncherConfigOptions {


    private String namePrefix;
    private String type;
}
