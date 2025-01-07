package io.sentrius.sso.config;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import io.sentrius.sso.core.model.security.UserType;
import io.sentrius.sso.core.model.users.User;
import io.sentrius.sso.core.security.CustomAuthenticationSuccessHandler;
import io.sentrius.sso.core.services.CustomUserDetailsService;
import io.sentrius.sso.core.services.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.context.annotation.Bean;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.client.oidc.web.logout.OidcClientInitiatedLogoutSuccessHandler;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.registration.InMemoryClientRegistrationRepository;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.logout.LogoutSuccessHandler;

@Slf4j
@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final CustomUserDetailsService userDetailsService;
    private final CustomAuthenticationSuccessHandler successHandler;
    final UserService userService;


    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {

       /* http
            .authorizeRequests(authorize -> authorize
                .requestMatchers("/sso/v1/**", "/api/v1/**").authenticated() // Pages that need authentication
                .requestMatchers("/node/**", "/js/**", "/css/**", "/images/**", "/error", "/sso/login", "/api/v1/login/authenticate").permitAll() // Public endpoints
                .anyRequest().authenticated() // Other pages need authentication
            )
            .logout(logout -> logout
                .logoutSuccessUrl("/sso/login?logout") // Redirect after logout
            )
            .sessionManagement(session -> session
                .sessionCreationPolicy(SessionCreationPolicy.STATELESS)
            )
            .oauth2Login(oauth2 -> oauth2             // Enable OAuth2 login
                .loginPage("/oauth2/authorization/keycloak") // Redirect to Keycloak
            )
            .oauth2ResourceServer(oauth2 -> oauth2
                .jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverterForKeycloak()))
            )
            .csrf(csrf -> csrf
                .csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse())
            )

            .cors(Customizer.withDefaults())
            .exceptionHandling(exception -> exception
                .accessDeniedPage("/error") // Handle access denied with error page
            );*/
        /*
        http
            .authorizeRequests(authorize -> authorize
                .requestMatchers("/sso/v1/**", "/api/v1/**").authenticated()
                .requestMatchers("/node/**", "/js/**", "/css/**", "/images/**", "/error", "/sso/login", "/api/v1/login/authenticate").permitAll()
                .anyRequest().authenticated()
            )
            .sessionManagement(session -> session
                .sessionCreationPolicy(SessionCreationPolicy.STATELESS)
            )
            .oauth2Login(oauth2 -> oauth2
                .loginPage("/oauth2/authorization/keycloak")
            )
            .oauth2ResourceServer(oauth2 -> oauth2
                .jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverterForKeycloak()))
            )
            .csrf(csrf -> csrf
                .csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse())
            )
            .cors(Customizer.withDefaults())
            .exceptionHandling(exception -> exception
                .accessDeniedPage("/error")
            );*/
        http
            .authorizeHttpRequests(auth -> auth.
                requestMatchers("/actuator/**").permitAll() // Public endpoints
                .requestMatchers("/**").fullyAuthenticated())
            .oauth2ResourceServer(oauth2 -> oauth2
                .jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverterForKeycloak()))
            )
            .oauth2Login(oauth2 -> oauth2
                .loginPage("/oauth2/authorization/keycloak")
            )
            .cors(Customizer.withDefaults());


        return http.build();
    }

    /*
    @Bean
    public JwtDecoder jwtDecoder(OAuth2ResourceServerProperties properties) {
        return JwtDecoders.fromIssuerLocation("http://localhost:8180/realms/sentrius");
    }
*/
    @Bean
    public JwtAuthenticationConverter jwtAuthenticationConverterForKeycloak() {
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        log.info("**** Initializing JwtAuthenticationConverter");

        converter.setJwtGrantedAuthoritiesConverter(jwt -> {
            log.info("**** Jwt Authentication Converter invoked");
            Collection<GrantedAuthority> authorities = new JwtGrantedAuthoritiesConverter().convert(jwt);
            log.info("JWT Claims: {}", jwt.getClaims());

            String userId = jwt.getClaimAsString("sub");
            String username = jwt.getClaimAsString("preferred_username");
            String email = jwt.getClaimAsString("email");

            log.info("Extracted User Info: userId={}, username={}, email={}", userId, username, email);

            User user = userService.getUserWithDetails(userId);
            if (user == null) {
                user = User.builder()
                    .username(username)
                    .emailAddress(email)
                    .authorizationType(UserType.createUnknownUser())
                    .build();
                log.info("Creating new user: {}", user);
                userService.save(user);
            }

            return authorities;
        });

        return converter;
    }


    private List<String> getRoles(Jwt jwt) {
        return Optional.ofNullable(jwt.getClaimAsMap("resource_access"))
            .map(resourceAccess -> (Map<String, Object>) resourceAccess.get("sentrius-api"))
            .map(client -> (List<String>) ((Map<String, Object>) client).get("roles"))
            .orElse(Collections.emptyList());
    }

}
