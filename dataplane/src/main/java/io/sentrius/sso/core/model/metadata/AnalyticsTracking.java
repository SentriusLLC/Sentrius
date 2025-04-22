package io.sentrius.sso.core.model.metadata;

import java.sql.Timestamp;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Entity
@Getter
@Setter
@Table(name = "analytics_tracking")
public class AnalyticsTracking {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "session_id", nullable = false, unique = true)
    private Long sessionId;

    @Column(name = "processed_at", nullable = false)
    private Timestamp processedAt = new Timestamp(System.currentTimeMillis());

    @Column(name = "status", nullable = false)
    private String status;

    // Getters and Setters
}
