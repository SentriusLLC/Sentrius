package io.sentrius.sso.core.model.security.enums;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Enum for data access permissions related to documents, knowledge graph, and data services.
 */
public enum DataAccessEnum {
  CAN_VIEW_DATA(1),
  CAN_SEARCH_DOCUMENTS(2),
  CAN_VIEW_KNOWLEDGE_GRAPH(4),
  CAN_QUERY_KNOWLEDGE_GRAPH(8),
  CAN_EDIT_KNOWLEDGE_GRAPH(16),
  CAN_MANAGE_DATA(31),
  NOT_AUTHORIZED_DATA(0);

  private final int value;

  DataAccessEnum(int i) {
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

  public static DataAccessEnum of(int value) {
    for (var accessEnum : values()) {
      if (accessEnum.getValue() == value) {
        return accessEnum;
      }
    }
    return null;
  }

  public static DataAccessEnum of(List<String> dataAccessList) {
    int value = 0;
    for (var accessEnum : values()) {
      if (dataAccessList.contains(accessEnum.name())) {
        value = value | accessEnum.getValue();
      }
    }
    return DataAccessEnum.of(value);
  }
}
