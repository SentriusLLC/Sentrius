package io.sentrius.sso.core.model.zt;

import io.sentrius.sso.core.model.HostSystem;
import io.sentrius.sso.core.model.users.User;
import jakarta.persistence.Column;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Inheritance;
import jakarta.persistence.InheritanceType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.MappedSuperclass;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

@SuperBuilder(toBuilder = true)
@Data
@NoArgsConstructor
@AllArgsConstructor
@MappedSuperclass
@Inheritance(strategy = InheritanceType.JOINED)
public abstract class ZTATRequestBase {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "system_id")
    private HostSystem system;

    @Column(name = "command", nullable = false)
    private String command;

    @Column(name = "command_hash", nullable = false)
    private String commandHash;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "ztat_reason_id")
    private ZeroTrustAccessTokenReason ztatReason;

    @Column(name = "last_updated", columnDefinition = "TIMESTAMP DEFAULT CURRENT_TIMESTAMP")
    private java.sql.Timestamp lastUpdated;
}
