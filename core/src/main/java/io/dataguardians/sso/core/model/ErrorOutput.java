package io.dataguardians.sso.core.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

// ErrorOutput Entity
@Entity
@Table(name = "error_output")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ErrorOutput {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "system_id")
    private Integer systemId;

    @Column(name = "error_type", nullable = false)
    private String errorType;

    @Column(name = "error_location")
    private String errorLocation;

    @Column(name = "error_hash", nullable = false)
    private String errorHash;

    @Column(name = "error_logs", nullable = false)
    private String errorLogs;

    @Column(name = "log_tm", columnDefinition = "TIMESTAMP DEFAULT CURRENT_TIMESTAMP")
    private java.sql.Timestamp logTm;
}