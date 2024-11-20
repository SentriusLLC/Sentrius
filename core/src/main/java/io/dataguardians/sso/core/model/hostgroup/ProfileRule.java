package io.dataguardians.sso.core.model.hostgroup;

import java.util.Set;
import io.dataguardians.sso.core.model.auditing.Rule;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

/** Value object that contains profile information */
@SuperBuilder
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
// Entity for 'rules' table
@Entity
@Table(name = "rules")
public class ProfileRule extends Rule {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "ruleName", nullable = false)
  private String ruleName;

  @Column(name = "ruleClass", nullable = false)
  private String ruleClass;

  @Column(name = "ruleConfig")
  private String ruleConfig;

  @ManyToMany(mappedBy = "rules", fetch = FetchType.EAGER)
  private Set<HostGroup> hostGroups;

}