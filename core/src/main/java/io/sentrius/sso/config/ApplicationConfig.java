package io.sentrius.sso.config;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

@Component
public class ApplicationConfig {

    private final String serviceName;

    @Autowired
    public ApplicationConfig(Environment environment) {
        this.serviceName = environment.getProperty("otel.resource.attributes.service.name", "unknown-service");
    }

    public String getServiceName() {
        return serviceName;
    }
}
