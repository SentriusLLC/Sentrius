package io.sentrius.sso.core.services;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.zip.ZipFile;
import io.sentrius.sso.core.repository.HostGroupRepository;
import io.sentrius.sso.core.repository.UserRepository;
import io.sentrius.sso.core.repository.SystemRepository;
import io.sentrius.sso.core.data.specification.HostGroupSpecification;
import io.sentrius.sso.core.model.HostSystem;
import io.sentrius.sso.core.model.users.User;
import io.sentrius.sso.core.model.hostgroup.HostGroup;
import jakarta.persistence.EntityNotFoundException;
import jakarta.transaction.Transactional;
import lombok.extern.slf4j.Slf4j;
import org.hibernate.Hibernate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class HostGroupService {

    @Autowired
    private HostGroupRepository hostGroupRepository;

    @Autowired
    private SystemRepository systemRepository;

    @Autowired
    private UserRepository userRepository;

    @Transactional
    public HostGroup getHostGroup(Long hostGroupId) {
        HostGroup hostGroup = hostGroupRepository.findById(hostGroupId)
            .orElseThrow(() -> new EntityNotFoundException("Host " + hostGroupId + " Group not found"));

        return hostGroup;
    }

    @Transactional
    public Optional<HostGroup> getHostGroupWithHostSystems(User user, Long hostGroupId) {
        HostGroup hostGroup = hostGroupRepository.findByIdWithUsers(hostGroupId)
            .orElseThrow(() -> new EntityNotFoundException("Host Group not found"));


        boolean userIsMember = hostGroup.getUsers().stream()
            .anyMatch(usr -> usr.getId().equals(user.getId()));

        if (!userIsMember) {
            return Optional.empty();
        }
        hostGroup.getHostSystemList().size(); // Forces initialization of the hostSystemList

        return Optional.of(hostGroup);
    }


    @Transactional
    public Optional<HostSystem> getHostSystem(Long hostId) {
        return systemRepository.findById(hostId);
    }

    @Transactional
    public List<HostSystem> getAssignedHostsForUser(User user) {
        final List<HostSystem> systems = new ArrayList<>();

        userRepository.findHostGroupsByUserId(user.getId()).forEach(hostGroup -> {
            HostGroup sys = hostGroupRepository.findById(hostGroup.getId())
                .orElseThrow(() -> new EntityNotFoundException("Host Group not found"));
            log.info("HostGroup: {}", sys.getId());
            if (null != sys && null != sys.getHostSystemList()) {
                Hibernate.initialize(sys.getHostSystemList());

                for(HostSystem system : sys.getHostSystemList()) {
                    log.info("HostSystem: {}", system);
                }
                log.info("Adding to Systems HostGroup: {}", sys.getHostSystemList());
                systems.addAll(sys.getHostSystemList());
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
                .orElseThrow(() -> new EntityNotFoundException("Host Group not found"));
            log.info("HostGroup: {}", sys.getId());
            if (null != sys && null != sys.getHostSystemList()) {
                Hibernate.initialize(sys.getHostSystemList());

                for (HostSystem system : sys.getHostSystemList()) {
                    log.info("HostSystem: {}", system);
                }
                log.info("Adding to Systems HostGroup: {}", sys.getHostSystemList());
                systems.addAll(sys.getHostSystemList());
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

    public void deleteHostSystem(User user, HostSystem hostSystem) {
        systemRepository.delete(hostSystem);
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