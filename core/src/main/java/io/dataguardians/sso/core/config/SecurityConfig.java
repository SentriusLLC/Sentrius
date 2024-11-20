package io.dataguardians.sso.core.config;

import io.dataguardians.sso.core.security.CustomAuthenticationSuccessHandler;
import io.dataguardians.sso.core.services.CustomUserDetailsService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.builders.AuthenticationManagerBuilder;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.context.annotation.Bean;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;

@Slf4j
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private final CustomUserDetailsService userDetailsService;
    private final CustomAuthenticationSuccessHandler successHandler;

    public SecurityConfig(CustomUserDetailsService userDetailsService, CustomAuthenticationSuccessHandler successHandler) {
        this.userDetailsService = userDetailsService;
        this.successHandler = successHandler;
    }


    @Bean
    public PasswordEncoder passwordEncoder() {
        var encoder = new BCryptPasswordEncoder();
        return encoder;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {

        http
            .authorizeRequests(authorize -> authorize
                .requestMatchers("/sso/v1/**", "/api/v1/**").authenticated() // Pages that need authentication
                .requestMatchers("/node/**", "/js/**", "/css/**", "/images/**", "/error", "/sso/login", "/api/v1/login/authenticate").permitAll() // Public endpoints
                .anyRequest().authenticated() // Other pages need authentication
            )
            .formLogin(formLogin -> formLogin
                .loginPage("/sso/login").permitAll() // Custom login page
                .loginProcessingUrl("/api/v1/login/authenticate") // URL for form submission
                .successHandler(successHandler).permitAll()
            )
            .logout(logout -> logout
                .logoutSuccessUrl("/sso/login?logout") // Redirect after logout
            )
            .sessionManagement(session -> session
                .sessionCreationPolicy(SessionCreationPolicy.ALWAYS)
            )/*.csrf(csrf -> csrf
                .csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse())
            )*/
            .exceptionHandling(exception -> exception
                .accessDeniedPage("/error") // Handle access denied with error page
            );

        return http.build();
    }

    @Bean
    public AuthenticationManager authenticationManager(HttpSecurity http) throws Exception {
        return http.getSharedObject(AuthenticationManagerBuilder.class)
            .userDetailsService(userDetailsService)
            .passwordEncoder(passwordEncoder())
            .and()
            .build();
    }
}
