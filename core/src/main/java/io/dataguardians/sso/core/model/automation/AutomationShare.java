package io.dataguardians.sso.core.model.automation;


import io.dataguardians.sso.core.model.users.User;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;


// ScriptShare Entity
@Entity
@Table(name = "script_shares")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AutomationShare {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "automation_id", nullable = false)
    private Automation automation;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;
}