package io.sentrius.sso.core.services;

import java.util.Optional;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
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
                    e.printStackTrace();
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

        log.info(" ** JwtUtil.getUserTypeName: {}", jwt);
        var claims = jwt.get("claims");
        if (claims != null) {
            var userId = claims.get("userType"); // change to sub for a user id
            if (null != userId){
                return Optional.of(userId.asText());
            }
        }
        return Optional.empty();

    }
}
