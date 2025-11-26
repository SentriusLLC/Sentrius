package io.sentrius.sso.config;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class Fingerprint {


    @PostConstruct
    public void logBuildFingerprint() {
        log.info("### integration-proxy BUILD {}",
            java.time.Instant.now());
    }
}
