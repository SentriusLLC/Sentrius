package io.sentrius.sso.core.model.zt;

import java.util.UUID;
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
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Getter
@Table(name = "ztat_approvals")
public class ZeroTrustAccessTokenApproval {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "approver_id", nullable = false)
    private User approver;

    @Column(name = "approved", nullable = false)
    private boolean approved;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ztat_request_id", nullable = false)
    private ZeroTrustAccessTokenRequest ztatRequest;

    @Column(name = "uses", nullable = false)
    private int uses;

    @Column(name = "token", unique = true, nullable = false)
    @Builder.Default
    private UUID token = UUID.randomUUID();
    // Getters and setters
}


