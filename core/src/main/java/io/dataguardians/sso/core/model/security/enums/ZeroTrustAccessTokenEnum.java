package io.dataguardians.sso.core.model.security.enums;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

public enum ZeroTrustAccessTokenEnum {
  CAN_VIEW_ZTATS(1),

  CAN_APPROVE_ZTATS(3),

  CAN_DENY_ZTATS(7),

  CAN_MANAGE_ZTATS(15);

  private final int value;

  ZeroTrustAccessTokenEnum(int i) {
    value = i;
  }

  public int getValue() {
    return value;
  }

  public Set<String> getAccessStrings() {
    Set<String> accessStrings = new HashSet<>();
    for (var accessEnum : values()) {
      if ((value & accessEnum.getValue()) == accessEnum.getValue()) {
        accessStrings.add(accessEnum.name());
      }
    }
    return accessStrings;
  }

  public static ZeroTrustAccessTokenEnum of(int value) {
    for (var accessEnum : values()) {
      if (accessEnum.getValue() == value) {
        return accessEnum;
      }
    }
    return null;
  }

  public static ZeroTrustAccessTokenEnum of(List<String> userAccessList) {
    int value = 0;
    for (var accessEnum : values()) {
      if (userAccessList.contains(accessEnum.name())) {
        value = value | accessEnum.getValue();
      }
    }
    return ZeroTrustAccessTokenEnum.of(value);
  }

  public interface OpsIfc {
    String created(long id) throws RuntimeException;
    String approved(long id) throws RuntimeException;
  }
}
