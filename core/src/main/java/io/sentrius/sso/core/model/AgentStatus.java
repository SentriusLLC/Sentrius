package io.sentrius.sso.core.model;

import lombok.Builder;
import lombok.Data;
import lombok.Getter;
import lombok.ToString;

@Getter
@Data
@ToString
@Builder
public class AgentStatus {
    private String status;
    private String version;
    private String health;
    private String osName;
    private String osArch;
    private String osVersion;
    private String hostName;
    private long uptimeMillis;
    private long totalMemory;
    private long freeMemory;
}
