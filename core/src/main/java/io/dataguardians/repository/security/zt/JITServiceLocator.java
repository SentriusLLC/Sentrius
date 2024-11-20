package io.dataguardians.repository.security.zt;

import java.util.HashMap;
import java.util.Map;

public class JITServiceLocator {
    private static final Map<String, JITProcessingRepository> repositories = new HashMap<>();

    private static final Map<String, JITProcessingRepository> serviceFunctions = new HashMap<>();

    static {
        repositories.put("default", new InMemoryJITProcessingRepository());
    }

    public static JITProcessingRepository getRepository(String type) {
        return repositories.getOrDefault(type, repositories.get("default"));
    }

    public static JITProcessingRepository getRepository() {
        return repositories.get("default");
    }

    public static void setDefaultJITRepository(JITProcessingRepository repository) {
        repositories.put("default", repository);
    }
}