package com.sentrius.sag.model;

public class ControlStatement implements Statement {
    private final Object condition;
    private final Statement thenStatement;
    private final Statement elseStatement;

    public ControlStatement(Object condition, Statement thenStatement, Statement elseStatement) {
        this.condition = condition;
        this.thenStatement = thenStatement;
        this.elseStatement = elseStatement;
    }

    public Object getCondition() {
        return condition;
    }

    public Statement getThenStatement() {
        return thenStatement;
    }

    public Statement getElseStatement() {
        return elseStatement;
    }

    @Override
    public String toString() {
        return "ControlStatement{condition=" + condition + ", thenStatement=" + thenStatement + 
               ", elseStatement=" + elseStatement + "}";
    }
}
