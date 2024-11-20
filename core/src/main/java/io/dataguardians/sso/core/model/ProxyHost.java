package io.dataguardians.sso.core.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.*;

@Entity

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "proxy_hosts")
public class ProxyHost {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY) // Primary key with auto-generated value
    @Column(name = "id")
    private Long proxyId;

    @Column(name = "host")
    private String host;

    @Column(name = "port")
    private Integer port;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "host_system_id", nullable = false) // Ensure nullable=false if every ProxyHost must be associated with a HostSystem
    private HostSystem hostSystem;


    @Column(name = "error_message")
    private String errorMsg;
}
