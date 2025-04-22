package io.sentrius.sso.core.model.security;

public enum IdentityType {
    USER, NON_PERSON_ENTITY, APPLICATION;

    public static IdentityType fromString(String name) {
        if (name == null) return USER;
        return switch (name.toUpperCase()) {
            case "NON_PERSON_ENTITY" -> NON_PERSON_ENTITY;
            case "APPLICATION" -> APPLICATION;
            default -> USER;
        };
    }
}
