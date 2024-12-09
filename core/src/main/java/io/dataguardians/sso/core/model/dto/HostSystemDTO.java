package io.dataguardians.sso.core.model.dto;

import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;
import io.dataguardians.sso.core.model.ConnectedSystem;
import io.dataguardians.sso.core.model.HostSystem;
import io.dataguardians.sso.core.model.hostgroup.HostGroup;
import io.dataguardians.sso.core.utils.TimeAgoFormatter;
import lombok.Data;
import lombok.Getter;

@Getter
@Data
public class HostSystemDTO {
    private Long id;
    private int port;
    private HostGroupDTO group;
    private String sshUser;
    private String displayName;
    private String statusCd;
    private List<String> publicKeyList;
    private String errorMsg;
    private String hostConnection;
    private String lastAccessed;

    public HostSystemDTO(HostSystem hostSystem) {
        this.id = hostSystem.getId();
        this.displayName = hostSystem.getDisplayName();
        this.statusCd = hostSystem.getStatusCd();
        this.publicKeyList = hostSystem.getPublicKeyList() != null ? new ArrayList<>(hostSystem.getPublicKeyList()) : new ArrayList<>();
        this.errorMsg = hostSystem.getErrorMsg();
        this.port = hostSystem.getPort();
        this.lastAccessed = "never";
        this.sshUser = hostSystem.getSshUser();
        this.group =  new HostGroupDTO();
    }

    public HostSystemDTO(HostSystem hostSystem, HostGroup hg) {
        this.id = hostSystem.getId();
        this.displayName = hostSystem.getDisplayName();
        this.statusCd = hostSystem.getStatusCd();
        this.port = hostSystem.getPort();
        this.sshUser = hostSystem.getSshUser();
        this.publicKeyList = new ArrayList<>(hostSystem.getPublicKeyList());
        this.errorMsg = hostSystem.getErrorMsg();
        this.lastAccessed = "never";
        group = new HostGroupDTO(hg);
    }

    public HostSystemDTO(String hostConnectionString, ConnectedSystem connectedSystem) {
        this(connectedSystem.getHostSystem());
        this.hostConnection = hostConnectionString;
        this.lastAccessed = TimeAgoFormatter.formatTimestampToMinutesAgo(connectedSystem.getSession().getSessionTm());
    }

    public static HostSystem fromDTO(HostSystemDTO dto) {
        HostSystem hostSystem = new HostSystem();
        if (null != dto) {
            hostSystem.setId(dto.getId());
        }
        hostSystem.setDisplayName(dto.getDisplayName());
        hostSystem.setStatusCd(dto.getStatusCd());
        hostSystem.setPublicKeyList(dto.getPublicKeyList());
        hostSystem.setErrorMsg(dto.getErrorMsg());
        hostSystem.setPort(dto.getPort());
        hostSystem.setSshUser(dto.getSshUser());
        return hostSystem;
    }
}
