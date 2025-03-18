package io.sentrius.sso.core.model.users;


import java.util.ArrayList;
import java.util.List;

import io.sentrius.sso.core.dto.UserDTO;
import io.sentrius.sso.core.dto.UserTypeDTO;
import io.sentrius.sso.core.model.actors.PrincipalEntity;
import io.sentrius.sso.core.model.hostgroup.HostGroup;
import io.sentrius.sso.core.model.security.UserType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import lombok.experimental.SuperBuilder;

@Entity
@Getter
@Setter
@ToString
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
@Table(name = "users")
public class User extends PrincipalEntity {



    @Column(name = "username")
    private String username;

    @Column(name = "password")
    private String password;

    @Column(name = "email_address")
    private String emailAddress;

    @Column(name = "user_id")
    private String userId;


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

    public  UserDTO toDto(){
        var builder = UserDTO.builder();
        builder.id(getId()).username(username).userId(userId).name(name).password(password).emailAddress(emailAddress).authorizationType(authorizationType.toDTO()).team(team);

        return builder.build();
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
