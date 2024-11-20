package io.sentrius.sso.core.model.metadata;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Entity
@Getter
@Setter
@Table(name = "terminal_risk_indicators")
public class TerminalRiskIndicator {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne
    @JoinColumn(name = "session_id", nullable = false)
    private TerminalSessionMetadata session;

    @Column(name = "dangerous_commands_count", nullable = false)
    private Integer dangerousCommandsCount = 0;

    @Column(name = "unauthorized_access_attempts", nullable = false)
    private Integer unauthorizedAccessAttempts = 0;

    @Column(name = "geo_anomaly", nullable = false)
    private Boolean geoAnomaly = false;

    @Column(name = "out_of_hours", nullable = false)
    private Boolean outOfHours = false;

    // Getters and Setters
}
