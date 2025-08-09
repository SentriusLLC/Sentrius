package io.sentrius.sso.sshproxy.service;

import io.sentrius.sso.core.model.HostSystem;
import io.sentrius.sso.core.model.hostgroup.HostGroup;
import io.sentrius.sso.core.repository.SystemRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hibernate.Hibernate;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

/**
 * Service for managing HostSystem selection in SSH proxy sessions.
 * Integrates with the existing Sentrius HostSystem database configuration.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class HostSystemSelectionService {

    private final SystemRepository systemRepository;

    /**
     * Get a HostSystem by ID for SSH proxy connection.
     */
    public Optional<HostSystem> getHostSystemById(Long id) {
        try {
            return systemRepository.findById(id);
        } catch (Exception e) {
            log.error("Error retrieving HostSystem with ID: {}", id, e);
            return Optional.empty();
        }
    }

    /**
     * Get all available HostSystems for SSH proxy.
     */
    public List<HostSystem> getAllHostSystems() {
        try {
            return systemRepository.findAll();
        } catch (Exception e) {
            log.error("Error retrieving all HostSystems", e);
            return List.of();
        }
    }

    /**
     * Find HostSystems by display name.
     */
    public List<HostSystem> getHostSystemsByDisplayName(String displayName) {
        try {
            return systemRepository.findByDisplayName(displayName);
        } catch (Exception e) {
            log.error("Error retrieving HostSystems by display name: {}", displayName, e);
            return List.of();
        }
    }

    /**
     * Find HostSystems by host address.
     */
    public List<HostSystem> getHostSystemsByHost(String host) {
        try {
            return systemRepository.findAll().stream()
                    .filter(hs -> host.equals(hs.getHost()))
                    .toList();
        } catch (Exception e) {
            log.error("Error retrieving HostSystems by host: {}", host, e);
            return List.of();
        }
    }

    /**
     * Get the default HostSystem (first available one) for SSH proxy.
     */
    @Transactional
    public Optional<HostSystem> getDefaultHostSystem() {
        try {
            List<HostSystem> hostSystems = systemRepository.findAll();
            if (!hostSystems.isEmpty()) {
                HostSystem defaultHost = hostSystems.get(0);
                Hibernate.initialize(defaultHost.getHostGroups());
                for(HostGroup gropu : defaultHost.getHostGroups()) {
                    Hibernate.initialize(gropu.getRules());
                }
                log.info("Using default HostSystem: {} ({}:{})", 
                    defaultHost.getDisplayName(), defaultHost.getHost(), defaultHost.getPort());
                return Optional.of(defaultHost);
            }
        } catch (Exception e) {
            log.error("Error retrieving default HostSystem", e);
        }
        return Optional.empty();
    }

    /**
     * Validate if a HostSystem is available and properly configured for SSH proxy.
     */
    public boolean isHostSystemValid(HostSystem hostSystem) {
        if (hostSystem == null) {
            return false;
        }
        
        boolean valid = hostSystem.getHost() != null && !hostSystem.getHost().trim().isEmpty()
                && hostSystem.getPort() != null && hostSystem.getPort() > 0
                && hostSystem.getSshUser() != null && !hostSystem.getSshUser().trim().isEmpty();
        
        if (!valid) {
            log.warn("HostSystem {} is not properly configured for SSH proxy", hostSystem.getId());
        }
        
        return valid;
    }
}