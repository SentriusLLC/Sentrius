package io.dataguardians.sso.core.model.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
@AllArgsConstructor
public class JITTrackerDTO {
    private Long id;
    private String command;
    private String commandHash;
    private String userName;
    private String hostName;
    private String reasonIdentifier;
    private String reasonUrl;
    private Integer usesRemaining;
    private Boolean canResubmit;
    @Builder.Default
    private boolean currentUser = false;
}
