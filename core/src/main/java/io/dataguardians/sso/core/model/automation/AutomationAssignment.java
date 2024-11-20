package io.dataguardians.sso.core.model.automation;

import io.dataguardians.sso.core.model.HostSystem;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;


@Entity
@Table(name = "automation_assignments")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AutomationAssignment {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "automation_id", nullable = false)
    private Automation automation;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "system_id", nullable = false)
    private HostSystem system;

    @Column(name = "number_execs")
    private Integer numberExecs;
}