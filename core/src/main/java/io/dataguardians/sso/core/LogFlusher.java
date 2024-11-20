package io.dataguardians.sso.core;

import io.dataguardians.sso.core.services.auditing.AuditService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class LogFlusher {
    private final AuditService auditService;

    public LogFlusher(AuditService auditService) {
        this.auditService = auditService;
    }

    @Scheduled(fixedRateString = "${log.flush.interval:5000}")
    public void flushLogs() {
        // Logic to flush logs
        auditService.flushLogs();
    }
}

