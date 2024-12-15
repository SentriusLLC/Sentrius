package io.dataguardians.sso.core.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import java.io.FileOutputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Properties;
import java.util.concurrent.locks.ReentrantReadWriteLock;

@Slf4j
@Service
public class ThreadSafeDynamicPropertiesService {

    @Value("${dynamic.properties.path}")
    private String configLocation;

    private static final String DYNAMIC_CONFIG_PATH = "dynamic.properties";
    private final Properties properties = new Properties();
    private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();

    public ThreadSafeDynamicPropertiesService() throws IllegalAccessException {
        loadProperties();
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
        if (null == configLocation || configLocation.isEmpty()) {
            path = getDynamicPropertiesPath();
        }
        else {
            path = Paths.get(configLocation).toString();
        }

        log.info("Properties path is {}" , path);
        try (FileInputStream in = new FileInputStream(path)) {
            properties.load(in);
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            lock.writeLock().unlock();
        }
    }

    // Updates a property if it's in the allowed list
    public void updateProperty(String key, String value) throws IOException {

        lock.writeLock().lock();
        try (FileOutputStream out = new FileOutputStream(DYNAMIC_CONFIG_PATH)) {
            if (null == value || value.isEmpty()) {
                properties.remove(key);
            }else {
                properties.setProperty(key, value);
            }
            properties.store(out, null);
        } finally {
            lock.writeLock().unlock();
        }
    }


    public String getProperty(String key, String defaultValue) {
        lock.readLock().lock();
        try {
            if (key.equals("yamlConfigurationPath")){
                System.out.println("yamlConfigurationPath: " + properties.getProperty(key, defaultValue));
            }
            return properties.getProperty(key, defaultValue);
        } finally {
            lock.readLock().unlock();
        }
    }

}
