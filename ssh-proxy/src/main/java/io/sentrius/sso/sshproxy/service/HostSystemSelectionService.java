package io.sentrius.sso.sshproxy.service;

import io.sentrius.sso.core.model.Host;
import io.sentrius.sso.core.model.HostSystem;
import io.sentrius.sso.core.model.hostgroup.HostGroup;
import io.sentrius.sso.core.repository.SystemRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hibernate.Hibernate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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

    @Transactional(readOnly = true)
    public Optional<HostSystem> getHostSystemById(Long id) {
        try {
            var hostSystem = systemRepository.findById(id);
            hostSystem.ifPresent(hs -> {
                Hibernate.initialize(hs.getHostGroups());
                for(HostGroup group : hs.getHostGroups()) {
                    Hibernate.initialize(group.getRules());
                }
            });
            return hostSystem;
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
    @Transactional(readOnly = true)
    public List<HostSystem> getHostSystemsByDisplayName(String displayName) {
        try {
            var listOfHostSystems = systemRepository.findByDisplayName(displayName);
            if (!listOfHostSystems.isEmpty()) {
                for (var hostSystem : listOfHostSystems) {
                    Hibernate.initialize(hostSystem.getHostGroups());
                    for (HostGroup group : hostSystem.getHostGroups()) {
                        Hibernate.initialize(group.getRules());
                    }
                }
            }
                return listOfHostSystems;

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
                for(HostSystem defaultHost : hostSystems) {

                    Hibernate.initialize(defaultHost.getHostGroups());
                    for (HostGroup group : defaultHost.getHostGroups()) {
                        Hibernate.initialize(group.getRules());
                    }
                    log.info(
                        "Using default HostSystem: {} ({}:{})",
                        defaultHost.getDisplayName(), defaultHost.getHost(), defaultHost.getPort()
                    );
                    if (defaultHost.getPort() == 3389){
                        log.warn("Default HostSystem {} is configured for RDP (port 3389). " +
                                "Ensure this is intended for SSH proxy usage.", defaultHost.getId());
                        continue;
                    }
                    return Optional.of(defaultHost);
                }
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