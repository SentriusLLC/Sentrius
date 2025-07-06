package io.sentrius.sso.config;

import lombok.Getter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.EnableAspectJAutoProxy;

@Configuration
@Getter
@EnableAspectJAutoProxy
public class AppConfig {
    // Your configuration beans
    @Value("${agentproxy.externalUrl:}") // Defaults to empty string if not set
    private String agentProxyExternalUrl;

    @Value("${sentrius.agent.register.bootstrap.allow:false}")
    private boolean allowRegistration;

    @Value("${sentrius.agent.bootstrap.policy:default-policy.yaml}")
    private String defaultPolicyFile;

    @Value("${sentrius.agent.launcher.service:http://sentrius-launcherservice:8080/}")
    private String sentriusLauncherService;
}
