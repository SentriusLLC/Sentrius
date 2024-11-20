package io.dataguardians.sso.core.model;

import com.jcraft.jsch.JSch;
import com.jcraft.jsch.Session;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;

@Builder
@Setter
@Getter
@EqualsAndHashCode
public class ProxySession {
  public JSch jsch;
  public Session session;
}
