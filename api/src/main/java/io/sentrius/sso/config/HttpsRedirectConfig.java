package io.sentrius.sso.config;

import java.net.URI;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.server.WebFilter;

@Configuration
public class HttpsRedirectConfig {

    @Value("${https.redirect.enabled:true}") // Default is true
    private boolean httpsRedirectEnabled;

    @Bean
    public WebFilter httpsRedirectFilter() {
        return (exchange, chain) -> {
            if (httpsRedirectEnabled &&
                exchange.getRequest().getHeaders().containsKey("X-Forwarded-Proto") &&
                "http".equals(exchange.getRequest().getHeaders().getFirst("X-Forwarded-Proto"))) {
                URI httpsUri = exchange.getRequest()
                    .getURI()
                    .resolve(exchange.getRequest().getURI().toString().replace("http://", "https://"));
                return exchange.getResponse().setComplete();
            }
            return chain.filter(exchange);
        };
    }
}