package io.sentrius.sso.core.data;

public enum EndpointThreat {
    NONE(0),
    MARGINAL(5), HIGH(10);

    private final int value;

    EndpointThreat(int value) {
        this.value = value;
    }

    public int getValue() {
        return value;
    }
}