package io.sentrius.sso.automation.factory;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.Properties;
import io.sentrius.sso.automation.AutomationPlugin;
import io.sentrius.sso.callbacks.ApplicationProperty;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class PluginFactory {

  // List of script names that are not supported for instantiation.
  static List<String> scriptNames = Arrays.asList("shell", "script", "bash");

  /**
   * Creates a new instance of an AutomationPlugin given its long name.
   *
   * @param longName The long name of the plugin (in the format "ClassName;ShortName").
   * @return A new instance of the specified AutomationPlugin, or null if the class is a script.
   */
  public static AutomationPlugin createNewInstance(String longName) {
    String[] names = longName.split(";");
    if (names.length > 2) {
      throw new RuntimeException("Invalid longName " + longName);
    }
    String className = names[0].trim();

    // Skip creating an instance if the class name matches a script type.
    if (scriptNames.contains(className.toLowerCase())) {
      return null;
    }

    AutomationPlugin newInstance = null;
    try {
      // Load the class by name and create a new instance using the default constructor.
      Class<? extends AutomationPlugin> clazz =
          Class.forName(className).asSubclass(AutomationPlugin.class);
      newInstance = clazz.getConstructor().newInstance();
    } catch (ClassNotFoundException e) {
      log.error("Error with " + className, e);
      throw new RuntimeException(e);
    } catch (InvocationTargetException e) {
      log.error("Error with " + className, e);
      throw new RuntimeException(e);
    } catch (InstantiationException e) {
      log.error("Error with " + className, e);
      throw new RuntimeException(e);
    } catch (IllegalAccessException e) {
      log.error("Error with " + className, e);
      throw new RuntimeException(e);
    } catch (NoSuchMethodException e) {
      log.error("Error with " + className, e);
      throw new RuntimeException(e);
    }

    return newInstance;
  }

  /**
   * Creates a new instance of an AutomationPlugin and configures it with the provided properties.
   *
   * @param appConfig The application property containing configurations.
   * @param longName The long name of the plugin (in the format "ClassName;ShortName").
   * @param properties A string representation of properties to configure the plugin.
   * @return A configured instance of the specified AutomationPlugin.
   */
  public static AutomationPlugin createNewInstance(ApplicationProperty appConfig, String longName, String properties) {
    String[] names = longName.split(";");
    if (names.length != 2) {
      throw new RuntimeException("Invalid longName " + longName);
    }
    String className = names[0];
    AutomationPlugin newInstance = null;
    try {
      // Create a new instance of the plugin.
      newInstance = createNewInstance(longName);

      // Load the base properties and add the additional properties provided.
      Properties props = AutomationUtil.getBasePropertiesList(longName, appConfig);
      props.load(new ByteArrayInputStream(properties.getBytes(StandardCharsets.UTF_8)));

      // Configure the plugin with the properties.
      newInstance.configure(props);
    } catch (ClassNotFoundException e) {
      log.error("Error with " + className, e);
      throw new RuntimeException(e);
    } catch (IOException e) {
      log.error("Error with " + className, e);
      throw new RuntimeException(e);
    }

    return newInstance;
  }
}
