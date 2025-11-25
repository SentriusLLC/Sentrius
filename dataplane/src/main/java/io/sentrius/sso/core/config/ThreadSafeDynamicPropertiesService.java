package io.sentrius.sso.core.config;

import io.sentrius.sso.core.model.ConfigurationOption;
import io.sentrius.sso.core.repository.ConfigurationOptionRepository;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Properties;
import java.util.concurrent.locks.ReentrantReadWriteLock;

@Slf4j
@Component
public class ThreadSafeDynamicPropertiesService {

    private final String configLocation;
    private final String podName;

    private static final String DYNAMIC_CONFIG_PATH = "dynamic.properties";
    private final Properties properties = new Properties();
    private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();
    final ConfigurationOptionRepository configurationOptionRepository;

    public ThreadSafeDynamicPropertiesService(ConfigurationOptionRepository configurationOptionRepository,
                                              @Value("$" +
        "{dynamic.properties.path:/config/dynamic.properties}") String configLocation,
                                              @Value("${sentrius.pod.name:${HOSTNAME:#{null}}}") String podName) throws IllegalAccessException {
        this.configLocation = configLocation;
        this.podName = podName;
        this.configurationOptionRepository = configurationOptionRepository;
        loadProperties();
    }

    @PostConstruct
    public void logConfigLocation() {
        log.info("*** Dynamic Properties Path: " + configLocation);
        log.info("*** Pod Name: " + (podName != null ? podName : "not set (global)"));
    }

    private String getDynamicPropertiesPath() {
        Path basePath = Paths.get(this.getClass()
                .getClassLoader()
                .getResource("application.properties")
                .getPath())
            .getParent();
        return basePath.resolve("dynamic.properties").toString();
    }

    // Load properties from the file
    private void loadProperties() {
        lock.writeLock().lock();
        var path = configLocation;
        try{
        try {
            if (null == configLocation || configLocation.isEmpty()) {
                log.info("No dynamic properties path provided, using default");
                path = getDynamicPropertiesPath();
            } else {
                log.info("Using dynamic properties path provided {}", configLocation);
                path = Paths.get(configLocation).toString();
            }
        }catch(Exception e){
            log.error("Error getting dynamic properties path This may be an ignorable error during dev/test.", e);
            return;
        }

        log.info("Properties path is {}" , path);
        try (FileInputStream in = new FileInputStream(path)) {
            properties.load(in);
        } catch (Exception e) {
            log.error("Error loading dynamic properties. This may be an ignorable error during dev/test.", e);
        }
        } finally {
            lock.writeLock().unlock();
        }
    }

    // Updates a property if it's in the allowed list (uses current pod name)
    public void updateProperty(String key, String value) throws IOException {
        updateProperty(podName, key, value);
    }

    // Updates a property for a specific pod
    public void updateProperty(String targetPodName, String key, String value) throws IOException {
        lock.writeLock().lock();
        try{
            configurationOptionRepository.save(ConfigurationOption.builder()
                .podName(targetPodName)
                .configurationName(key)
                .configurationValue(null == value ? "" : value)
                .build());
        } finally {
            lock.writeLock().unlock();
        }
    }

    // Gets property for the current pod (falls back to global if not found)
    public String getProperty(String key, String defaultValue) {
        return getProperty(podName, key, defaultValue);
    }

    // Gets property for a specific pod (falls back to global if not found)
    public String getProperty(String targetPodName, String key, String defaultValue) {
        lock.readLock().lock();
        try {
            // First try pod-specific override
            if (targetPodName != null) {
                var podOption = configurationOptionRepository.findLatestByPodNameAndConfigurationName(targetPodName, key);
                if (podOption.isPresent()) {
                    return podOption.get().getConfigurationValue();
                }
            }
            
            // Fall back to global override
            var globalOption = configurationOptionRepository.findLatestGlobalByConfigurationName(key);
            if (globalOption.isPresent()) {
                return globalOption.get().getConfigurationValue();
            }
            
            // Fall back to file properties
            return properties.getProperty(key, defaultValue);
        } finally {
            lock.readLock().unlock();
        }
    }

    /**
     * Get the current pod name.
     * 
     * @return The pod name or null if not set
     */
    public String getPodName() {
        return podName;
    }

}
