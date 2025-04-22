package io.sentrius.sso.core.dto;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.sql.Timestamp;

public class TerminalLogOutputDTO {
    private LocalDateTime logTm;
    private long outputSize;

    public TerminalLogOutputDTO(Timestamp logTm, String output) {
        // Convert java.sql.Timestamp to java.time.LocalDateTime
        this.logTm = logTm.toLocalDateTime();
        this.outputSize = output != null ? output.length() : 0;
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