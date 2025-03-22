package io.sentrius.sso.install.configuration;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import io.sentrius.sso.core.dto.HostSystemDTO;
import io.sentrius.sso.core.dto.UserDTO;
import io.sentrius.sso.core.dto.UserTypeDTO;
import io.sentrius.sso.core.model.security.UserType;
import io.sentrius.sso.core.model.security.enums.CertKeyConfiguration;
import io.sentrius.sso.core.model.security.enums.SystemKeyConfiguration;
import io.sentrius.sso.install.configuration.dtos.HostGroupConfigurationDTO;
import io.sentrius.sso.install.configuration.dtos.RuleDTO;
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

  private List<HostSystemDTO> systems;

  private List<UserDTO> users;

  @Builder.Default
  private List<RuleDTO> rules = new ArrayList<>();

  @Builder.Default
  private UserDTO adminUser =
      UserDTO.builder()
          .id(0L)
          .authorizationType(UserType.createSuperUser().toDTO())
          .username("admin")
          .password("changeme")
          .build();

  @Builder.Default private List<UserTypeDTO> userTypes = new ArrayList<>();

  @Builder.Default private List<HostGroupConfigurationDTO> managementGroups = new ArrayList<>();

  private List<SystemKeyConfiguration> systemKeyConfigurations;

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
