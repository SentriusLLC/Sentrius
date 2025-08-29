package io.sentrius.sso.core.model.automation;


import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

// ScriptCronEntry Entity
@Entity
@Table(name = "automation_cron_entries")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AutomationCronEntry {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "automation_id", nullable = false, insertable = false, updatable = false)
    private Automation automation;

    @Column(name = "automation_id")
    private Long automationId;

    @Column(name = "script_cron", nullable = false)
    private String scriptCron;
}