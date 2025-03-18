package io.sentrius.sso.core.config;

import java.io.IOException;
import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Map;
import io.sentrius.sso.core.annotations.RequiresRestart;
import io.sentrius.sso.core.annotations.Updatable;
import io.sentrius.sso.core.dto.SystemOption;
import jakarta.annotation.PostConstruct;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/** Purpose: Centralizes a POJO for options with sensible defaults. */
@Slf4j
@Builder
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Component
public class SystemOptions {


  @Autowired
  private ThreadSafeDynamicPropertiesService dynamicPropertiesService;

  @Builder.Default public String systemLogoName = "Sentrius";

  @Builder.Default public String systemLogoPathSmall = "/images/sentrius_small.png";

  @Builder.Default public String systemLogoPathLarge = "/images/sentrius_large.jpg";

  @Builder.Default public String systemTopBanner = "";

  @Builder.Default public String systemTopBannerClass = "";

  /** Full admin can login. */
  @Builder.Default public Boolean fullAdminCanLogin = true;

  @Builder.Default public Boolean ztatRequiresTicket = false;

  @Builder.Default public Integer approvedJITPeriod = 60;

  @Builder.Default public Boolean allowProxies = true;

  @Builder.Default public String auditorClass = "io.sentrius.sso.automation.auditing.RuleAlertAuditor";

  @Builder.Default public Integer automationThreads = 10;

  @Builder.Default public Integer automationStateRefreshSecs = 60;

  @Builder.Default public Boolean allowInsecureCookies = false;

  @Builder.Default public Boolean requireProfileForLogin = true;

  @Builder.Default public Integer maxJitUses = 1;

  /**
   * This is how long before a ztat request ( that has been denied or approved ) can last.
   */
  @Builder.Default public Integer maxJitDurationMs = (1440 * 1000); // 60 min * 24 hrs * 1000 ms

  @Builder.Default public int sessionLogThreadPoolSize = 1;

  @Builder.Default public Boolean enableInternalAudit = true;

  @Builder.Default public Integer auditFlushIntervalMs = 5000;

  @Builder.Default
  public String knownHostsPath = System.getProperty("user.home") + "/.ssh/known_hosts";

  @Builder.Default public Boolean terminalsInNewTab = true;

  @Builder.Default public Boolean testMode = false;

  @Builder.Default public String defaultUserTypeName = "";

  @Builder.Default
  public Integer globalCacheExpirationMinutes = 1440; // 24 hours

  public String systemBanner = "";

  public Boolean agentForwarding = false;

  public Boolean keyManagementEnabled = true;

  public Integer serverAliveInterval = 60;

  public String yamlConfiguration = "";

  public Boolean deleteYamlConfigurationFile = false;

  public Boolean allowUploadSystemConfiguration = false;

  public Boolean enableLLMQuestions = false;


  public Boolean sshEnabled = true;

  public Double aiRiskThreshold = 0.8;

  public Integer commandsToBuffer = 10;

    public Integer commandsToEvaluate = 5;

  /**
   * Purely for testing mode
   */
  public Boolean canApproveOwnZtat = false;


  // the default path may be sufficient
  public String uploadPath;
  public String sshKeyType = "rsa";

  public String getUploadPath() {
    if (null == uploadPath || uploadPath.isEmpty()){
      // since these are loaded at startup we will get an NPE if we attempt to set
      // this as the default value.
      return SystemOptions.class.getClassLoader().getResource(".").getPath() + "." +
              "./upload";
    }
    return uploadPath;
  }

  @PostConstruct
  private void init() throws IllegalAccessException {
    Field[] fields = getClass().getDeclaredFields();
    for (Field field : fields) {
      field.setAccessible(true); // Allow access to private fields
      // Only process fields with non-null defaults or initialize with a default if null
      String defaultValue = field.get(this) != null ? field.get(this).toString() : "";
      String propertyValue = dynamicPropertiesService.getProperty(field.getName(), defaultValue);

      // Convert propertyValue to the field's actual \ if necessary
      if (field.getType() == Boolean.class || field.getType() == boolean.class) {
        field.set(this, Boolean.parseBoolean(propertyValue));
      } else if (field.getType() == Integer.class || field.getType() == int.class) {
        field.set(this, Integer.parseInt(propertyValue));
      } else if (field.getType() == String.class) {
        field.set(this, propertyValue);
      }
      // Add additional type checks as needed
    }
  }

  /**
   * Updates the value of a specific system option by reflecting on the field name.
   * It also updates the application configuration file based on the new value.
   *
   * @param fieldName  the name of the field to update
   * @param fieldValue the new value to set for the field
   * @return true if the field was successfully updated, false otherwise.
   */
  public boolean setValue(String fieldName, Object fieldValue, boolean save){
    Field[] fields = this.getClass().getDeclaredFields();
    for (var field : fields) {
      if (field.getName().equalsIgnoreCase(fieldName)) {
        log.debug("Setting field {} to {}", fieldName, fieldValue);
        try {
          field.set(this, fieldValue);

          // Update the AppConfig with the new field value
          dynamicPropertiesService.updateProperty(fieldName, fieldValue.toString());
          log.debug("Set field {} to {}", fieldName, fieldValue);
          return true;
        } catch (IllegalAccessException e) {
          log.error("Failed to update field {}", fieldName);
          return false;
        } catch (IOException e) {
          log.error("Failed to update field {}", fieldName);
            throw new RuntimeException(e);
        }
      }
    }
    return false;
  }

  public boolean setValue(String fieldName, Object fieldValue){
    return setValue(fieldName, fieldValue, true);
  }

  /**
   * Retrieves the system options marked as updatable and returns them as a map of field names to SystemOption objects.
   * Each field is checked for annotations such as @Updatable and @RequiresRestart to set the necessary attributes.
   *
   * @return a Map of system option field names and corresponding SystemOption objects.
   * @throws IllegalAccessException if there is an issue accessing the field value via reflection.
   */
  public Map<String, SystemOption> getOptions() throws IllegalAccessException {
    // Retrieve all fields from the system options class
    Field[] fields = getClass().getDeclaredFields();

    // Map to store the updatable fields with their respective SystemOption objects
    Map<String, SystemOption> entries = new HashMap<>();
    for (Field field : fields) {
      // Check if the field is marked with the @Updatable annotation
      if (field.isAnnotationPresent(Updatable.class)) {
        boolean requiresRestart = false;

        // Check if the field requires a system restart when updated
        if (field.isAnnotationPresent(RequiresRestart.class)) {
          requiresRestart = true;
        }

        String fieldName = field.getName();
        Object fieldValue = field.get(this);

        log.trace("Field: {} Value: {}", fieldName, fieldValue);

        // Create a SystemOption object with the field details
        var sysOpt = SystemOption.builder()
            .name(fieldName)
            .value(fieldValue == null ? "" : String.valueOf(fieldValue))
            .requiresRestart(requiresRestart);

        // Set the description if available in the annotation
        var desc = field.getAnnotation(Updatable.class).description();
        if (null != desc && !desc.isEmpty()) {
          sysOpt = sysOpt.description(desc);
        }

        // Set the closest data type of the field if it's not a primitive type
        if (!field.getType().isPrimitive()) {
          sysOpt.closestType(field.getType().getCanonicalName());
        }

        // Add the field to the map of system options
        entries.put(fieldName, sysOpt.build());
      }
    }
    return entries;
  }
}
