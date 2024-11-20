package io.dataguardians.sso.core.model.automation;

import io.dataguardians.automation.sideeffects.state.State;
import io.dataguardians.sso.core.model.users.User;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "automation")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Automation {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "script_type")
    private String type;

    @Column(name = "display_nm", nullable = false)
    private String displayName;

    @Column(name = "script", nullable = false, columnDefinition = "TEXT")
    private String script = "#!/bin/bash\n\n";

    @Column(name = "description")
    private String description;

    @Transient
    private State state;


    @Column(name = "automation_options", columnDefinition = "TEXT")
    private String automationOptionsStr;

    public void setAutomationName(String name) {
        this.displayName = name;
    }

    public void setAutomationType(String type) {
        this.type = type;
    }
}