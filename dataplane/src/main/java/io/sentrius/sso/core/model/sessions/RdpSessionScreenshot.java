package io.sentrius.sso.core.model.sessions;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;

/**
 * Entity to store RDP session screenshot data.
 * Screenshots are captured from Guacamole protocol PNG/IMG instructions during RDP sessions.
 * Image data is stored directly in the database for asynchronous analysis by the analytics agent.
 */
@Entity
@Table(name = "rdp_session_screenshots")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RdpSessionScreenshot {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "session_id", nullable = false)
    private String sessionId;

    @Column(name = "captured_at", nullable = false)
    private Instant capturedAt;

    @Lob
    @Basic(fetch = FetchType.LAZY)
    @JdbcTypeCode(SqlTypes.BINARY)
    @Column(name = "image_data", nullable = false)
    private byte[] imageData;


    @Column(name = "image_format", length = 10)
    private String imageFormat;

    @Column(name = "file_size")
    private Long fileSize;

    @Column(name = "processed", columnDefinition = "BOOLEAN DEFAULT FALSE")
    private Boolean processed = false;

    @Column(name = "analysis_result", columnDefinition = "TEXT")
    private String analysisResult;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) createdAt = Instant.now();
        if (processed == null) processed = false;
    }
}
