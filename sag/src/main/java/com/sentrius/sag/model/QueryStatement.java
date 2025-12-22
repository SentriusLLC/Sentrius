package com.sentrius.sag.model;

public class QueryStatement implements Statement {
    private final Object expression;
    private final Object constraint;

    public QueryStatement(Object expression, Object constraint) {
        this.expression = expression;
        this.constraint = constraint;
    }

    public Object getExpression() {
        return expression;
    }

    public Object getConstraint() {
        return constraint;
    }

    @Override
    public String toString() {
        return "QueryStatement{expression=" + expression + ", constraint=" + constraint + "}";
    }
}
