package io.dataguardians.sso.core.model.security.enums;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

public enum SSHAccessEnum {
  // _ _ _ 1 = 1
  // _ _ 1 1 = 3
  // _ 1 _ _ = 4
  // _ 1 1 1 = 7
  // 1 1 1 1 = 15
  // 1 _ _ _ = 8
  CAN_VIEW_SYSTEMS(1),

  CAN_EDIT_SYSTEMS(7),

  CAN_DEL_SYSTEMS(8),

  CAN_MANAGE_SYSTEMS(15);

  private final int value;

  SSHAccessEnum(int i) {
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

  public static SSHAccessEnum of(int value) {
    for (var accessEnum : values()) {
      if (accessEnum.getValue() == value) {
        return accessEnum;
      }
    }
    return null;
  }

  public static SSHAccessEnum of(List<String> userAccessList) {
    int value = 0;
    for (var accessEnum : values()) {
      if (userAccessList.contains(accessEnum.name())) {
        value = value | accessEnum.getValue();
      }
    }
    return SSHAccessEnum.of(value);
  }
}
