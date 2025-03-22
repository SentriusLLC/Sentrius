/**
 * Copyright (C) 2013 Loophole, LLC
 *
 * <p>Licensed under The Prosperity Public License 3.0.0
 */
package io.sentrius.sso.core.model.hostgroup;

import java.io.IOException;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import com.fasterxml.jackson.annotation.JsonManagedReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.sentrius.sso.core.dto.HostGroupDTO;
import io.sentrius.sso.core.dto.UserDTO;
import io.sentrius.sso.core.model.ApplicationKey;
import io.sentrius.sso.core.model.HostSystem;
import io.sentrius.sso.core.model.users.User;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

/** Value object that contains profile information */
@Builder
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
@Entity
@Table(name = "host_groups")
public class HostGroup {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  //@Column(name = "host_group_id")
  private Long id;

  @Column(name = "name")
  private String name;

  @Column(name = "description")
  private String description;
  @ManyToMany(fetch = FetchType.EAGER)
  @JoinTable(
      name = "hostgroup_hostsystems",
      joinColumns = @JoinColumn(name = "hostgroup_id"),
      inverseJoinColumns = @JoinColumn(name = "host_system_id")
  )
  private List<HostSystem> hostSystemList;

  @Column(name = "configuration", columnDefinition = "TEXT")
  @Builder.Default
  private String configurationJson = "{}";

  @Builder.Default
  @Transient
  private boolean selected = false;

  @ManyToMany(fetch = FetchType.LAZY)
  @JoinTable(
      name = "user_hostgroups",
      joinColumns = @JoinColumn(name = "hostgroup_id"),
      inverseJoinColumns = @JoinColumn(name = "user_id")
  )
  private List<User> users;

  @ManyToMany(mappedBy = "hostGroups", fetch = FetchType.LAZY)
  @JsonManagedReference
  private List<HostSystem> hostSystems;

  @ManyToMany
  @JoinTable(
      name = "system_rules",
      joinColumns = @JoinColumn(name = "system_id"),
      inverseJoinColumns = @JoinColumn(name = "rule_id")
  )
  @Builder.Default
  private Set<ProfileRule> rules = new HashSet<>();

  @OneToOne(cascade = {CascadeType.MERGE, CascadeType.REMOVE})
  @JoinColumn(name = "application_key_id", referencedColumnName = "id", unique = true)
  private ApplicationKey applicationKey;

  public void setSystems(Collection<String> systems) {
    hostSystemList = new java.util.ArrayList<>();
    systems.forEach(
        system -> {
          HostSystem hostSystem = new HostSystem();
          hostSystem.setDisplayName(system);
          hostSystemList.add(hostSystem);
        });
  }

  public ProfileConfiguration getConfiguration() {
    ObjectMapper objectMapper = new ObjectMapper();
    try {
      if (null == configurationJson || configurationJson.isEmpty()) {
        return new ProfileConfiguration();
      }
      return objectMapper.readValue(configurationJson, ProfileConfiguration.class);
    } catch (IOException e) {
      return new ProfileConfiguration();
    }
  }

  public void setConfiguration(ProfileConfiguration configuration) {
    ObjectMapper objectMapper = new ObjectMapper();
    try {
      this.configurationJson = objectMapper.writeValueAsString(configuration);
    } catch (IOException e) {
      throw new RuntimeException("Failed to serialize configuration to JSON", e);
    }
  }

  public HostGroupDTO toDTO(){
    return toDTO(false);
  }

  public HostGroupDTO toDTO(boolean setUsers){
    var builder = HostGroupDTO.builder();

    builder.groupId(this.getId());
    builder.displayName(this.getName());
    builder.description(this.getDescription());
    builder.hostCount(this.getHostSystemList().size());
    builder.configuration(this.getConfiguration());
    if (setUsers){
      builder.users(this.getUsers().stream().map(x -> x.toDto()).toList());
    }

    return builder.build();

  }
}
