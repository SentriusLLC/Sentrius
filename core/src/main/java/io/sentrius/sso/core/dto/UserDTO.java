package io.sentrius.sso.core.dto;

import lombok.*;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Data
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserDTO {
    public Long id;
    public String userId;
    public String username;
    public String name;
    public String emailAddress;
    public UserTypeDTO authorizationType;
    public String identityType;
    public String lastSeen;

    public String team;
    public String password;

    @Builder.Default
    public Boolean isNpe= false;

    @Builder.Default
    public String status = "ACTIVE";

    @Builder.Default
    public List<HostGroupDTO> hostGroups = new ArrayList<>();

    private String atlpDefinition;
    
    // Agent-specific fields for non-person entities
    private UUID contextId;
    private Integer generation;
    private UUID parentId;
    private String memoryNamespace;
    private Double trustScore;
    private String policyId;
    private Long inheritedMemoryCount;
}
