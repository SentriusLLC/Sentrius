package io.dataguardians.sso.core.model.security.enums;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

public enum RuleAccessEnum {

  CAN_VIEW_RULES(1),

  CAN_EDIT_RULES(3),

  CAN_DEL_RULES(7),

  CAN_MANAGE_RULES(15);

  private final int value;

  RuleAccessEnum(int i) {
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

  public static RuleAccessEnum of(int value) {
    for (var accessEnum : values()) {
      if (accessEnum.getValue() == value) {
        return accessEnum;
      }
    }
    return null;
  }

  public static RuleAccessEnum of(List<String> userAccessList) {
    int value = 0;
    for (var accessEnum : values()) {
      if (userAccessList.contains(accessEnum.name())) {
        value = value | accessEnum.getValue();
      }
    }
    return RuleAccessEnum.of(value);
  }
}
