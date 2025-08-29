package io.sentrius.sso.core.model.security.enums;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Builder
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class SystemKeyConfiguration {
  String keyConfigurationName;
  String pathToPrivateKey;
  String privateKeyPassphrase;
  String pathToPublicKey;

  String privateKey;
  String publicKey;
}
