package io.sentrius.sso.core.dto;


import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonPOJOBuilder;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.extern.jackson.Jacksonized;

@Getter
@Data
@Builder
@Jacksonized
@NoArgsConstructor
@AllArgsConstructor
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
