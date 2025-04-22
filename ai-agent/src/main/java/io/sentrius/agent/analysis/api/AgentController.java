package io.sentrius.agent.analysis.api;

import java.lang.management.ManagementFactory;
import java.net.InetAddress;
import java.net.UnknownHostException;
import io.sentrius.sso.core.model.AgentStatus;
import io.sentrius.sso.core.services.security.KeycloakService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequestMapping("/api/v1/agent")
public class AgentController {

    KeycloakService keycloakService;

    @GetMapping("/ping")
    public ResponseEntity<AgentStatus> getStatus() {
        String hostName = "unknown";
        try {
            hostName = InetAddress.getLocalHost().getHostName();
        } catch (UnknownHostException e) {
            log.warn("Unable to resolve hostname", e);
        }

        long uptimeMillis = ManagementFactory.getRuntimeMXBean().getUptime();
        Runtime runtime = Runtime.getRuntime();

        AgentStatus status = AgentStatus.builder()
            .status("UP")
            .version("1.0.0")
            .health("OK")
            .osName(System.getProperty("os.name"))
            .osArch(System.getProperty("os.arch"))
            .osVersion(System.getProperty("os.version"))
            .hostName(hostName)
            .uptimeMillis(uptimeMillis)
            .totalMemory(runtime.totalMemory())
            .freeMemory(runtime.freeMemory())
            .build();

        log.info("Ping status: {}", status);
        return ResponseEntity.ok(status);
    }

}
