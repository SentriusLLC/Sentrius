package io.sentrius.sso.core.services.automation;

import com.jcraft.jsch.*;
import io.sentrius.sso.core.model.HostSystem;
import io.sentrius.sso.core.model.automation.Automation;
import io.sentrius.sso.core.model.automation.AutomationExecution;
import io.sentrius.sso.core.model.automation.AutomationSuggestion;
import io.sentrius.sso.core.model.users.User;
import io.sentrius.sso.core.repository.SystemRepository;
import io.sentrius.sso.core.repository.automation.ScriptExecutionRepository;
import io.sentrius.sso.core.repository.automation.ScriptRepository;
import io.sentrius.sso.core.services.TerminalService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Service for executing automation scripts on remote systems with cleanup
 */
@Slf4j
@Service
@RequiredArgsConstructor
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public class AutomationExecutionService {

    private final ScriptRepository scriptRepository;
    private final ScriptExecutionRepository scriptExecutionRepository;
    private final SystemRepository systemRepository;
    private final FileTransferService fileTransferService;
    private final TerminalService terminalService;


    /**
     * Execute a suggestion script on a target system without requiring automation conversion
     *
     * @param suggestionId ID of the suggestion to execute
     * @param systemId ID of the target system
     * @param executedByUser User who initiated the execution
     * @return Execution result with status and output
     */
    @Transactional
    public Map<String, Object> executeSuggestionOnSystem(Long suggestionId, Long systemId, User executedByUser, AutomationSuggestion suggestion) {
        log.info("Executing suggestion {} on system {} by user {}", suggestionId, systemId, executedByUser.getUsername());

        Map<String, Object> result = new HashMap<>();

        // Fetch system
        Optional<HostSystem> systemOpt = systemRepository.findById(systemId);
        if (systemOpt.isEmpty()) {
            result.put("status", "error");
            result.put("message", "System not found: " + systemId);
            return result;
        }

        HostSystem system = systemOpt.get();

        // Get script from suggestion
        String script = suggestion.getSuggestedScript();
        String scriptType = suggestion.getScriptType();

        if (script == null || script.trim().isEmpty()) {
            result.put("status", "error");
            result.put("message", "Suggestion has no script to execute");
            return result;
        }

        // Generate unique temporary file path
        String remoteFilePath = generateTempFilePathForSuggestion(suggestionId, scriptType);

        // Create execution record with PENDING status
        AutomationExecution execution = AutomationExecution.builder()
                .suggestion(suggestion)
                .system(system)
                .executedBy(executedByUser)
                .status("PENDING")
                .logTm(new Timestamp(System.currentTimeMillis()))
                .build();
        execution = scriptExecutionRepository.save(execution);

        try {
            // Transfer script to remote system
            log.info("Transferring script to system {} at path {}", system.getDisplayName(), remoteFilePath);
            Map<String, Object> transferResult = fileTransferService.transferScriptToSystem(
                    system,
                    script,
                    remoteFilePath
            );

            if (!"success".equals(transferResult.get("status"))) {
                execution.setStatus("FAILED");
                execution.setExecutionOutput("Transfer failed: " + transferResult.get("message"));
                scriptExecutionRepository.save(execution);

                result.put("status", "error");
                result.put("message", "Failed to transfer script: " + transferResult.get("message"));
                result.put("executionId", execution.getId());
                return result;
            }

            // Update status to RUNNING
            execution.setStatus("RUNNING");
            scriptExecutionRepository.save(execution);

            // Execute the script
            log.info("Executing script on system {}", system.getDisplayName());
            Map<String, Object> execResult = executeScriptOnSystemWithCleanup(
                    system,
                    remoteFilePath,
                    scriptType
            );

            // Update execution record with results
            execution.setExecutionOutput((String) execResult.get("output"));
            execution.setExitCode((Integer) execResult.get("exitCode"));
            execution.setStatus((Integer) execResult.get("exitCode") == 0 ? "SUCCESS" : "FAILED");
            scriptExecutionRepository.save(execution);

            result.put("status", execution.getStatus().toLowerCase());
            result.put("message", "Execution completed");
            result.put("executionId", execution.getId());
            result.put("exitCode", execution.getExitCode());
            result.put("output", execution.getExecutionOutput());
            result.put("system", system.getDisplayName());

            log.info("Suggestion execution completed with status: {}", execution.getStatus());

        } catch (Exception e) {
            log.error("Error executing suggestion {} on system {}", suggestionId, systemId, e);
            execution.setStatus("FAILED");
            execution.setExecutionOutput("Execution error: " + e.getMessage());
            scriptExecutionRepository.save(execution);

            result.put("status", "error");
            result.put("message", "Execution failed: " + e.getMessage());
            result.put("executionId", execution.getId());
        }

        return result;
    }

    /**
     * Execute an automation script on a target system and clean up after execution
     *
     * @param automationId ID of the automation to execute
     * @param systemId ID of the target system
     * @param executedByUser User who initiated the execution
     * @return Execution result with status and output
     */
    @Transactional
    public Map<String, Object> executeAutomationOnSystem(Long automationId, Long systemId, User executedByUser) {
        log.info("Executing automation {} on system {} by user {}", automationId, systemId, executedByUser.getUsername());

        Map<String, Object> result = new HashMap<>();

        // Fetch automation and system
        Optional<Automation> automationOpt = scriptRepository.findById(automationId);
        if (automationOpt.isEmpty()) {
            result.put("status", "error");
            result.put("message", "Automation not found: " + automationId);
            return result;
        }

        Optional<HostSystem> systemOpt = systemRepository.findById(systemId);
        if (systemOpt.isEmpty()) {
            result.put("status", "error");
            result.put("message", "System not found: " + systemId);
            return result;
        }

        Automation automation = automationOpt.get();
        HostSystem system = systemOpt.get();

        // Generate unique temporary file path
        String remoteFilePath = generateTempFilePath(automation);

        // Create execution record with PENDING status
        AutomationExecution execution = AutomationExecution.builder()
                .automation(automation)
                .system(system)
                .executedBy(executedByUser)
                .status("PENDING")
                .logTm(new Timestamp(System.currentTimeMillis()))
                .build();
        execution = scriptExecutionRepository.save(execution);

        try {
            // Transfer script to remote system
            log.info("Transferring script to system {} at path {}", system.getDisplayName(), remoteFilePath);
            Map<String, Object> transferResult = fileTransferService.transferScriptToSystem(
                    system,
                    automation.getScript(),
                    remoteFilePath
            );

            if (!"success".equals(transferResult.get("status"))) {
                execution.setStatus("FAILED");
                execution.setExecutionOutput("Transfer failed: " + transferResult.get("message"));
                scriptExecutionRepository.save(execution);

                result.put("status", "error");
                result.put("message", "Failed to transfer script: " + transferResult.get("message"));
                result.put("executionId", execution.getId());
                return result;
            }

            // Update status to RUNNING
            execution.setStatus("RUNNING");
            scriptExecutionRepository.save(execution);

            // Execute the script
            log.info("Executing script on system {}", system.getDisplayName());
            Map<String, Object> execResult = executeScriptOnSystemWithCleanup(
                    system,
                    remoteFilePath,
                    automation.getType()
            );

            // Update execution record with results
            execution.setExecutionOutput((String) execResult.get("output"));
            execution.setExitCode((Integer) execResult.get("exitCode"));
            execution.setStatus((Integer) execResult.get("exitCode") == 0 ? "SUCCESS" : "FAILED");
            scriptExecutionRepository.save(execution);

            result.put("status", execution.getStatus().toLowerCase());
            result.put("message", "Execution completed");
            result.put("executionId", execution.getId());
            result.put("exitCode", execution.getExitCode());
            result.put("output", execution.getExecutionOutput());
            result.put("system", system.getDisplayName());

            log.info("Automation execution completed with status: {}", execution.getStatus());

        } catch (Exception e) {
            log.error("Error executing automation {} on system {}", automationId, systemId, e);
            execution.setStatus("FAILED");
            execution.setExecutionOutput("Execution error: " + e.getMessage());
            scriptExecutionRepository.save(execution);

            result.put("status", "error");
            result.put("message", "Execution failed: " + e.getMessage());
            result.put("executionId", execution.getId());
        }

        return result;
    }

    /**
     * Execute script on remote system and clean up the file after execution
     */
    private Map<String, Object> executeScriptOnSystemWithCleanup(HostSystem system, String remoteFilePath, String scriptType) {
        Map<String, Object> result = new HashMap<>();
        StringBuilder outputBuilder = new StringBuilder();
        int exitCode = -1;

        Session session = null;
        ChannelExec channel = null;

        try {
            // Create SSH session using TerminalService
            session = terminalService.createJSchSession(system, system.getSshPassword(), true);

            // Build execution command with cleanup
            String command = buildExecutionCommandWithCleanup(remoteFilePath, scriptType);
            log.debug("Executing command: {}", command);

            channel = (ChannelExec) session.openChannel("exec");
            channel.setCommand(command);

            InputStream inputStream = channel.getInputStream();
            InputStream errorStream = channel.getErrStream();

            log.debug("Connecting");
            channel.connect();
            log.debug("Connected");
            // Read output
            String stdout = readStream(inputStream);
            String stderr = readStream(errorStream);

            exitCode = channel.getExitStatus();

            // Combine stdout and stderr
            if (!stdout.isEmpty()) {
                outputBuilder.append("=== Standard Output ===\n").append(stdout);
            }
            if (!stderr.isEmpty()) {
                if (outputBuilder.length() > 0) {
                    outputBuilder.append("\n");
                }
                outputBuilder.append("=== Standard Error ===\n").append(stderr);
            }

            log.info("Script execution completed with exit code: {}", exitCode);

        } catch (Exception e) {
            log.error("Error executing script on system {}", system.getDisplayName(), e);
            outputBuilder.append("Execution error: ").append(e.getMessage());
            exitCode = -1;
        } finally {
            if (channel != null && channel.isConnected()) {
                channel.disconnect();
            }
            if (session != null && session.isConnected()) {
                session.disconnect();
            }
        }

        result.put("output", outputBuilder.toString());
        result.put("exitCode", exitCode);
        return result;
    }

    /**
     * Build command to execute script and clean up after
     */
    private String buildExecutionCommandWithCleanup(String remoteFilePath, String scriptType) {
        String interpreter;
        if ("python".equalsIgnoreCase(scriptType)) {
            interpreter = "python3";
        } else {
            interpreter = "bash";
        }

        // Execute script and remove it after execution (using trap to ensure cleanup on exit)
        return String.format(
                "trap 'rm -f %s' EXIT; chmod +x %s && %s %s",
                remoteFilePath,
                remoteFilePath,
                interpreter,
                remoteFilePath
        );
    }

    /**
     * Generate a unique temporary file path for the script
     */
    private String generateTempFilePath(Automation automation) {
        String timestamp = String.valueOf(System.currentTimeMillis());
        String filename = "automation_" + automation.getId() + "_" + timestamp;

        if ("python".equalsIgnoreCase(automation.getType())) {
            filename += ".py";
        } else {
            filename += ".sh";
        }

        return "/tmp/" + filename;
    }

    /**
     * Generate a unique temporary file path for a suggestion script
     */
    private String generateTempFilePathForSuggestion(Long suggestionId, String scriptType) {
        String timestamp = String.valueOf(System.currentTimeMillis());
        String filename = "suggestion_" + suggestionId + "_" + timestamp;

        if ("python".equalsIgnoreCase(scriptType)) {
            filename += ".py";
        } else {
            filename += ".sh";
        }

        return "/tmp/" + filename;
    }


    /**
     * Read stream and convert to string
     */
    private String readStream(InputStream stream) throws Exception {
        StringBuilder output = new StringBuilder();
        BufferedReader reader = new BufferedReader(
                new InputStreamReader(stream, StandardCharsets.UTF_8)
        );

        String line;
        while ((line = reader.readLine()) != null) {
            output.append(line).append("\n");
        }

        return output.toString();
    }

    /**
     * Get execution history for an automation
     */
    public List<AutomationExecution> getExecutionHistory(Long automationId) {
        return scriptExecutionRepository.findByAutomationIdOrderByLogTmDesc(automationId);
    }

    /**
     * Get execution history for a suggestion
     */
    public List<AutomationExecution> getSuggestionExecutionHistory(Long suggestionId) {
        return scriptExecutionRepository.findBySuggestionIdOrderByLogTmDesc(suggestionId);
    }

    /**
     * Get execution by ID
     */
    public Optional<AutomationExecution> getExecutionById(Long executionId) {
        return scriptExecutionRepository.findById(executionId);
    }
}
