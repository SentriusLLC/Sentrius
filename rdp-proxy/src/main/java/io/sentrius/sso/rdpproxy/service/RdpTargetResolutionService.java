package io.sentrius.sso.rdpproxy.service;

import io.sentrius.sso.core.services.HostGroupService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import lombok.Builder;
import lombok.Data;
import org.springframework.stereotype.Service;

import java.util.Optional;

/**
 * Service for resolving RDP targets from JWT claims to backend host systems.
 * Supports both database lookups and static configuration mappings.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RdpTargetResolutionService {
    
    private final HostGroupService hostGroupService;

    
    /**
     * Resolve target identifier to RDP connection information
     */
    public Optional<TargetResolution> resolveTarget(String target) {
        if (target == null || target.trim().isEmpty()) {
            log.warn("Empty target provided for resolution");
            return Optional.empty();
        }
        
        log.debug("Resolving RDP target: {}", target);

        var hostSystem = hostGroupService.getHostSystem(Long.valueOf(target));

        return hostSystem.map(hs -> TargetResolution.builder()
            .host(hs.getHost())
            .port(hs.getPort())
            .displayName(hs.getDisplayName())
            .rdpUser(hs.getRdpUser() != null ? hs.getRdpUser() : "Administrator")
            .rdpPassword(hs.getRdpPassword() != null ? hs.getRdpPassword() : "")
            .rdpDomain(hs.getRdpDomain() != null ? hs.getRdpDomain() : "")
            .redirectionAllowed(false).build())  ;

    }
    

    
    /**
     * Validate if a host string is reasonable (basic validation)
     */
    private boolean isValidHost(String host) {
        if (host == null || host.trim().isEmpty()) {
            return false;
        }
        
        // Basic validation - could be enhanced
        host = host.trim();
        
        // Check for localhost variations
        if (host.equals("localhost") || host.equals("127.0.0.1")) {
            return true;
        }
        
        // Check for private IP ranges (basic check)
        if (host.matches("^10\\..*") || host.matches("^192\\.168\\..*") || 
            host.matches("^172\\.(1[6-9]|2[0-9]|3[01])\\..*")) {
            return true;
        }
        
        // Check for FQDN pattern
        if (host.matches("^[a-zA-Z0-9]([a-zA-Z0-9-]{0,61}[a-zA-Z0-9])?(\\.[a-zA-Z0-9]([a-zA-Z0-9-]{0,61}[a-zA-Z0-9])?)*$")) {
            return true;
        }
        
        return false;
    }
    
    /**
     * Validate if a port number is in valid range
     */
    private boolean isValidPort(int port) {
        return port > 0 && port <= 65535;
    }


    
    /**
     * Target resolution result
     */
    @Builder
    @Data
    public static class TargetResolution {
        private String host;
        private int port;
        private String displayName;
        private String rdpUser;
        private String rdpPassword;
        private String rdpDomain;
        private boolean redirectionAllowed;
    }
}