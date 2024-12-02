package io.dataguardians.sso.core.model.zt;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

// JITReason Entity
@Builder
@Entity
@Table(name = "ztat_reasons")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ZeroTrustAccessTokenReason {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "command_need", nullable = false)
    private String commandNeed;

    @Column(name = "reason_identifier")
    private String reasonIdentifier;

    @Column(name = "url")
    private String url;
}