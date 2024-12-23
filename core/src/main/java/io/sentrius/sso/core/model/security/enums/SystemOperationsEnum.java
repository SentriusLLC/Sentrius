package io.sentrius.sso.core.model.security.enums;


public enum SystemOperationsEnum {
  EDIT_PROFILE,
  LOCKING_SYSTEMS,
  EDIT_USERS,
  DELETE_USERS,
  EDIT_USER_TYPES,
  DELETE_SYSTEMS,
  CREATE_SYSTEMS;

  public interface OpsIfc {
    String exec() throws RuntimeException;
  }
}
