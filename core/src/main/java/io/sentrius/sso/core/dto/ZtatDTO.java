package io.sentrius.sso.core.dto;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Data
@Builder
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class ZtatDTO {
    private Long id;
    @Builder.Default
    private String status = "Open";
    private String summary;
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
    @Builder.Default
    private boolean canApprove = false;
    @Builder.Default
    private boolean canDeny = false;

    @Builder.Default
    List<String> communicationIds = new ArrayList<>();
}
