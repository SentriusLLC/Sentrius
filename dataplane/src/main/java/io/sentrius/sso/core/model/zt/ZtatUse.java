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
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

@Entity
@Setter
@Builder
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "ztat_uses")
public class ZtatUse {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ztat_approval_id", nullable = false)
    private ZeroTrustAccessTokenApproval ztatApproval;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @CreationTimestamp
    @Column(name = "used_at", nullable = false, updatable = false)
    private LocalDateTime usedAt;
}
