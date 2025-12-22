package com.sentrius.sag.model;

public class AssertStatement implements Statement {
    private final String path;
    private final Object value;

    public AssertStatement(String path, Object value) {
        this.path = path;
        this.value = value;
    }

    public String getPath() {
        return path;
    }

    public Object getValue() {
        return value;
    }

    @Override
    public String toString() {
        return "AssertStatement{path='" + path + "', value=" + value + "}";
    }
}
