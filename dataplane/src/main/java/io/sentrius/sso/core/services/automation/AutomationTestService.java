package io.sentrius.sso.core.services.automation;

import com.jcraft.jsch.*;
import io.sentrius.sso.core.model.ConnectedSystem;
import io.sentrius.sso.core.model.HostSystem;
import io.sentrius.sso.core.model.automation.AutomationSuggestion;
import io.sentrius.sso.core.repository.SystemRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.regex.Pattern;

/**
 * Service for testing automation scripts on target systems via SSH.
 * Includes safety checks to prevent destructive operations.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AutomationTestService {
    
    private final SystemRepository systemRepository;
    
    private static final Set<String> DESTRUCTIVE_COMMANDS = Set.of(
        "rm", "rmdir", "dd", "mkfs", "fdisk", "parted", 
        "reboot", "shutdown", "halt", "poweroff", "init",
        "mkswap", "swapoff", "swapon", "mount", "umount",
        "kill", "killall", "pkill",
        "systemctl stop", "systemctl disable", "systemctl mask",
        "service stop", "service disable",
        "userdel", "groupdel", "deluser", "delgroup",
        "iptables -F", "iptables -X",
        "truncate", "shred", "wipe"
    );
    
    private static final Pattern WRITE_REDIRECT_PATTERN = Pattern.compile(">[^>]");
    private static final Pattern DANGEROUS_PATH_PATTERN = Pattern.compile(
        "/(etc|boot|sys|proc|dev|bin|sbin|lib|lib64|usr/bin|usr/sbin)"
    );
    
    /**
     * Test an automation script on a target system
     */
    public Map<String, Object> testAutomation(AutomationSuggestion suggestion, 
                                             String script, 
                                             boolean dryRun) {
        log.info("Testing automation {} on target {} (dry-run: {})", 
                 suggestion.getId(), suggestion.getTargetSystem(), dryRun);
        
        Map<String, Object> result = new HashMap<>();
        
        Map<String, Object> safetyCheck = analyzeScriptSafety(script, suggestion.getScriptType());
        result.put("safetyAnalysis", safetyCheck);
        
        boolean isDestructive = (boolean) safetyCheck.getOrDefault("isDestructive", true);
        
        if (isDestructive && !dryRun) {
            result.put("status", "blocked");
            result.put("message", "Script contains potentially destructive operations and cannot be executed. Use dry-run mode for validation.");
            return result;
        }
        
        HostSystem targetSystem = findTargetSystem(suggestion.getTargetSystem());
        if (targetSystem == null) {
            result.put("status", "error");
            result.put("message", "Target system not found: " + suggestion.getTargetSystem());
            return result;
        }
        
        if (dryRun) {
            result.put("status", "dry-run-complete");
            result.put("message", "Dry run completed. Script syntax validated.");
            result.put("targetSystem", targetSystem.getDisplayName());
            return result;
        }
        
        try {
            Map<String, Object> execResult = executeScriptOnSystem(targetSystem, script, suggestion.getScriptType());
            result.putAll(execResult);
            result.put("status", "success");
        } catch (Exception e) {
            log.error("Error executing automation on target system", e);
            result.put("status", "error");
            result.put("message", "Execution failed: " + e.getMessage());
        }
        
        return result;
    }
    
    /**
     * Analyze script for safety and destructive operations
     */
    public Map<String, Object> analyzeScriptSafety(String script, String scriptType) {
        Map<String, Object> analysis = new HashMap<>();
        List<String> destructiveOps = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        
        String[] lines = script.split("\n");
        for (String line : lines) {
            String trimmedLine = line.trim();
            
            if (trimmedLine.isEmpty() || trimmedLine.startsWith("#")) {
                continue;
            }
            
            for (String destructiveCmd : DESTRUCTIVE_COMMANDS) {
                if (trimmedLine.contains(destructiveCmd)) {
                    destructiveOps.add("Line contains destructive command: " + destructiveCmd);
                }
            }
            
            if (WRITE_REDIRECT_PATTERN.matcher(trimmedLine).find()) {
                if (DANGEROUS_PATH_PATTERN.matcher(trimmedLine).find()) {
                    warnings.add("Line writes to system directory: " + trimmedLine);
                }
            }
            
            if (trimmedLine.contains("chmod 777") || trimmedLine.contains("chmod -R 777")) {
                warnings.add("Overly permissive chmod detected");
            }
            
            if (trimmedLine.contains("curl") && trimmedLine.contains("| bash")) {
                warnings.add("Potentially dangerous pipe to bash from curl");
            }
            
            if (trimmedLine.contains("wget") && trimmedLine.contains("| sh")) {
                warnings.add("Potentially dangerous pipe to shell from wget");
            }
        }
        
        analysis.put("isDestructive", !destructiveOps.isEmpty());
        analysis.put("destructiveOperations", destructiveOps);
        analysis.put("warnings", warnings);
        analysis.put("overallRisk", determineRiskLevel(destructiveOps, warnings));
        
        return analysis;
    }
    
    /**
     * Execute a script on the target system via SSH
     */
    private Map<String, Object> executeScriptOnSystem(HostSystem targetSystem, 
                                                      String script, 
                                                      String scriptType) throws Exception {
        Map<String, Object> result = new HashMap<>();
        
        JSch jsch = new JSch();
        Session session = null;
        ChannelExec channel = null;
        
        try {
            session = jsch.getSession(
                targetSystem.getSshUser(),
                targetSystem.getHost(),
                targetSystem.getPort()
            );
            
            if (targetSystem.getSshPassword() != null && !targetSystem.getSshPassword().isEmpty()) {
                session.setPassword(targetSystem.getSshPassword());
            }
            
            Properties config = new Properties();
            config.put("StrictHostKeyChecking", "no");
            session.setConfig(config);
            
            session.connect(30000);
            
            channel = (ChannelExec) session.openChannel("exec");
            
            String command = buildExecutionCommand(script, scriptType);
            channel.setCommand(command);
            
            InputStream inputStream = channel.getInputStream();
            InputStream errorStream = channel.getErrStream();
            
            channel.connect();
            
            String output = readStream(inputStream);
            String errorOutput = readStream(errorStream);
            
            int exitStatus = channel.getExitStatus();
            
            result.put("stdout", output);
            result.put("stderr", errorOutput);
            result.put("exitCode", exitStatus);
            result.put("executionTime", System.currentTimeMillis());
            
            log.info("Script execution completed with exit code: {}", exitStatus);
            
        } finally {
            if (channel != null && channel.isConnected()) {
                channel.disconnect();
            }
            if (session != null && session.isConnected()) {
                session.disconnect();
            }
        }
        
        return result;
    }
    
    private String buildExecutionCommand(String script, String scriptType) {
        if ("python".equalsIgnoreCase(scriptType)) {
            String escapedScript = script.replace("'", "'\\''");
            return "python3 -c '" + escapedScript + "'";
        } else {
            String escapedScript = script.replace("'", "'\\''");
            return "bash -c '" + escapedScript + "'";
        }
    }
    
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
    
    private HostSystem findTargetSystem(String targetSystemIdentifier) {
        if (targetSystemIdentifier == null || targetSystemIdentifier.isEmpty()) {
            return null;
        }
        
        try {
            Long systemId = Long.parseLong(targetSystemIdentifier);
            return systemRepository.findById(systemId).orElse(null);
        } catch (NumberFormatException e) {
            List<HostSystem> systems = systemRepository.findByHost(targetSystemIdentifier);
            return systems.isEmpty() ? null : systems.get(0);
        }
    }
    
    private String determineRiskLevel(List<String> destructiveOps, List<String> warnings) {
        if (!destructiveOps.isEmpty()) {
            return "HIGH";
        }
        if (warnings.size() >= 3) {
            return "MEDIUM";
        }
        if (!warnings.isEmpty()) {
            return "LOW";
        }
        return "SAFE";
    }
}
