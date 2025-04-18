package io.sentrius.agent.analysis.api;

import java.security.GeneralSecurityException;
import java.sql.SQLException;
import io.sentrius.agent.analysis.model.AgentStatus;
import io.sentrius.sso.core.services.security.KeycloakService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.apache.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequestMapping("/api/v1/agent")
public class AgentController {

    KeycloakService keycloakService;

    @GetMapping("/status")
    public ResponseEntity<AgentStatus> getStatus() {
        return ResponseEntity.ok(AgentStatus.builder().status("UP").version("1.0.0").health("OK").build());
    }

}
