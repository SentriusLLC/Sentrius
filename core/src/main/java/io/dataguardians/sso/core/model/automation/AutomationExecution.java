package io.dataguardians.sso.core.model.automation;

import io.dataguardians.sso.core.model.HostSystem;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;


// ScriptExecution Entity
@Entity
@Table(name = "automation_executions")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AutomationExecution {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "system_id", nullable = false)
    private HostSystem system;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "automation_id", nullable = false)
    private Automation automation;

    @Column(name = "execution_output", columnDefinition = "TEXT")
    private String executionOutput;

    @Column(name = "log_tm", columnDefinition = "TIMESTAMP DEFAULT CURRENT_TIMESTAMP")
    private java.sql.Timestamp logTm;
}
