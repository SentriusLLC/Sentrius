package io.dataguardians.sso.core.model.dto;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.sql.Timestamp;

public class TerminalLogOutputDTO {
    private LocalDateTime logTm;
    private long outputSize;

    public TerminalLogOutputDTO(Timestamp logTm, long outputSize) {
        // Convert java.sql.Timestamp to java.time.LocalDateTime
        this.logTm = logTm.toLocalDateTime();
        this.outputSize = outputSize;
    }

    public LocalDateTime getLogTm() {
        return logTm;
    }

    public void setLogTm(LocalDateTime logTm) {
        this.logTm = logTm;
    }

    public long getOutputSize() {
        return outputSize;
    }

    public void setOutputSize(long outputSize) {
        this.outputSize = outputSize;
    }
}