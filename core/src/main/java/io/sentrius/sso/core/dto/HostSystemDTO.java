package io.sentrius.sso.core.dto;

import java.util.List;
import lombok.*;

@Getter
@Data
@Builder
@ToString
@AllArgsConstructor
@NoArgsConstructor
public class HostSystemDTO {
    private Long id;
    private int port;
    private HostGroupDTO group;
    private String sshUser;
    private String host;
    private String displayName;
    private String password;
    private String statusCd;
    private List<String> publicKeyList;
    private String errorMsg;
    private String hostConnection;
    private String lastAccessed;

    private String authorizedKeys;

}
