package io.sentrius.agent.launcher.api;

import io.sentrius.agent.launcher.service.DockerImageBuilderService;
import io.sentrius.sso.config.ApiPaths;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping(ApiPaths.API_V1 + "/builder")
public class DockerImageBuilderController {

    @Autowired
    private DockerImageBuilderService dockerImageBuilderService;

    /**
     * Trigger a Docker image build
     */
    @PostMapping("/build")
    public ResponseEntity<Map<String, Object>> buildImage(@RequestBody Map<String, Object> buildRequest) {
        try {
            Long sessionId = ((Number) buildRequest.get("sessionId")).longValue();
            String podName = (String) buildRequest.get("podName");
            String dockerfilePath = (String) buildRequest.get("dockerfilePath");
            String contextPath = (String) buildRequest.get("contextPath");
            
            log.info("Received Docker build request for session {} pod {}", sessionId, podName);
            
            String jobName = dockerImageBuilderService.buildDockerImage(
                    sessionId, podName, dockerfilePath, contextPath);
            
            Map<String, Object> response = new HashMap<>();
            if (jobName != null) {
                response.put("success", true);
                response.put("jobName", jobName);
                return ResponseEntity.ok(response);
            } else {
                response.put("success", false);
                response.put("message", "Failed to create build job");
                return ResponseEntity.internalServerError().body(response);
            }
            
        } catch (Exception e) {
            log.error("Error handling build request", e);
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("message", "Error: " + e.getMessage());
            return ResponseEntity.internalServerError().body(response);
        }
    }

    /**
     * Check build status
     */
    @GetMapping("/status")
    public ResponseEntity<Map<String, String>> getBuildStatus(@RequestParam String jobName) {
        try {
            String status = dockerImageBuilderService.checkBuildStatus(jobName);
            
            Map<String, String> response = new HashMap<>();
            response.put("status", status);
            response.put("jobName", jobName);
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            log.error("Error getting build status for job {}", jobName, e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * Get build logs
     */
    @GetMapping("/logs")
    public ResponseEntity<Map<String, String>> getBuildLogs(@RequestParam String jobName) {
        try {
            String logs = dockerImageBuilderService.getBuildLogs(jobName);
            
            Map<String, String> response = new HashMap<>();
            response.put("logs", logs);
            response.put("jobName", jobName);
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            log.error("Error getting build logs for job {}", jobName, e);
            return ResponseEntity.internalServerError().build();
        }
    }
}
