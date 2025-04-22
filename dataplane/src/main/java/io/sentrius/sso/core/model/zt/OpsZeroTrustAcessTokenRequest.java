package io.sentrius.sso.core.model.zt;

import java.util.List;
import io.sentrius.sso.core.model.users.User;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
// JITRequest Entity

@Builder
@Entity
@Table(name = "operations_request")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class OpsZeroTrustAcessTokenRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User user;

    @Column(name = "command", nullable = false)
    private String command;

    @Column(name = "summary", columnDefinition = "TEXT")
    private String summary;

    @Column(name = "command_hash", nullable = false)
    private String commandHash;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "ztat_reason_id")
    private ZeroTrustAccessTokenReason ztatReason;

    @Column(name = "last_updated", columnDefinition = "TIMESTAMP DEFAULT CURRENT_TIMESTAMP")
    private java.sql.Timestamp lastUpdated;
    // Add the relationship to OpsApproval
    @OneToMany(mappedBy = "ztatRequest", fetch = FetchType.LAZY, cascade = CascadeType.ALL)
    private List<OpsApproval> approvals;
}