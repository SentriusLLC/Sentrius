package io.sentrius.sso.core.model.security.enums;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

public enum UserAccessEnum {
  IS_USER(1),
  CAN_VIEW_USERS(3),

  CAN_EDIT_USERS(7),

  CAN_DEL_USERS(8),

  CAN_MANAGE_USERS(15);

  private final int value;

  UserAccessEnum(int i) {
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

  public static UserAccessEnum of(int value) {
    for (var accessEnum : values()) {
      if (accessEnum.getValue() == value) {
        return accessEnum;
      }
    }
    return null;
  }

  public static UserAccessEnum of(List<String> userAccessList) {
    int value = 0;
    for (var accessEnum : values()) {
      if (userAccessList.contains(accessEnum.name())) {
        value = value | accessEnum.getValue();
      }
    }
    return UserAccessEnum.of(value);
  }
}
