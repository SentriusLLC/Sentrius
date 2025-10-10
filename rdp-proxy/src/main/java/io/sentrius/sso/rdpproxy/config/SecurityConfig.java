package io.sentrius.sso.rdpproxy.config;

import io.sentrius.sso.core.security.PublicKeyManager;
import io.sentrius.sso.rdpproxy.service.ZtatPublicKeyService;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.PublicKey;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import io.sentrius.sso.config.KeycloakAuthSuccessHandler;
import io.sentrius.sso.core.model.security.UserType;
import io.sentrius.sso.core.model.users.User;
import io.sentrius.sso.core.security.CustomAuthenticationSuccessHandler;
import io.sentrius.sso.core.services.CustomUserDetailsService;
import io.sentrius.sso.core.services.UserService;
import io.sentrius.sso.core.services.security.KeycloakService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.context.annotation.Bean;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.oauth2.server.resource.web.BearerTokenResolver;
import org.springframework.security.web.SecurityFilterChain;

@Slf4j
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    // Reads ?token=… (or Authorization header if present)
    @Bean
    BearerTokenResolver tunnelQueryTokenResolver() {
        return request -> {
            String auth = request.getHeader("Authorization");
            if (auth != null && auth.startsWith("Bearer ")) return auth.substring(7);
            String token = request.getParameter("token");
            return (token != null && !token.isBlank()) ? token : null;
        };
    }

    // HS256 decoder for your short-lived tunnel tokens (deprecated - replaced by asymmetric JWT)
    @Bean
    JwtDecoder guacHmacDecoder(@Value("${sentrius.rdp-proxy.hmac-secret:defaultSecretForBackwardCompatibility}") String secret) {
        var key = new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
        return NimbusJwtDecoder.withSecretKey(key).macAlgorithm(MacAlgorithm.HS256).build();
    }
    
    /**
     * Composite JWT decoder that supports both symmetric HS256 (backward compatibility)
     * and asymmetric RS256 tokens from ZtatTokenService
     */
    @Bean
    JwtDecoder compositeJwtDecoder(@Value("${sentrius.rdp-proxy.hmac-secret:defaultSecretForBackwardCompatibility}") String secret,
                                   ZtatPublicKeyService ztatPublicKeyService) {
        // HS256 decoder for backward compatibility
        var hmacKey = new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
        JwtDecoder hmacDecoder = NimbusJwtDecoder.withSecretKey(hmacKey).macAlgorithm(MacAlgorithm.HS256).build();

        return token -> {
            // Try RS256 with ZtatTokenService public key first
            PublicKey ztatPublicKey = ztatPublicKeyService.getZtatPublicKey();
            if (ztatPublicKey != null) {

                try {
                    JwtDecoder rsaDecoder = NimbusJwtDecoder.withPublicKey((java.security.interfaces.RSAPublicKey) ztatPublicKey)
                        .signatureAlgorithm(SignatureAlgorithm.RS256)
                        .build();
                    log.info("Composite JWT decoder initialized with HS256 and RS256 support {}", token);
                    if (token.endsWith("?"))
                    {
                        token = token.substring(0, token.length() - 1);
                    }
                    else if (token.endsWith("?."))
                    {
                        token = token.substring(0, token.length() - 2);
                    } else if (token.endsWith("?undefined."))
                    {
                        token = token.substring(0, token.length() - 11);
                    }
                    else if (token.endsWith("?undefined"))
                    {
                        token = token.substring(0, token.length() - 10);
                    }
                    log.info("Composite JWT decoder initialized with HS256 and RS256 support {}", token);
                    return rsaDecoder.decode(token);
                } catch (JwtException e) {
                    log.debug("Failed to decode JWT with ZtatTokenService public key, trying HMAC: {}", e.getMessage());
                }
            }
            
            // Fall back to HS256 for backward compatibility
            return hmacDecoder.decode(token);
        };
    }

    @Bean
    @Order(1)
    SecurityFilterChain tunnelChain(HttpSecurity http,
                                    BearerTokenResolver tunnelQueryTokenResolver,
                                    JwtDecoder compositeJwtDecoder) throws Exception {
        http
            .securityMatcher("/guacamole/**")
            .requestCache(cache -> cache.disable())
            .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth.anyRequest().authenticated())
            .oauth2ResourceServer(oauth -> oauth
                .bearerTokenResolver(tunnelQueryTokenResolver)  // accept ?token=
                .jwt(jwt -> jwt.decoder(compositeJwtDecoder)));     // validate both HS256 and RS256 JWT
        return http.build();
    }

    // API + UI chains same as in (A), omitted for brevity
}