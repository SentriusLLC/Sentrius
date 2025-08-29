package io.sentrius.sso.core.data;

public enum NotificationType {
    SYSTEM_NOTIFICATION(0),
    JIT_NOTIFICATION(1), BROADCAST(2);

    private final int value;

    NotificationType(int value) {
        this.value = value;
    }

    public int getValue() {
        return value;
    }
}