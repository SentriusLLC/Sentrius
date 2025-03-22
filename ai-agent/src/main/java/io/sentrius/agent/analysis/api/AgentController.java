package io.sentrius.agent.analysis.api;

import io.sentrius.agent.analysis.model.AgentStatus;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequestMapping("/api/v1/agent")
public class AgentController {


    @GetMapping("/status")
    public ResponseEntity<AgentStatus> getStatus() {
        return ResponseEntity.ok(AgentStatus.builder().status("UP").version("1.0.0").health("OK").build());
    }

}
