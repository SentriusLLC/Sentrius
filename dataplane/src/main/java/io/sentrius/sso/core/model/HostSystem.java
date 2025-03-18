package io.sentrius.sso.core.model;

import java.util.ArrayList;
import java.util.List;
import com.fasterxml.jackson.annotation.JsonBackReference;
import io.sentrius.sso.core.dto.HostGroupDTO;
import io.sentrius.sso.core.dto.HostSystemDTO;
import io.sentrius.sso.core.model.hostgroup.HostGroup;
import io.sentrius.sso.core.utils.TimeAgoFormatter;
import jakarta.persistence.CascadeType;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.OneToMany;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

@SuperBuilder(toBuilder = true)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
@Entity
@Table(name = "host_systems") // Name of the table for HostSystem
public class HostSystem implements Host {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY) // Auto-generate the ID
    @Column(name = "host_system_id")
    private Long id;

    @Column(name = "display_name")
    private String displayName;

    @Builder.Default
    @Column(name = "ssh_user", nullable = false)
    private String sshUser = "root";

    @Builder.Default
    @Column(name = "ssh_user_password")
    private String sshPassword = "";

    @Column(name = "host")
    @Builder.Default
    private String host = "";

    @Builder.Default
    @Column(name = "port")
    private Integer port = 22;

    @Column(name = "display_label")
    private String displayLabel;

    @Builder.Default
    @Column(name = "authorized_keys")
    private String authorizedKeys = "~/.ssh/authorized_keys";

    @Builder.Default
    @Column(name = "checked")
    private Boolean checked = false;

    @Builder.Default
    @Column(name = "status_code")
    private String statusCd = INITIAL_STATUS;

    @Column(name = "error_message")
    private String errorMsg;

    @ElementCollection // Stores the list of public keys
    @CollectionTable(name = "host_system_public_keys", joinColumns = @JoinColumn(name = "host_system_id"))
    @Column(name = "public_key")
    private List<String> publicKeyList;

    @Column(name = "instance_id")
    private Integer instanceId;

    @Builder.Default
    @Column(name = "locked")
    private boolean locked = false;

    @OneToMany(mappedBy = "hostSystem", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private List<ProxyHost> proxies;

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
        name = "hostgroup_hostsystems",
        joinColumns = @JoinColumn(name = "host_system_id"),
        inverseJoinColumns = @JoinColumn(name = "hostgroup_id")
    )
    @JsonBackReference
    private List<HostGroup> hostGroups;


    public static final String INITIAL_STATUS = "INITIAL";
    public static final String AUTH_FAIL_STATUS = "AUTHFAIL";
    public static final String PUBLIC_KEY_FAIL_STATUS = "KEYAUTHFAIL";
    public static final String GENERIC_FAIL_STATUS = "GENERICFAIL";
    public static final String SUCCESS_STATUS = "CONNECTED";
    public static final String HOST_FAIL_STATUS = "HOSTFAIL";



    public HostSystemDTO toDTO() {
        var dto = HostSystemDTO.builder();

        dto.id(this.id);
        dto.displayName(this.displayName);
        dto.host(this.host);
        dto.statusCd(this.statusCd);
        dto.publicKeyList(this.publicKeyList != null ? new ArrayList<>(this.publicKeyList) : new ArrayList<>());
        dto.errorMsg(this.errorMsg);
        dto.port(this.port);
        dto.lastAccessed("never");
        dto.sshUser(this.sshUser);
        dto.group(new HostGroupDTO());

        return dto.build();
    }

    public HostSystemDTO toDTO(HostGroup hg) {
        var dto = HostSystemDTO.builder();

        dto.id(this.id);
        dto.displayName(this.displayName);
        dto.host(this.host);
        dto.statusCd(this.statusCd);
        dto.publicKeyList(this.publicKeyList != null ? new ArrayList<>(this.publicKeyList) : new ArrayList<>());
        dto.errorMsg(this.errorMsg);
        dto.port(this.port);
        dto.lastAccessed("never");
        dto.sshUser(this.sshUser);
        dto.group(hg.toDTO());

        return dto.build();
    }

    public HostSystemDTO toDTO(String hostConnectionString, ConnectedSystem connectedSystem) {
        var dto = toDTO();
        dto.setHostConnection(hostConnectionString);
        dto.setLastAccessed(TimeAgoFormatter.formatTimestampToMinutesAgo(connectedSystem.getSession().getSessionTm()));
        return dto;
    }

    public static HostSystem fromDTO(HostSystemDTO dto) {
        HostSystem hostSystem = new HostSystem();
        if (null != dto) {
            hostSystem.setId(dto.getId());
        }
        hostSystem.setHost(dto.getHost());
        hostSystem.setSshPassword(dto.getPassword());
        hostSystem.setDisplayLabel(dto.getDisplayName());
        hostSystem.setDisplayName(dto.getDisplayName());
        hostSystem.setStatusCd(dto.getStatusCd());
        hostSystem.setPublicKeyList(dto.getPublicKeyList());
        hostSystem.setErrorMsg(dto.getErrorMsg());
        hostSystem.setPort(dto.getPort());
        hostSystem.setSshUser(dto.getSshUser());
        return hostSystem;
    }
}