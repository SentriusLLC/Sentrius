package io.dataguardians.sso.core.model.dto;


import lombok.Builder;
import lombok.Data;
import lombok.Getter;

@Getter
@Data
@Builder
public class TicketDTO {
    private String id;
    private String summary;
    private String description;;
    private String status;
    private String command;
    private String commandHash;
    private String type;
    private String userName;
    private String hostName;
    private String reasonIdentifier;
    private String reasonUrl;
    private Integer usesRemaining;
    private Boolean canResubmit;
    private String lastUpdated;
    private boolean currentUser;
}
