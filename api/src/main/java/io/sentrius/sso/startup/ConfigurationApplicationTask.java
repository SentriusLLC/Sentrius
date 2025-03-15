package io.sentrius.sso.startup;

import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.DigestInputStream;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.SQLException;
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
import io.sentrius.sso.automation.sideeffects.SideEffect;
import io.sentrius.sso.automation.sideeffects.SideEffectType;
import io.sentrius.sso.core.config.SystemOptions;
import io.sentrius.sso.core.model.ConfigurationOption;
import io.sentrius.sso.core.model.HostSystem;
import io.sentrius.sso.core.model.dto.HostGroupDTO;
import io.sentrius.sso.core.model.dto.HostSystemDTO;
import io.sentrius.sso.core.model.dto.UserTypeDTO;
import io.sentrius.sso.core.model.hostgroup.HostGroup;
import io.sentrius.sso.core.model.hostgroup.ProfileRule;
import io.sentrius.sso.core.model.security.UserType;
import io.sentrius.sso.core.model.security.enums.ApplicationAccessEnum;
import io.sentrius.sso.core.model.security.enums.AutomationAccessEnum;
import io.sentrius.sso.core.model.security.enums.RuleAccessEnum;
import io.sentrius.sso.core.model.security.enums.SSHAccessEnum;
import io.sentrius.sso.core.model.security.enums.UserAccessEnum;
import io.sentrius.sso.core.model.security.enums.ZeroTrustAccessTokenEnum;
import io.sentrius.sso.core.model.users.User;
import io.sentrius.sso.core.repository.ConfigurationOptionRepository;
import io.sentrius.sso.core.repository.HostGroupRepository;
import io.sentrius.sso.core.repository.SystemRepository;
import io.sentrius.sso.core.repository.UserRepository;
import io.sentrius.sso.core.repository.UserTypeRepository;
import io.sentrius.sso.core.services.security.CryptoService;
import io.sentrius.sso.core.services.HostGroupService;
import io.sentrius.sso.core.services.RuleService;
import io.sentrius.sso.core.services.UserService;
import io.sentrius.sso.install.configuration.InstallConfiguration;
import io.sentrius.sso.install.configuration.dtos.HostGroupConfigurationDTO;
import io.sentrius.sso.install.configuration.dtos.RuleDTO;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.thymeleaf.util.StringUtils;

@Component
@Slf4j
@RequiredArgsConstructor
public class ConfigurationApplicationTask {

    final SystemOptions systemOptions;

    final ConfigurationOptionRepository configurationOptionRepository;

    final UserRepository userRepository;

    final HostGroupRepository hostGroupRepository;

    final SystemRepository systemRepository;

    final UserTypeRepository  userTypeRepository;

    final RuleService ruleService;

    final UserService userService;
    private final HostGroupService hostGroupService;

    final CryptoService cryptoService;

    @EventListener(ApplicationReadyEvent.class)
    @Transactional
    public void afterStartup() throws IOException, GeneralSecurityException, JSchException, SQLException {
        // Your logic here

        if (!StringUtils.isEmpty(systemOptions.getYamlConfiguration())) {
            log.info("Checking for configuration file {}", systemOptions.getYamlConfiguration());
            var digestStream = new DigestInputStream(
                new FileInputStream(systemOptions.getYamlConfiguration()),
                MessageDigest.getInstance("SHA256")
            );
            MessageDigest digest = digestStream.getMessageDigest();
            var hash = new String(digest.digest());

            AtomicBoolean recreate = new AtomicBoolean(false);
            configurationOptionRepository.findLatestByConfigurationName("yamlConfigurationFileHash")
                .ifPresentOrElse(
                    configurationOption -> {
                        if (!hash.equals(configurationOption.getConfigurationValue())) {
                            log.info("Configuration file hash has changed, recreating database");
                            recreate.set(true);
                        }else {
                            log.info("Configuration file hash has not changed");
                        }
                        configurationOption.setConfigurationValue(hash);
                        configurationOptionRepository.save(configurationOption);
                    },
                    () -> {
                        log.info("No configuration file hash found, creating one");
                        var configurationOption = new ConfigurationOption();
                        configurationOption.setConfigurationName("yamlConfigurationFileHash");
                        configurationOption.setConfigurationValue(hash);
                        configurationOptionRepository.save(configurationOption);
                        recreate.set(true);
                    }
                );

            Boolean deleteFile = systemOptions.getDeleteYamlConfigurationFile();
            if (null == deleteFile) {
                deleteFile = true;
            }
            if (deleteFile) {
                Files.delete(Paths.get(systemOptions.getYamlConfiguration()));
            }

            if (recreate.get()) {
                log.info("Recreating database");
                var installConfiguration =
                    InstallConfiguration.fromYaml(new FileInputStream(systemOptions.getYamlConfiguration()));
                // recreate the database

                initialize(installConfiguration, true);
            }
        } else {
            log.info("No configuration file found");
        }
    }
    @Transactional
    public List<SideEffect> createStaticType(UserType type, boolean action) throws SQLException,
        GeneralSecurityException {

        AtomicReference<Long> typeId = new AtomicReference<>();

        List<SideEffect> sideEffects = new ArrayList<>();

        userTypeRepository.findByUserTypeName(type.getUserTypeName())
            .ifPresentOrElse(
                userType -> {
                    typeId.set( userType.getId());
                },
                () -> {
                    try {
                        sideEffects.add(SideEffect.builder().sideEffectDescription("Creating user type " + type.getUserTypeName()).type(
                            SideEffectType.UPDATE_DATABASE).asset("UserTypes").build());
                        if (action) {
                            typeId.set(userTypeRepository.save(type).getId());
                        }
                    } catch (Exception e) {
                        // ignore
                    }
                }
            );

        return sideEffects;
    }

    @Transactional
    public List<SideEffect> initialize(InstallConfiguration installConfiguration, boolean action)
        throws SQLException,
        GeneralSecurityException,
        JSchException,
        IOException {
        List<SideEffect> sideEffects = new ArrayList<>();
        try {
            sideEffects.addAll( createStaticType(UserType.createSuperUser(),action) );
            sideEffects.addAll( createStaticType(UserType.createSystemAdmin(), action) );
            sideEffects.addAll( createStaticType(UserType.createUnknownUser(), action ) );
        } catch (Exception e) {
            // ignore
        }

        // first we create the admin user, then the user types followed by all users
        sideEffects.addAll(createAdminUser(installConfiguration, action));

        createSystemUser(installConfiguration);

        var userTypes = createUserTypes(sideEffects, installConfiguration, action);

        // now it is time to configure the systems

        sideEffects.addAll( createSystems(installConfiguration, action) );

        // create profiles and assign systems

        var rules = createRules(sideEffects, installConfiguration, action);

        var profiles = createHostGroups(sideEffects, rules, installConfiguration, action);

        createUsers(sideEffects, installConfiguration, userTypes, profiles, action);



        // create automation assignments

        //AppConfig.encryptProperty("initialized", Instant.now().toString());

        return sideEffects;
    }
    @Transactional
    protected Map<String,ProfileRule> createRules(
        List<SideEffect> sideEffects, InstallConfiguration installConfiguration,
        boolean action
    ) {
        Map<String,ProfileRule> rules = new HashMap<>();
        var configuredRules = installConfiguration.getRules();
        for(RuleDTO rule : configuredRules) {
            ProfileRule newRule =
                ProfileRule.builder().ruleClass(rule.getRuleClass()).ruleName(rule.getDisplayName()).ruleConfig(rule.getConfiguration()).build();
            newRule = ruleService.saveRule(newRule);
            rules.put(rule.getDisplayName(), newRule);
        }
        return rules;
    }

    @Transactional
    protected List<HostGroup> createHostGroups(List<SideEffect> sideEffects, Map<String,ProfileRule> rules, InstallConfiguration installConfiguration,
                                             boolean action)
        throws JSchException, GeneralSecurityException, IOException {
        List<HostGroup> profiles = new ArrayList<>();
        if (null != installConfiguration.getManagementGroups()) {
            for (HostGroupConfigurationDTO hostGroupDto : installConfiguration.getManagementGroups()) {
                var hostGroup = HostGroup.builder().name(hostGroupDto.getDisplayName()).description(hostGroupDto.getDescription()).build();
                if (action) {
                    hostGroup.setApplicationKey(cryptoService.generateKeyPair(UUID.randomUUID().toString()));
                }

                boolean update = true;

                if (null != hostGroupDto.getSystems()) {
                    List<HostSystem> systems = new ArrayList<>();
                    for (var system : hostGroupDto.getSystems()) {
                        for (var hostSystem : installConfiguration.getSystems()) {
                            if (hostSystem.getDisplayName().equals(system)) {

                                var hsList = systemRepository.findByDisplayNameAndHost(hostSystem.getDisplayName(), hostSystem.getHost());
                                if (!hsList.isEmpty()) {
                                    update = true;
                                    hsList.forEach(systems::add);
                                }else {
                                    // it is possible that these are already assigned.
                                    log.info("Adding new systems to profile");
                                    systems.add(HostSystemDTO.fromDTO(hostSystem));
                                }
                                break;
                            }
                        }
                    }
                    if (!systems.isEmpty()) {
                        hostGroup.setHostSystems(systems);
                    }

                    for( var assignedRule : hostGroupDto.getAssignedRules() ) {
                        if (rules.containsKey(assignedRule)) {
                            var rule = rules.get(assignedRule);
                            hostGroup.getRules().add(rule);
                            rule.getHostGroups().add(hostGroup);
                        }
                    };
                }
                var hostGroups = hostGroupService.getHostGroupsByName(hostGroup.getName());
                if (hostGroups.isEmpty()) {
                    if (action) {

                        hostGroup = hostGroupRepository.save(hostGroup);
                        log.info("Creating Host Enclave {} with {}", hostGroup.getId(), hostGroupDto.getDisplayName());
                        profiles.add(hostGroup);
                        for(var hs : hostGroup.getHostSystems()) {
                            if (null == hs.getHostGroups()){
                                hs.setHostGroups(new ArrayList<>());
                            }

                            hs.getHostGroups().add(hostGroup);
                            systemRepository.save(hs);
                        }
                    }
                    sideEffects.add(SideEffect.builder().sideEffectDescription("Creating Host Enclave " + hostGroupDto.getDisplayName()).type(
                        SideEffectType.UPDATE_DATABASE).asset("HostGroups").build());
                }else {

                        boolean existsInHostGroup = false;


                            for(HostGroup hg : hostGroups) {
                                log.info("Updating Host Enclave {} with {}", hg.getId(), hg.getId());
                                profiles.add(hg);

                                for(var hs : hostGroup.getHostSystems()) {
                                    if (!systemRepository.isAssignedToHostGroups(hs.getId(), List.of( hg.getId()))) {
                                        if (action) {
                                        hs.getHostGroups().add(hg);
                                        systemRepository.save(hs);
                                        log.info("Updating Host Enclave {} with {}", hg.getId(), hs.getId());
                                    }

                                        sideEffects.add(SideEffect.builder()
                                            .sideEffectDescription("Updating Host Enclave " + hostGroupDto.getDisplayName()).type(
                                                SideEffectType.UPDATE_DATABASE).asset("HostGroups").build());
                                }
                            }
                        }

                }

            }
        }
        return profiles;
    }

    @Transactional
    protected List<SideEffect> createSystems(InstallConfiguration installConfiguration, boolean action) throws SQLException,
        GeneralSecurityException {
        List<SideEffect> sideEffects = new ArrayList<>();
        if (null != installConfiguration.getSystems()) {
            for (var system : installConfiguration.getSystems()) {
                var systemObj = HostSystemDTO.fromDTO(system);
                if ( shouldInsertSystem(systemObj)) {
                    if (action) {
                        var sys = systemRepository.save(systemObj);
                    }
                    sideEffects.add(
                        SideEffect.builder().sideEffectDescription("Creating system " + system.getDisplayName()).type(
                            SideEffectType.UPDATE_DATABASE).asset("Systems").build());
                }
            }
        }
        return sideEffects;
    }

    @Transactional
    protected boolean shouldInsertSystem(HostSystem systemObj) {
        var systems = systemRepository.findByDisplayName(systemObj.getDisplayName());
        if (systems.isEmpty()) {
            return true;
        }
        for(HostSystem system : systems) {
            if (system.getHost().equals(systemObj.getHost()) && system.getPort() == systemObj.getPort()) {
                return false;
            }
        }
        return true;
    }

    @Transactional
    protected List<UserType> createUserTypes(List<SideEffect> sideEffects, InstallConfiguration installConfiguration,
                                             boolean action) throws SQLException, GeneralSecurityException {
        List<UserType> types = new ArrayList<>();
        if (null != installConfiguration.getUserTypes()) {
            for (UserTypeDTO type : installConfiguration.getUserTypes()) {
                var builder = UserType.builder();
                if (null != type.getRuleAccess()){
                    builder.ruleAccess(RuleAccessEnum.of(List.of(type.getRuleAccess())));
                }
                if (null != type.getSystemAccess()){
                    builder.systemAccess(SSHAccessEnum.of(List.of(type.getSystemAccess())));
                }
                if (null != type.getUserAccess()){
                    builder.userAccess(UserAccessEnum.of(List.of(type.getUserAccess())));
                }
                if (null != type.getAutomationAccess()){
                    builder.automationAccess(AutomationAccessEnum.of(List.of(type.getAutomationAccess())));
                }
                if (null != type.getZtAccessTokenAccess()){
                    builder.ztAccessTokenAccess(ZeroTrustAccessTokenEnum.of(List.of(type.getZtAccessTokenAccess())));
                }

                if (null != type.getApplicationAccess()){
                    builder.applicationAccess(ApplicationAccessEnum.of(List.of(type.getApplicationAccess())));
                }

                UserType newType = builder.userTypeName(type.getUserTypeName()).build();
                userTypeRepository.findByUserTypeName(type.getUserTypeName())
                    .ifPresentOrElse(
                        userType -> {
                            newType.setId( userType.getId());
                        },
                        () -> {
                            if (action){
                            newType.setId( userTypeRepository.save(newType).getId() );
                            }
                            sideEffects.add(
                                SideEffect.builder().sideEffectDescription("Creating user type " + type.getUserTypeName()).type(
                                    SideEffectType.UPDATE_DATABASE).asset("UserTypes").build()
                            );
                        }
                    );
                //type.setId( userTypeRepository.save(newType).getId() );
                types.add(newType);
            }
        }
        return types;
    }

    @Transactional
    protected List<User> createUsers(
        List<SideEffect> sideEffects, InstallConfiguration installConfiguration, List<UserType> userTypes,
        List<HostGroup> profiles, boolean action)
        throws SQLException, GeneralSecurityException {
        List<User> users = new ArrayList<>();
        Map<Long, Set<Long>> assignments = new HashMap<>();
        if (null != installConfiguration.getUsers()) {
            for (var userDTO : installConfiguration.getUsers()) {

                // set the UserType from the configuration
                if (null != userDTO.getAuthorizationType()) {
                    for (UserType type : userTypes) {
                        if (type.getUserTypeName().equals(userDTO.getAuthorizationType().getUserTypeName())) {
                            userDTO.setAuthorizationType(new UserTypeDTO(type));
                            break;
                        }
                    }
                } else {
                    userDTO.setAuthorizationType(new UserTypeDTO( UserType.createSystemAdmin()));
                }

                User user = User.from(userDTO);
                User finalUser = user;
                userService.findByUsername(user.getUsername()).ifPresentOrElse(
                    user1 -> {
                        finalUser.setId(user1.getId());
                    },
                    () -> {
                        if (action) {
                            var usr  = userService.addUscer(user);
                            user.setId(usr.getId());
                        }
                        sideEffects.add(SideEffect.builder().sideEffectDescription("Creating user " + userDTO.getUsername()).type(
                            SideEffectType.UPDATE_DATABASE).asset("Users").build());
                    }
                );

                users.add(user);

                if (null != userDTO.getHostGroups()) {
                    if (action) {
                        for (HostGroupDTO profile : userDTO.getHostGroups()) {
                            for (HostGroup hostGroup : profiles) {
                                if (hostGroup.getName().equals(profile.getDisplayName())) {
                                    if (!userRepository.isAssignedToHostGroups(user.getId(), List.of(hostGroup.getId()))) {


                                    sideEffects.add(SideEffect.builder().sideEffectDescription(
                                        "Assigning user " + userDTO.getUsername() + " to Host Enclave " +
                                            hostGroup.getName()).type(
                                        SideEffectType.UPDATE_DATABASE).asset("Users").build());
                                        log.info("Assigning user {} to Host Enclave {}", userDTO.getUsername(),
                                        hostGroup.getId());
                                        user.getHostGroups().add(hostGroup);
                                    }
                                    break;
                                }
                            }
                            if (null != user.getPassword() ) {
                                user.setPassword(userService.encodePassword(user.getPassword()));
                            }
                            userRepository.save(user);
                        }
                    }
                    else {
                        for (var profile : userDTO.getHostGroups()) {
                            for (HostGroup hostGroup : profiles) {
                                if (hostGroup.getName().equals(profile.getDisplayName())) {
                                    log.info("Assigning user {} to Host Enclave {}", userDTO.getUsername(),
                                        hostGroup.getId());
                                    if (null == hostGroup.getId() || !userRepository.isAssignedToHostGroups(user.getId(),
                                        List.of(hostGroup.getId()))) {
                                        sideEffects.add(SideEffect.builder().sideEffectDescription(
                                            "Assigning user " + userDTO.getUsername() + " to Host Enclave " +
                                                hostGroup.getName()).type(
                                            SideEffectType.UPDATE_DATABASE).asset("Users").build());
                                        log.info("Assigning user {} to Host Enclave {}", userDTO.getUsername(),
                                            hostGroup.getId()
                                        );
                                    }
                                    break;
                                }
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


    @Transactional
    protected List<SideEffect> createAdminUser(InstallConfiguration installConfiguration, boolean action) throws NoSuchAlgorithmException {

        var user = installConfiguration.getAdminUser();

        if (null == user) {
            throw new IllegalStateException("Admin user not found in configuration");
        }
        List<SideEffect> sideEffects = new ArrayList<>();
        userService.findByUsername(user.getUsername()).ifPresentOrElse(
            user1 -> {
                // ignore
            },
            () -> {
                sideEffects.add(SideEffect.builder().sideEffectDescription("Creating admin user " + user.getUsername()).type(
                    SideEffectType.UPDATE_DATABASE).asset("Users").build());
                if (action) {
                    try {
                        user.setPassword(userService.encodePassword(user.getPassword()));
                        user.setAuthorizationType(new UserTypeDTO(UserType.createSuperUser()));

                        userService.addUscer(User.from(user));
                    } catch (NoSuchAlgorithmException e) {
                        throw new RuntimeException(e);
                    }

                    // insert default admin user

                }
            }
        );


        return sideEffects;
    }

    @Transactional
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

        userService.findByUsername(user.getUsername()).ifPresentOrElse(
            user1 -> {
                // ignore
            },
            () -> {
                userService.addUscer(user);
            }
        );



    }
}
