package io.dataguardians.sso.install.configuration;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class DatabaseConfiguration {

  /*
  private final InstallConfiguration installConfiguration;

  public DatabaseConfiguration(InputStream inputStream) throws IOException {
    this.installConfiguration = InstallConfiguration.fromYaml(inputStream);
  }

  public void initialize(Connection connection)
      throws SQLException,
          ConfigurationException,
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

    var initialized = AppConfig.decryptProperty("initialized");

    if (null != initialized) {
      // cannot achieve a desired state. This is a one time operation
      System.out.println("System already initialized");
      return;
    }

    // first we create the admin user, then the user types followed by all users
    createAdminUser(connection);

    createSystemUser(connection);

    var userTypes = createUserTypes();

    // now it is time to configure the systems

    createSystems();

    // create profiles and assign systems

    var profiles = createProfiles();

    createUsers(userTypes, profiles);

    createKeys(connection);

    // create automation

    createAutomation();

    // create automation assignments

    AppConfig.encryptProperty("initialized", Instant.now().toString());
  }

  private void createAutomation() throws SQLException, GeneralSecurityException, IOException {

    List<Automation> configuredAutomations = installConfiguration.getAutomation();
    Map<String, Automation> automationRef = new HashMap<>();
    for (var automation : configuredAutomations) {
      String[] nameSplit = automation.getType().split(";");
      if (nameSplit.length != 2 && !automation.getType().equals("script")) {
        automation.setType(automation.getType() + ";" + automation.getDisplayNm());
      }

      if (automation.getType().equals("script")) {

      } else {
        Properties props = new Properties();
        automation
            .getAutomationOptions()
            .forEach(
                (k, v) -> {
                  props.put(k, v);
                });

        StringWriter writer = new StringWriter();
        props.store(writer, null);

        automation.setScript(writer.toString());
      }
      ScriptDB.insertScript(automation, installConfiguration.getAdminUser().getId());
      automationRef.put(automation.getDisplayNm(), automation);
    }

    List<ScriptAssignment> assignments = installConfiguration.getAutomationAssignments();

    for (var assignment : assignments) {
      Automation automation = automationRef.get(assignment.getScript().getDisplayNm());
      if (null == automation) {
        log.error(
            "Automation reference invalid, skipping assignment of "
                + assignment.getScript().getDisplayNm());
        continue;
      }
      List<Long> systemIds = new ArrayList<>();
      for (String systemName : assignment.getSystems()) {
        for (HostSystem system : installConfiguration.getSystems()) {
          if (system.getDisplayNm().equals(systemName)) {
            systemIds.add(system.getId());
          }
        }
      }
      assignment.setSystemIds(systemIds);

      ScriptDB.insertAssignments(automation, assignment, assignment.getCron());

      List<Long> userIds = new ArrayList<>();
      for (String userName : assignment.getUsers()) {
        for (User user : installConfiguration.getUsers()) {
          if (user.getUsername().equals(userName)) {
            userIds.add(user.getId());
          }
        }
        if (!userIds.isEmpty()) {
          ScriptDB.shareSript(automation, userIds);
        }
      }

      // get users
      // get systems

    }
  }

  private List<Profile> createProfiles()
      throws SQLException, GeneralSecurityException, IOException {
    List<Profile> profiles = new ArrayList<>();
    if (null != installConfiguration.getManagementGroups()) {
      for (Profile profile : installConfiguration.getManagementGroups()) {
        profile.setId(ProfileDB.insertProfile(profile));
        if (null != profile.getConfiguration()) {
          ProfileDB.saveConfiguration(profile.getConfiguration());
        }
        profiles.add(profile);
        if (null != profile.getHostSystemList()) {
          List<Long> systems = new ArrayList<>();
          for (var system : profile.getHostSystemList()) {
            for (var hostSystem : installConfiguration.getSystems()) {
              if (hostSystem.getDisplayNm().equals(system.getDisplayNm())) {
                systems.add(hostSystem.getId());
                break;
              }
            }
          }
          if (!systems.isEmpty()) {
            ProfileSystemsDB.setSystemsForProfile(profile.getId(), systems);
          }
        }
      }
    }
    return profiles;
  }

  private void createKeys(Connection connection)
      throws JSchException,
          SQLException,
          ConfigurationException,
          GeneralSecurityException,
          IOException {
    DBInitializer.regenerateKeys(connection);
  }

  private void createSystems() throws SQLException, GeneralSecurityException {
    if (null != installConfiguration.getSystems()) {
      for (var system : installConfiguration.getSystems()) {
        system.setId(SystemDB.insertSystem(system));
        if (null != system.getProxies()) {
          var proxies = new ArrayList<ProxyHost>();
          for (var proxy : system.getProxies()) {
            proxies.add(ProxyDB.addProxyHost(proxy, system));
          }
          system.setProxies(proxies);
        }
      }
    }
  }

  protected List<UserType> createUserTypes() throws SQLException, GeneralSecurityException {
    List<UserType> types = new ArrayList<>();
    if (null != installConfiguration.getUserTypes()) {
      for (UserType type : installConfiguration.getUserTypes()) {
        type.setId(UserDB.insertType(type));
        types.add(type);
      }
    }
    return types;
  }

  protected List<User> createUsers(List<UserType> userTypes, List<Profile> profiles)
      throws SQLException, GeneralSecurityException {
    List<User> users = new ArrayList<>();
    Map<Long, Set<Long>> assignments = new HashMap<>();
    if (null != installConfiguration.getUsers()) {
      for (User user : installConfiguration.getUsers()) {

        // set the UserType from the configuration
        if (null != user.getAuthorizationType()) {
          for (UserType type : userTypes) {
            if (type.getUserTypeName().equals(user.getAuthorizationType().getUserTypeName())) {
              user.setAuthorizationType(type);
              break;
            }
          }
        } else {
          user.setAuthorizationType(UserType.createSystemAdmin());
        }

        user.setId(UserDB.insertUser(user));
        users.add(user);

        if (null != user.getProfileList()) {
          for (Profile profile : user.getProfileList()) {
            for (Profile profile1 : profiles) {
              if (profile1.getNm().equals(profile.getNm())) {
                createOrUpdate(assignments, profile1.getId(), user.getId());
                break;
              }
            }
          }
        }
      }
    }
    for (var entry : assignments.entrySet()) {
      UserProfileDB.setUsersForProfile(entry.getKey(), entry.getValue());
    }
    return users;
  }

  static void createOrUpdate(Map<Long, Set<Long>> assignments, Long profileId, Long userId) {
    var set = assignments.computeIfAbsent(profileId, id -> new HashSet<>());
    set.add(userId);
  }

  protected void createAdminUser(Connection connection)
      throws SQLException, GeneralSecurityException, ConfigurationException {

    User user = installConfiguration.getAdminUser();

    if (null == user) {
      throw new IllegalStateException("Admin user not found in configuration");
    }

    String salt = EncryptionUtil.generateSalt();

    String defaultPassword = EncryptionUtil.hash(user.getPassword() + salt);

    String initialPassword = AppConfig.getProperty("initialPassword");

    if (null != initialPassword && !AppConfig.isPropertyEncrypted("initialPassword")) {
      defaultPassword = EncryptionUtil.hash(initialPassword + salt);
      AppConfig.encryptProperty("initialPassword", AppConfig.getProperty("initialPassword"));
    }

    // insert default admin user
    user.setUserType(User.MANAGER);
    user.setAuthorizationType(UserType.createSuperUser());
    try {
      user.setId(UserDB.insertUser(connection, user));
    } catch (Exception e) {
      log.error("Error creating admin user. Likely a duplicate", e);
    }

    user = UserDB.getUser(1L);
  }

  protected void createSystemUser(Connection connection)
      throws SQLException, GeneralSecurityException, ConfigurationException {

    User user = User.builder()
        .id(0L)
        .authorizationType(UserType.createSuperUser())
        .username("SYSTEM")
        .password(UUID.randomUUID().toString() + "." + UUID.randomUUID().toString())
        .build();

    if (null == user) {
      throw new IllegalStateException("Admin user not found in configuration");
    }

    String salt = EncryptionUtil.generateSalt();

    String defaultPassword = EncryptionUtil.hash(user.getPassword() + salt);

    user.setPassword(defaultPassword);
    user.setId(-1L);
    // insert default admin user
    user.setUserType(User.MANAGER);
    user.setAuthorizationType(UserType.createSuperUser());
    try {
      user.setId(UserDB.insertDefinedUser(connection, user));
    } catch (Exception e) {
      log.error("Error creating admin user. Likely a duplicate", e);
    }

  }

   */
}
