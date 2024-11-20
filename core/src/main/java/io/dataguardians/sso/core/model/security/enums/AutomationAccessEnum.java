package io.dataguardians.sso.core.model.security.enums;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

public enum AutomationAccessEnum {
  CAN_VIEW_AUTOMATION(1),

  CAN_EDIT_AUTOMATION(3),

  CAN_DEL_AUTOMATION(7),

  CAN_RUN_AUTOMATION(9),

  CAN_MANAGE_AUTOMATION(15);

  private final int value;

  AutomationAccessEnum(int i) {
    value = i;
  }

  public static AutomationAccessEnum of(List<String> userAccessList) {
    int value = 0;
    for (var accessEnum : values()) {
      if (userAccessList.contains(accessEnum.name())) {
        value = value | accessEnum.getValue();
      }
    }
    return AutomationAccessEnum.of(value);
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

  public static AutomationAccessEnum of(int value) {
    for (var accessEnum : values()) {
      if (accessEnum.getValue() == value) {
        return accessEnum;
      }
    }
    return null;
  }
}
