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

    private static final String DYNAMIC_CONFIG_PATH = "dynamic.properties";
    private final Properties properties = new Properties();
    private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();
    final ConfigurationOptionRepository configurationOptionRepository;

    public ThreadSafeDynamicPropertiesService(ConfigurationOptionRepository configurationOptionRepository,
                                              @Value("$" +
        "{dynamic.properties.path:/config/dynamic.properties}") String configLocation) throws IllegalAccessException {
        this.configLocation = configLocation;
        this.configurationOptionRepository = configurationOptionRepository;
        loadProperties();
    }

    @PostConstruct
    public void logConfigLocation() {
        log.info("*** Dynamic Properties Path: " + configLocation);
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

    // Updates a property if it's in the allowed list
    public void updateProperty(String key, String value) throws IOException {
        lock.writeLock().lock();
        try{
            configurationOptionRepository.save(ConfigurationOption.builder().configurationName(key).configurationValue(null == value ? "" : value).build());
        } finally {
            lock.writeLock().unlock();
        }
    }


    public String getProperty(String key, String defaultValue) {
        lock.readLock().lock();
        try {
            var dbOption = configurationOptionRepository.findLatestByConfigurationName(key);
            if (dbOption.isEmpty()) {
                return properties.getProperty(key, defaultValue);
            }
            return dbOption.get().getConfigurationValue();
        } finally {
            lock.readLock().unlock();
        }
    }

}
