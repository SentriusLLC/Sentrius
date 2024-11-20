package io.dataguardians.sso.core.model.security.enums;

public enum SpecialAccesses {
  CANNOT_ACCESS_SYSTEMS("CANNOT_ACCESS_SYSTEMS");

  private final String value;

  SpecialAccesses(String value) {
    this.value = value;
  }

  public String getValue() {
    return value;
  }
}
