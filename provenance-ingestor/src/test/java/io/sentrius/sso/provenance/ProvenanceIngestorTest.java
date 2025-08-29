package io.sentrius.sso.provenance;

import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringApplication;

import static org.junit.jupiter.api.Assertions.*;

class ProvenanceIngestorTest {

    @Test
    void provenanceIngestorCanBeInstantiated() {
        ProvenanceIngestor ingestor = new ProvenanceIngestor();
        assertNotNull(ingestor);
    }

    @Test
    void provenanceIngestorMainMethodExists() {
        // Test that the main method exists and can be called
        // We're not actually starting the Spring application in tests
        assertDoesNotThrow(() -> {
            // Check that the main method signature is correct
            var mainMethod = ProvenanceIngestor.class.getMethod("main", String[].class);
            assertNotNull(mainMethod);
            assertTrue(mainMethod.getReturnType().equals(void.class));
        });
    }

    @Test
    void provenanceIngestorHasProperAnnotations() {
        // Test that the class has the expected Spring Boot annotations
        assertTrue(ProvenanceIngestor.class.isAnnotationPresent(
            org.springframework.boot.autoconfigure.SpringBootApplication.class));
        assertTrue(ProvenanceIngestor.class.isAnnotationPresent(
            org.springframework.data.jpa.repository.config.EnableJpaRepositories.class));
        assertTrue(ProvenanceIngestor.class.isAnnotationPresent(
            org.springframework.boot.autoconfigure.domain.EntityScan.class));
        assertTrue(ProvenanceIngestor.class.isAnnotationPresent(
            org.springframework.kafka.annotation.EnableKafka.class));
        assertTrue(ProvenanceIngestor.class.isAnnotationPresent(
            org.springframework.scheduling.annotation.EnableScheduling.class));
    }

    @Test
    void provenanceIngestorClassIsPublic() {
        assertTrue(java.lang.reflect.Modifier.isPublic(ProvenanceIngestor.class.getModifiers()));
    }
}