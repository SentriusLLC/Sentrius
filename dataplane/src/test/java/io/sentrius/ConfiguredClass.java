package io.sentrius;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.io.CleanupMode;
import org.junit.jupiter.api.io.TempDir;

public class ConfiguredClass {

    static ClassLoader classLoader = ConfiguredClass.class.getClassLoader();

    @BeforeAll
    public static void setupConfiguration(@TempDir(cleanup = CleanupMode.ALWAYS) Path tempDir)
        throws  IOException {
        synchronized (classLoader) {
            String resourceName = "configs/application.properties";

            File file = new File(classLoader.getResource(resourceName).getFile());

            String path = file.getParent();
            assertTrue(tempDir.toFile().isDirectory());

            Files.copy(file.toPath(), Path.of(tempDir + "/application.properties"));

            System.setProperty("CONFIG_DIR", tempDir.toString() + "/");
        }
    }
}
