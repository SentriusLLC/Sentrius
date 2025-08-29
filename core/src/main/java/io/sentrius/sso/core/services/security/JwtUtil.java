package io.sentrius.sso.core.services.security;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Optional;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.jsonwebtoken.Jwts;
import io.sentrius.sso.core.utils.JsonUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;

@Slf4j
public class JwtUtil {
    public static ObjectNode getJWT() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        if (authentication != null && authentication.isAuthenticated()) {
            if (authentication.getPrincipal() instanceof Jwt) {
                Jwt jwt = (Jwt) authentication.getPrincipal();
                ObjectNode node = JsonUtil.MAPPER.createObjectNode();
                node.put("sub", jwt.getClaimAsString("sub"));
                return node;
            } else {
                try {
                    String jwt = JsonUtil.MAPPER
                        .registerModule(new JavaTimeModule())
                        .writeValueAsString(authentication.getPrincipal());
                    return (ObjectNode) JsonUtil.MAPPER.readTree(jwt);
                } catch (Exception e) {
                    // ignorable error, just return an empty node
                }
            }
        }
        return JsonUtil.MAPPER.createObjectNode();
    }

    public static Optional<String> getEmail(ObjectNode jwt) {
        var claims = jwt.get("claims");
        if (claims != null) {
            var email = claims.get("email");
            if (null != email){
                return Optional.of(email.asText());
            }
        }
        return Optional.of("");
    }

    public static Optional<String> getUserId(ObjectNode jwt) {

        var claims = jwt.get("claims");
        if (claims != null) {
            var userId = claims.get("sub"); // change to sub for a user id
            if (null != userId){
                return Optional.of(userId.asText());
            }
        }
        return Optional.empty();

    }

    public static Optional<String> getUsername(ObjectNode jwt) {

        var claims = jwt.get("claims");
        if (claims != null) {
            var userId = claims.get("preferred_username"); // change to sub for a user id
            if (null != userId){
                return Optional.of(userId.asText());
            }
        }
        return Optional.empty();

    }

    public static Optional<String> getUserTypeName(ObjectNode jwt) {
        
        var claims = jwt.get("claims");
        if (claims != null) {
            var userId = claims.get("userType"); // change to sub for a user id
            if (null != userId){
                return Optional.of(userId.asText());
            }
        }
        return Optional.empty();

    }

    public static String extractKid(String jwt) {
        try {
            // Strip "Bearer " prefix if present
            if (jwt.startsWith("Bearer ")) {
                jwt = jwt.substring(7);
            }
            String[] parts = jwt.split("\\.");

            if (parts.length != 3) {
                throw new IllegalArgumentException("Invalid JWT token format");
            }

            String headerJson = new String(Base64.getUrlDecoder().decode(parts[0]), StandardCharsets.UTF_8);
            var headerNode = JsonUtil.MAPPER.readTree(headerJson);

            if (!headerNode.has("kid")) {
                throw new RuntimeException("Missing 'kid' in JWT header");
            }

            return headerNode.get("kid").asText();
        } catch (Exception e) {
            log.error("Failed to extract 'kid' from JWT: {}", jwt, e);
            throw new RuntimeException("Failed to extract 'kid' from JWT", e);
        }
    }

}
