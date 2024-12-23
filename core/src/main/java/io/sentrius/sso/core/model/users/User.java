package io.sentrius.sso.core.model.users;


import java.util.ArrayList;
import java.util.List;

import io.sentrius.sso.core.model.dto.UserDTO;
import io.sentrius.sso.core.model.hostgroup.HostGroup;
import io.sentrius.sso.core.model.security.UserType;
import io.sentrius.sso.core.model.actors.UserActor;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
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
@Table(name = "users")
public class User implements UserActor {

    @Id
    @Column(name = "id")
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "username")
    private String username;


    @Column(name = "name")
    private String name;

    @Column(name = "password")
    private String password;

    @Column(name = "email_address")
    private String emailAddress;

    @Column(name = "user_id")
    private String userId;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "role_id", referencedColumnName = "id")
    @Builder.Default
    public UserType authorizationType = UserType.builder().build();

    @Column(name = "team")
    private String team;

    @ManyToMany
    @JoinTable(
        name = "user_hostgroups",  // join table name
        joinColumns = @JoinColumn(name = "user_id"),
        inverseJoinColumns = @JoinColumn(name = "hostgroup_id")
    )
    @Builder.Default
    private List<HostGroup> hostGroups = new ArrayList<>();

    /*
    @OneToMany(fetch = FetchType.LAZY, cascade = CascadeType.ALL)
    @JoinTable(
        name = "user_profile_restrictions",
        joinColumns = @JoinColumn(name = "user_id"),
        inverseJoinColumns = @JoinColumn(name = "time_config_id")
    )
    @MapKeyColumn(name = "hostgroup_id")
    private Map<Long, TimeConfigs> hostGroupWindows = new HashMap<>();;

    public void setManagementGroups(Collection<String> groups) {
        hostGroups = new ArrayList<>();
        groups.forEach(
            group -> {
                HostGroup profile = new HostGroup();
                profile.setName(group);
                hostGroups.add(profile);
            });
    }

*/
    public boolean canAccessProfile(HostGroup profile) {
        return true;
        /*
        var timeConfig = hostGroupWindows.get(profile.getId());
        if (null == timeConfig) {
            return true;
        }
        LocalDateTime currentTime = LocalDateTime.now();
        for (var config : timeConfig.getTimeConfigs()) {
            try {
                if (TimeChecker.isTimeWithinRange(currentTime, config)) {
                    return true;
                }
            } catch (JsonProcessingException e) {
                throw new RuntimeException(e);
            }
        }
        return false;

         */
    }

    public static User from(UserDTO dto){
        return User.builder().id(dto.getId())
            .username(dto.getUsername())
            .userId(dto.getUserId())
            .name(dto.getName())
            .password(dto.getPassword())
            .emailAddress(dto.getEmailAddress())
            .authorizationType(UserType.builder().id(dto.getAuthorizationType().getId()).build())
            .team(dto.getTeam())
            .build();
    }
}
