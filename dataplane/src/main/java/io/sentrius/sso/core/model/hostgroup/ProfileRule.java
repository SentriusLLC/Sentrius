package io.sentrius.sso.core.model.hostgroup;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import io.sentrius.sso.core.dto.HostGroupDTO;
import io.sentrius.sso.core.dto.ProfileRuleDTO;
import io.sentrius.sso.core.model.auditing.Rule;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
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
  @Builder.Default
  private Set<HostGroup> hostGroups = new HashSet<>();

  public ProfileRuleDTO toDTO(boolean canEdit, boolean canView,
                              boolean canDelete) {
    var builder = ProfileRuleDTO.builder();
    builder.canEdit(canEdit);
    builder.canView(canView);
    builder.canDelete(canDelete);
    builder.id(id);
    builder.ruleName(ruleName);
    builder.ruleClass(curateName(ruleClass));
    builder.hostGroups(hostGroups.stream().map(x -> x.toDTO()).toList());
    builder.ruleConfig(ruleConfig);
    return builder.build();
  }


  private String curateName(String ruleClazz) {
    if (null != ruleClazz && ruleClazz.contains("io.sentrius")){
      ruleClazz = ruleClazz.substring(ruleClazz.lastIndexOf('.') + 1);
    }
    return ruleClazz;
  }


  public ProfileRuleDTO toDTO(HostGroup hostGroup, boolean canEdit, boolean canView, boolean canDelete) {
    var builder = ProfileRuleDTO.builder();
    builder.canEdit(canEdit);
    builder.canView(canView);
    builder.canDelete(canDelete);
    builder.id(id);
    builder.ruleName(ruleName);
    builder.ruleClass(curateName(ruleClass));
    builder.hostGroups(List.of(hostGroup.toDTO()));
    builder.ruleConfig(ruleConfig);
    return builder.build();
  }

}