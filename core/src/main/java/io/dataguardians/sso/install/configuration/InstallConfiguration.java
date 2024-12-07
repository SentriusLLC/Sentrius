package io.dataguardians.sso.install.configuration;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import io.dataguardians.sso.core.model.HostSystem;
import io.dataguardians.sso.core.model.hostgroup.HostGroup;
import io.dataguardians.sso.core.model.security.UserType;
import io.dataguardians.sso.core.model.security.enums.CertKeyConfiguration;
import io.dataguardians.sso.core.model.security.enums.SystemKeyConfiguration;
import io.dataguardians.sso.core.model.users.User;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Builder
@NoArgsConstructor
@Getter
@AllArgsConstructor
public class InstallConfiguration {

  private List<HostSystem> systems;

  private List<User> users;

  @Builder.Default
  private User adminUser =
      User.builder()
          .id(0L)
          .authorizationType(UserType.createSuperUser())
          .username("admin")
          .password("changeme")
          .build();

  @Builder.Default private List<UserType> userTypes = new ArrayList<>();

  @Builder.Default private List<HostGroup> managementGroups = new ArrayList<>();

  private SystemKeyConfiguration systemKeyConfiguration;

  private CertKeyConfiguration certKeyConfiguration;

  @Getter(AccessLevel.NONE)
  @Setter(AccessLevel.NONE)
  public static ObjectMapper objectMapper = new ObjectMapper(new YAMLFactory());

  public static InstallConfiguration fromYaml(String yaml) throws IOException {
    return objectMapper.readValue(yaml, InstallConfiguration.class);
  }

  public static InstallConfiguration fromYaml(InputStream inputStream) throws IOException {
    return objectMapper.readValue(inputStream, InstallConfiguration.class);
  }
}
