package io.sentrius.sso.core.model.users;

import java.sql.Timestamp;
import io.sentrius.sso.core.model.actors.PrincipalEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@SuperBuilder
@Entity
@EqualsAndHashCode
@Table(name = "npe_users")
public class NonPersonEntity extends PrincipalEntity {

    @Column(name = "npe_id", unique = true, nullable = false)
    private String npeId;


    @Column(name = "created_at", columnDefinition = "TIMESTAMP DEFAULT CURRENT_TIMESTAMP")
    private Timestamp createdAt;
}
