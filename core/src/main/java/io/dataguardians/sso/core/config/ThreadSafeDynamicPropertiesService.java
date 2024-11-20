package io.dataguardians.sso.core.config;

import org.springframework.stereotype.Service;
import java.io.FileOutputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.Properties;
import java.util.concurrent.locks.ReentrantReadWriteLock;

@Service
public class ThreadSafeDynamicPropertiesService {


    private static final String DYNAMIC_CONFIG_PATH = "dynamic.properties";
    private final Properties properties = new Properties();
    private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();

    public ThreadSafeDynamicPropertiesService() throws IllegalAccessException {
        loadProperties();
    }

    // Load properties from the file
    private void loadProperties() {
        lock.writeLock().lock();
        try (FileInputStream in = new FileInputStream(DYNAMIC_CONFIG_PATH)) {
            properties.load(in);
        } catch (IOException e) {
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
            return properties.getProperty(key, defaultValue);
        } finally {
            lock.readLock().unlock();
        }
    }

}
