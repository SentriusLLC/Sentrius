package io.sentrius.sso.mcp.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Configuration for MCP proxy services
 */
@Configuration
public class MCPProxyConfig {

    @Bean
    public RestTemplate mcpRestTemplate() {
        return new RestTemplate();
    }

    @Bean
    public ObjectMapper mcpObjectMapper() {
        return new ObjectMapper();
    }
}