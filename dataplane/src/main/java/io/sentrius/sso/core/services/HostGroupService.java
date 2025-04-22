package io.sentrius.sso.core.services;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.zip.ZipFile;
import io.sentrius.sso.core.model.ProxyHost;
import io.sentrius.sso.core.repository.HostGroupRepository;
import io.sentrius.sso.core.repository.TerminalSessionMetadataRepository;
import io.sentrius.sso.core.repository.UserRepository;
import io.sentrius.sso.core.repository.SystemRepository;
import io.sentrius.sso.core.data.specification.HostGroupSpecification;
import io.sentrius.sso.core.model.HostSystem;
import io.sentrius.sso.core.model.users.User;
import io.sentrius.sso.core.model.hostgroup.HostGroup;
import jakarta.persistence.EntityNotFoundException;
import lombok.extern.slf4j.Slf4j;
import org.hibernate.Hibernate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
public class HostGroupService {

    @Autowired
    private HostGroupRepository hostGroupRepository;

    @Autowired
    private SystemRepository systemRepository;

    @Autowired
    private UserRepository userRepository;


    @Autowired
    private TerminalSessionMetadataRepository terminalSessionMetadataRepository;

    @Transactional
    public HostGroup getHostGroup(Long hostGroupId) {
        HostGroup hostGroup = hostGroupRepository.findById(hostGroupId)
            .orElseThrow(() -> new EntityNotFoundException("Host " + hostGroupId + " Group not found"));

        return hostGroup;
    }

    @Transactional
    public Optional<HostGroup> getHostGroupWithHostSystems(User user, Long hostGroupId) {
        HostGroup hostGroup = hostGroupRepository.findByIdWithUsers(hostGroupId)
            .orElseThrow(() -> new EntityNotFoundException("Host Enclave not found"));


        boolean userIsMember = hostGroup.getUsers().stream()
            .anyMatch(usr -> usr.getId().equals(user.getId()));

        if (!userIsMember) {
            return Optional.empty();
        }
        hostGroup.getHostSystems().size(); // Forces initialization of the hostSystemList

        return Optional.of(hostGroup);
    }


    @Transactional(readOnly = true)
    public Optional<HostSystem> getHostSystem(Long hostId) {
        var hostSystem = systemRepository.findById(hostId);
        if (hostSystem.isPresent()) {
            Hibernate.initialize(hostSystem.get().getHostGroups());
            Hibernate.initialize(hostSystem.get().getPublicKeyList());
        }
        return hostSystem;
    }

    @Transactional
    public List<HostSystem> getAssignedHostsForUser(User user) {
        final List<HostSystem> systems = new ArrayList<>();

        userRepository.findHostGroupsByUserId(user.getId()).forEach(hostGroup -> {
            HostGroup sys = hostGroupRepository.findById(hostGroup.getId())
                .orElseThrow(() -> new EntityNotFoundException("Host Enclave not found"));
            log.info("HostGroup: {}", sys.getId());
            if (null != sys && null != sys.getHostSystems()) {
                Hibernate.initialize(sys.getHostSystems());

                for(HostSystem system : sys.getHostSystems()) {
                    log.info("HostSystem: {}", system);
                }
                log.info("Adding to Systems HostGroup: {}", sys.getHostSystems());
                systems.addAll(sys.getHostSystems());
            }
        });

        return systems;
    }

    @Transactional
    public List<HostSystem> getAssignedHostsForUserAndId(User user, Long groupId) {
        final List<HostSystem> systems = new ArrayList<>();

        userRepository.findHostGroupsByUserId(user.getId()).stream().filter(hostGroup-> {
            return hostGroup.getId().equals(groupId);
    }).forEach(hostGroup -> {
            HostGroup sys = hostGroupRepository.findById(hostGroup.getId())
                .orElseThrow(() -> new EntityNotFoundException("Host Enclave not found"));
            log.info("HostGroup: {}", sys.getId());
            if (null != sys && null != sys.getHostSystems()) {
                Hibernate.initialize(sys.getHostSystems());

                for (HostSystem system : sys.getHostSystems()) {
                    log.info("HostSystem: {}", system);
                }
                log.info("Adding to Systems HostGroup: {}", sys.getHostSystems());
                systems.addAll(sys.getHostSystems());
            }
        });


        return systems;
    }

    @Transactional
    public HostSystem addHost(User user, HostSystem system) {
        return systemRepository.save(system);
    }

    public List<HostGroup> searchHostGroupsByUserIdAndFilters(Long userId, String enclaveName) {
        Specification<HostGroup> spec = HostGroupSpecification.findByUserIdAndOptionalFilters(userId, enclaveName);
        return hostGroupRepository.findAll(spec);
    }

    @Transactional
    public HostGroup createHostGroupAndAssignToUser(User operatingUser, HostGroup hostGroup) {
        // Step 1: Create a new HostGroup (Enclave)
        HostGroup savedHostGroup = hostGroupRepository.save(hostGroup);

        // Step 2: Assign the HostGroup to the User
        Optional<User> userOptional = userRepository.findById(operatingUser.getId());
        if (userOptional.isPresent()) {
            User user = userOptional.get();
            user.getHostGroups().add(savedHostGroup);
            userRepository.save(user);
        } else {
            throw new RuntimeException("User with ID " + operatingUser.getId() + " not found");
        }

        return savedHostGroup;
    }

    @Transactional
    public void assignHostSystemToHostGroup(Long hostGroupId, Long hostSystemId) {
        Optional<HostGroup> hostGroupOptional = hostGroupRepository.findById(hostGroupId);
        Optional<HostSystem> hostSystemOptional = systemRepository.findById(hostSystemId);

        if (hostGroupOptional.isPresent() && hostSystemOptional.isPresent()) {
            HostGroup hostGroup = hostGroupOptional.get();
            HostSystem hostSystem = hostSystemOptional.get();

            // Assuming there's a List<HostSystem> in HostGroup and vice versa
            hostGroup.getHostSystems().add(hostSystem);
            if (null == hostSystem.getHostGroups()){
                hostSystem.setHostGroups(new ArrayList<>());
            }
            hostSystem.getHostGroups().add(hostGroup);

            hostGroupRepository.save(hostGroup);
            systemRepository.save(hostSystem);
        } else {
            throw new RuntimeException("HostGroup or HostSystem not found");
        }
    }

    @Transactional
    public void removeHostSystemFromHostGroup(Long hostGroupId, Long hostSystemId) {
        Optional<HostGroup> hostGroupOptional = hostGroupRepository.findById(hostGroupId);
        Optional<HostSystem> hostSystemOptional = systemRepository.findById(hostSystemId);

        if (hostGroupOptional.isPresent() && hostSystemOptional.isPresent()) {
            HostGroup hostGroup = hostGroupOptional.get();
            HostSystem hostSystem = hostSystemOptional.get();

            // Assuming there's a List<HostSystem> in HostGroup and vice versa
            hostGroup.getHostSystems().remove(hostSystem);
            hostSystem.getHostGroups().remove(hostGroup);

            hostGroupRepository.save(hostGroup);
            systemRepository.save(hostSystem);
        } else {
            throw new RuntimeException("HostGroup or HostSystem not found");
        }
    }

    public List<HostGroup> getAllHostGroups() {
        return hostGroupRepository.findAll();
    }

    public List<HostGroup> getHostGroupsByName(String name) {
        return hostGroupRepository.findByName(name);
    }


    public List<HostGroup> getAllHostGroups(User user) {
        return hostGroupRepository.findAllByUserId(user.getId());
    }

    @Transactional
    public void save(HostGroup hostGroup) {
        hostGroupRepository.save(hostGroup);
    }

    public List<HostSystem> getAllHosts() {
        return systemRepository.findAll();
    }

    public List<HostGroup> searchAllHostGroups(String enclaveName) {
        Specification<HostGroup> spec = HostGroupSpecification.findByOptionalFilters(enclaveName);
        return hostGroupRepository.findAll(spec);
    }

    @Transactional
    public void deleteHostSystem(User user, Long hostId) {
        HostSystem attachedHostSystem = systemRepository.findById(hostId)
            .orElseThrow(() -> new EntityNotFoundException("Host system not found"));

        // Force-load
        attachedHostSystem.getProxies().size();
        attachedHostSystem.getHostGroups().size();

        // Sever proxies
        for (ProxyHost proxy : attachedHostSystem.getProxies()) {
            proxy.setHostSystem(null);
        }
        attachedHostSystem.getProxies().clear();

        // Sever host groups
        for (HostGroup group : attachedHostSystem.getHostGroups()) {
            group.getHostSystems().remove(attachedHostSystem);
        }
        attachedHostSystem.getHostGroups().clear();

        // Save decoupled HostSystem
        systemRepository.saveAndFlush(attachedHostSystem);

        // DELETE terminal session metadata linked to this host
        terminalSessionMetadataRepository.deleteByHostSystemId(hostId);


        // Now safe to delete
        systemRepository.delete(attachedHostSystem);

        log.info("Deleted HostSystem {} for User {}", attachedHostSystem.getId(), user.getId());
    }

/*
    public List<HostSystem> getUnassignedHostsForUser(User operatingUser) {
        systemRepository.findAll().forEach(hostSystem -> {
            if (!hostSystem.getUsers().contains(operatingUser)) {
                return;
            }
        });
    }*/
}