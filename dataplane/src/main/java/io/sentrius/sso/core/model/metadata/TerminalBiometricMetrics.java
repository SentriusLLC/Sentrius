package io.sentrius.sso.core.model.metadata;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "terminal_biometric_metrics")
@Getter
@Setter
public class TerminalBiometricMetrics {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne
    @JoinColumn(name = "session_id", nullable = false)
    private TerminalSessionMetadata session;

    @Column(name = "avg_dwell_time")
    private Float avgDwellTime;

    @Column(name = "avg_flight_time")
    private Float avgFlightTime;

    @Column(name = "keystroke_variance")
    private Float keystrokeVariance;

    @Column(name = "mouse_entropy")
    private Float mouseEntropy;

    @Column(name = "typing_entropy")
    private Float typingEntropy;
}