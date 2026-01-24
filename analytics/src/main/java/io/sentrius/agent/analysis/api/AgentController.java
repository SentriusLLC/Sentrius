package io.sentrius.agent.analysis.api;

import io.sentrius.agent.analysis.model.AgentStatus;
import io.sentrius.agent.analysis.service.AgentExecutionSummarizerService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/v1/agent")
public class AgentController {

    private final AgentExecutionSummarizerService summarizerService;

    @Autowired
    public AgentController(AgentExecutionSummarizerService summarizerService) {
        this.summarizerService = summarizerService;
    }

    @GetMapping("/status")
    public ResponseEntity<AgentStatus> getStatus() {
        return ResponseEntity.ok(AgentStatus.builder().status("UP").version("1.0.0").health("OK").build());
    }

    /**
     * Summarize an agent execution by analyzing its logs.
     * 
     * @param executionId The execution ID
     * @param agentId     The agent ID (e.g., pod name)
     * @param agentType   The type of agent
     * @param podLogs     The pod logs to analyze (request body)
     * @return Map containing status, summary, resourceLinks, and exitCode
     */
    @PostMapping("/summarize")
    public ResponseEntity<Map<String, Object>> summarizeExecution(
            @RequestParam String executionId,
            @RequestParam String agentId,
            @RequestParam String agentType,
            @RequestBody String podLogs) {
        
        log.info("Received summarization request for execution: {}", executionId);
        
        Map<String, Object> result = summarizerService.summarizeExecution(
            executionId, agentId, agentType, podLogs
        );
        
        return ResponseEntity.ok(result);
    }
}
