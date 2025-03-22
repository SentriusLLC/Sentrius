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
public class CertKeyConfiguration {
  String pathToKeyStore;
  String keyStorePassphrase;
  String keyStoreType;

  String pathToTrustStore;
  String trustStorePassphrase;
  String trustStoreType;
}
