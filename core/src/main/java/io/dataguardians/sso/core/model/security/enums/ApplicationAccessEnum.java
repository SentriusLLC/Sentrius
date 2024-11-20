package io.dataguardians.sso.core.model.security.enums;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

public enum ApplicationAccessEnum {

  CAN_LOG_IN(1),

  CAN_EDIT_ACCESS_TYPES(3),

  CAN_MANAGE_APPLICATION(15);

  private final int value;

  ApplicationAccessEnum(int i) {
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

  public static ApplicationAccessEnum of(int value) {
    for (var accessEnum : values()) {
      if (accessEnum.getValue() == value) {
        return accessEnum;
      }
    }
    return null;
  }

  public static ApplicationAccessEnum of(List<String> userAccessList) {
    int value = 0;
    for (var accessEnum : values()) {
      if (userAccessList.contains(accessEnum.name())) {
        value = value | accessEnum.getValue();
      }
    }
    return ApplicationAccessEnum.of(value);
  }
}
