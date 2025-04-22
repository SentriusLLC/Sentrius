package io.sentrius.sso.automation.factory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import com.google.common.base.Splitter;
import com.google.common.collect.ImmutableMap;
import io.sentrius.sso.automation.AutomationConfiguration;
import io.sentrius.sso.automation.AutomationPlugin;
import io.sentrius.sso.callbacks.ApplicationProperty;

/**
 * Utility class for managing automation configurations and properties.
 * Provides methods to retrieve base properties and configuration lists for automation plugins.
 */
public class AutomationUtil {

  // Cached list of automation configurations.
  static List<AutomationConfiguration> configurationList = null;
  // Cached map of base properties for automation configurations.
  static Map<String, Properties> basePropertiesList = null;

  /**
   * Retrieves the base properties associated with a specific automation configuration.
   *
   * @param longName The long name of the automation configuration.
   * @param appConfig The application property containing configurations.
   * @return The properties associated with the given long name, or an empty Properties object if none exist.
   * @throws ClassNotFoundException If a required class cannot be found.
   */
  public static Properties getBasePropertiesList(String longName, ApplicationProperty appConfig) throws ClassNotFoundException {
    if (null == basePropertiesList) {
      getConfigurationList(appConfig);
    }
    return basePropertiesList.getOrDefault(longName, new Properties());
  }

  /**
   * Retrieves the list of automation configurations from the application property.
   * Initializes the configuration list and base properties if not already done.
   *
   * @param appConfig The application property containing configurations.
   * @return A list of automation configurations.
   * @throws ClassNotFoundException If a required class cannot be found.
   */
  public static List<AutomationConfiguration> getConfigurationList(ApplicationProperty appConfig) throws ClassNotFoundException {
    if (null == configurationList) {
      synchronized (AutomationUtil.class) {
        configurationList = new ArrayList<>();
        basePropertiesList = new HashMap<>();
        var configName = "automation";

        // Load up to 10,000 automation configurations from the application properties.
        for (int i = 0; i < 10000; i++) {
          String option = configName + ".config." + Integer.valueOf(i).toString();
          var rule = appConfig.getProperty(option);
          if (null == rule) {
            break;
          }

          // Parse the configuration rule.
          String[] ruleSplit = rule.split(";");

          String shortName = null, longName, clazzName = null;
          if (ruleSplit.length >= 2) {
            shortName = ruleSplit[1].trim();
            longName = ruleSplit[0].trim() + ";" + shortName;
            clazzName = ruleSplit[0].trim();

            // Parse any additional properties for the configuration.
            if (ruleSplit.length == 3) {
              String properties = ruleSplit[2].trim();
              Splitter.on(",")
                  .split(properties)
                  .forEach(
                      property -> {
                        String[] propertySplit = property.split("=");
                        if (propertySplit.length == 2) {
                          if (null == basePropertiesList.get(longName)) {
                            basePropertiesList.put(longName, new Properties());
                          }
                          basePropertiesList
                              .get(longName)
                              .setProperty(propertySplit[0].trim(), propertySplit[1].trim());
                        }
                      });
            }
          } else {
            longName = null;
          }

          // Add the configuration to the list if all required elements are present.
          if (null != shortName && null != longName && null != clazzName) {
            configurationList.add(
                AutomationConfiguration.builder()
                    .shortName(shortName)
                    .longName(longName)
                    .clazz((Class<? extends AutomationPlugin>) Class.forName(clazzName))
                    .build());
          }
        }

        // Make the base properties map immutable.
        basePropertiesList = ImmutableMap.copyOf(basePropertiesList);
      }
    }
    return configurationList;
  }
}
