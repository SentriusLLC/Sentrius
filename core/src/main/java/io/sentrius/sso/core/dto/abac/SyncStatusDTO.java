package io.sentrius.sso.core.dto.abac;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class SyncStatusDTO {
    private String lastSyncTime;
    private Long totalUsers;
    private Long totalAttributes;
    private boolean syncEnabled;
    private String nextScheduledSync;
}
