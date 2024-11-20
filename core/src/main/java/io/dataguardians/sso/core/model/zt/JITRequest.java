package io.dataguardians.sso.core.model.zt;

import io.dataguardians.sso.core.model.HostSystem;
import io.dataguardians.sso.core.model.users.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
// JITRequest Entity

@Builder
@Entity
@Table(name = "jit_requests")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class JITRequest {
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
    @JoinColumn(name = "jit_reason_id")
    private JITReason jitReason;

    @Column(name = "last_updated", columnDefinition = "TIMESTAMP DEFAULT CURRENT_TIMESTAMP")
    private java.sql.Timestamp lastUpdated;
}