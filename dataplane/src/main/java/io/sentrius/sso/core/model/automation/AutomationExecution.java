package io.sentrius.sso.core.model.automation;

import io.sentrius.sso.core.model.HostSystem;
import io.sentrius.sso.core.model.users.User;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;


// ScriptExecution Entity
@Entity
@Builder
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
    @JoinColumn(name = "automation_id")
    private Automation automation;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "suggestion_id")
    private AutomationSuggestion suggestion;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "executed_by_user_id")
    private User executedBy;

    @Column(name = "execution_output", columnDefinition = "TEXT")
    private String executionOutput;

    @Column(name = "status", length = 50)
    private String status;

    @Column(name = "exit_code")
    private Integer exitCode;

    @Column(name = "log_tm", columnDefinition = "TIMESTAMP DEFAULT CURRENT_TIMESTAMP")
    private java.sql.Timestamp logTm;
}
