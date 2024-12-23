package io.sentrius.sso.core.model.dto;

import java.util.Date;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
@AllArgsConstructor
public class JITTrackerDTO {
    private Long id;
    @Builder.Default
    private String status = "Open";
    private String command;
    private String commandHash;
    private String userName;
    private String hostName;
    private String reasonIdentifier;
    private String reasonUrl;
    private Integer usesRemaining;
    private Boolean canResubmit;
    private Date lastUpdated;
    @Builder.Default
    private boolean currentUser = false;
}
