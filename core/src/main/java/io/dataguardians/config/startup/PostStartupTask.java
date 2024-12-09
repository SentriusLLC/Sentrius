package io.dataguardians.config.startup;

import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.DigestInputStream;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.SQLException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import com.jcraft.jsch.JSchException;
import io.dataguardians.sso.core.config.SystemOptions;
import io.dataguardians.sso.core.model.ConfigurationOption;
import io.dataguardians.sso.core.model.HostSystem;
import io.dataguardians.sso.core.model.ProxyHost;
import io.dataguardians.sso.core.model.dto.HostSystemDTO;
import io.dataguardians.sso.core.model.dto.UserTypeDTO;
import io.dataguardians.sso.core.model.hostgroup.HostGroup;
import io.dataguardians.sso.core.model.security.UserType;
import io.dataguardians.sso.core.model.users.User;
import io.dataguardians.sso.core.repository.ConfigurationOptionRepository;
import io.dataguardians.sso.core.repository.HostGroupRepository;
import io.dataguardians.sso.core.repository.SystemRepository;
import io.dataguardians.sso.core.repository.UserRepository;
import io.dataguardians.sso.core.repository.UserTypeRepository;
import io.dataguardians.sso.core.security.service.CryptoService;
import io.dataguardians.sso.core.services.HostGroupService;
import io.dataguardians.sso.core.services.UserService;
import io.dataguardians.sso.install.configuration.InstallConfiguration;
import io.dataguardians.sso.install.configuration.dtos.HostGroupConfigurationDTO;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.thymeleaf.util.StringUtils;

@Component
@RequiredArgsConstructor
public class PostStartupTask {

    final SystemOptions systemOptions;

    final ConfigurationOptionRepository configurationOptionRepository;

    final UserRepository userRepository;

    final HostGroupRepository hostGroupRepository;

    final SystemRepository systemRepository;

    final UserTypeRepository  userTypeRepository;

    final UserService userService;
    private final HostGroupService hostGroupService;

    final CryptoService cryptoService;

    @EventListener(ApplicationReadyEvent.class)
    public void afterStartup() throws IOException, GeneralSecurityException, JSchException, SQLException {
        // Your logic here

        if (!StringUtils.isEmpty(systemOptions.getYamlConfiguration())) {

            var digestStream = new DigestInputStream(
                new FileInputStream(systemOptions.getYamlConfiguration()),
                MessageDigest.getInstance("SHA256")
            );
            MessageDigest digest = digestStream.getMessageDigest();
            var hash = new String(digest.digest());

            AtomicBoolean recreate = new AtomicBoolean(false);
            configurationOptionRepository.findByConfigurationName("yamlConfigurationFileHash")
                .ifPresentOrElse(
                    configurationOption -> {
                        if (!hash.equals(configurationOption.getConfigurationValue())) {
                            recreate.set(true);
                        }
                        configurationOption.setConfigurationValue(hash);
                        configurationOptionRepository.save(configurationOption);
                    },
                    () -> {
                        var configurationOption = new ConfigurationOption();
                        configurationOption.setConfigurationName("yamlHash");
                        configurationOption.setConfigurationValue(hash);
                        configurationOptionRepository.save(configurationOption);
                        recreate.set(true);
                    }
                );

            Boolean deleteFile = systemOptions.deleteYamlConfigurationFile;
            if (null == deleteFile) {
                deleteFile = true;
            }
            if (deleteFile) {
                Files.delete(Paths.get(systemOptions.getYamlConfiguration()));
            }

            if (recreate.get()) {

                var installConfiguration =
                    InstallConfiguration.fromYaml(new FileInputStream(systemOptions.getYamlConfiguration()));
                // recreate the database

                initialize(installConfiguration);
            }
        }
    }

    public void createStaticType(UserType type) throws SQLException, GeneralSecurityException {

        AtomicReference<Long> typeId = new AtomicReference<>();

        userTypeRepository.findByUserTypeName(type.getUserTypeName())
            .ifPresentOrElse(
                userType -> {
                    typeId.set( userType.getId());
                },
                () -> {
                    try {
                        typeId.set(userTypeRepository.save(type).getId());
                    } catch (Exception e) {
                        // ignore
                    }
                }
            );
    }


    public void initialize(InstallConfiguration installConfiguration)
        throws SQLException,
        GeneralSecurityException,
        JSchException,
        IOException {
        try {
            createStaticType(UserType.createSuperUser());
            createStaticType(UserType.createSystemAdmin());
            createStaticType(UserType.createUnknownUser());
        } catch (Exception e) {
            // ignore
        }

        // first we create the admin user, then the user types followed by all users
        createAdminUser(installConfiguration);

        createSystemUser(installConfiguration);

        var userTypes = createUserTypes(installConfiguration);

        // now it is time to configure the systems

        createSystems(installConfiguration);

        // create profiles and assign systems

        var profiles = createHostGroups(installConfiguration);

        createUsers(installConfiguration, userTypes, profiles);
        
        // create automation assignments

        //AppConfig.encryptProperty("initialized", Instant.now().toString());
    }


    private List<HostGroup> createHostGroups(InstallConfiguration installConfiguration)
        throws JSchException, GeneralSecurityException, IOException {
        List<HostGroup> profiles = new ArrayList<>();
        if (null != installConfiguration.getManagementGroups()) {
            for (HostGroupConfigurationDTO hostGroupDto : installConfiguration.getManagementGroups()) {
                var hostGroup = HostGroup.builder().name(hostGroupDto.getDisplayName()).description(hostGroupDto.getDescription()).build();
                hostGroup.setApplicationKey( cryptoService.generateKeyPair(UUID.randomUUID().toString()));


                profiles.add(hostGroup);
                if (null != hostGroupDto.getSystems()) {
                    List<HostSystem> systems = new ArrayList<>();
                    for (var system : hostGroupDto.getSystems()) {
                        for (var hostSystem : installConfiguration.getSystems()) {
                            if (hostSystem.getDisplayName().equals(system)) {
                                systems.add(HostSystemDTO.fromDTO(hostSystem));
                                break;
                            }
                        }
                    }
                    if (!systems.isEmpty()) {
                        hostGroup.setHostSystems(systems);
                    }
                }
                hostGroup = hostGroupRepository.save(hostGroup);
            }
        }
        return profiles;
    }

    private void createSystems(InstallConfiguration installConfiguration) throws SQLException, GeneralSecurityException {
        if (null != installConfiguration.getSystems()) {
            for (var system : installConfiguration.getSystems()) {
                var sys = systemRepository.save(HostSystemDTO.fromDTO(system));
                if (null != sys.getProxies()) {
                    var proxies = new ArrayList<ProxyHost>();
                    for (var proxy : sys.getProxies()) {
                        //proxies.add(ProxyDB.addProxyHost(proxy, system));
                    }
                    sys.setProxies(proxies);
                }
            }
        }
    }

    protected List<UserType> createUserTypes(InstallConfiguration installConfiguration) throws SQLException, GeneralSecurityException {
        List<UserType> types = new ArrayList<>();
        if (null != installConfiguration.getUserTypes()) {
            for (UserTypeDTO type : installConfiguration.getUserTypes()) {
                UserType newType = UserType.builder()
                    .userTypeName(type.getUserTypeName())
                    .ruleAccess(type.getRuleAccess())
                    .systemAccess(type.getSystemAccess())
                    .build();
                type.setId( userTypeRepository.save(type).getId() );
                types.add(type);
            }
        }
        return types;
    }

    protected List<User> createUsers(InstallConfiguration installConfiguration, List<UserType> userTypes,
                                     List<HostGroup> profiles)
        throws SQLException, GeneralSecurityException {
        List<User> users = new ArrayList<>();
        Map<Long, Set<Long>> assignments = new HashMap<>();
        if (null != installConfiguration.getUsers()) {
            for (var userDTO : installConfiguration.getUsers()) {

                // set the UserType from the configuration
                if (null != userDTO.getAuthorizationType()) {
                    for (UserType type : userTypes) {
                        if (type.getUserTypeName().equals(userDTO.getAuthorizationType().getUserTypeName())) {
                            userDTO.setAuthorizationType(type);
                            break;
                        }
                    }
                } else {
                    userDTO.setAuthorizationType(UserType.createSystemAdmin());
                }

                var user = userService.addUscer(User.from(userDTO));
                users.add(user);

                if (null != userDTO.getHostGroups()) {
                    for (HostGroup profile : user.getHostGroups()) {
                        for (HostGroup profile1 : profiles) {
                            if (profile1.getName().equals(profile.getName())) {
                                createOrUpdate(assignments, profile1.getId(), userDTO.getId());
                                break;
                            }
                        }
                    }
                }
            }
        }
        for (var entry : assignments.entrySet()) {

            //UserHostGroupDB.setUsersForHostGroup(entry.getKey(), entry.getValue());
        }
        return users;
    }

    static void createOrUpdate(Map<Long, Set<Long>> assignments, Long profileId, Long userId) {
        var set = assignments.computeIfAbsent(profileId, id -> new HashSet<>());
        set.add(userId);
    }

    protected void createAdminUser(InstallConfiguration installConfiguration) throws NoSuchAlgorithmException {

        var user = installConfiguration.getAdminUser();

        if (null == user) {
            throw new IllegalStateException("Admin user not found in configuration");
        }

        user.setPassword(userService.encodePassword( user.getPassword()));

        // insert default admin user
        user.setAuthorizationType(UserType.createSuperUser());

        userService.addUscer(User.from(user));

    }

    protected void createSystemUser(InstallConfiguration connection) throws NoSuchAlgorithmException {

        User user = User.builder()
            .id(0L)
            .authorizationType(UserType.createSuperUser())
            .username("SYSTEM")
            .password(UUID.randomUUID().toString() + "." + UUID.randomUUID().toString())
            .build();

        if (null == user) {
            throw new IllegalStateException("Admin user not found in configuration");
        }

        user.setPassword(userService.encodePassword( user.getPassword()));

        user.setAuthorizationType(UserType.createSuperUser());

        user = userService.addUscer(user);

    }
}
