package io.sentrius.sso.core.model.zt;


import java.time.LocalDateTime;
import io.sentrius.sso.core.model.users.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

@Entity
@Setter
@Getter
@Table(name = "ztat_approval_history")
public class ZeroTrustApprovalHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ztat_request_id", nullable = false)
    private ZeroTrustAccessTokenRequest ztatRequest;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "approver_id", nullable = false)
    private User approver;

    @Column(name = "approved", nullable = false)
    private boolean approved;

    @Column(name = "rationale", columnDefinition = "TEXT")
    private String rationale;

    @CreationTimestamp
    @Column(name = "decision_timestamp", nullable = false, updatable = false)
    private LocalDateTime decisionTimestamp;
}
