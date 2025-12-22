package com.sentrius.sag.model;

import java.util.List;
import java.util.Map;

public class ActionStatement implements Statement {
    private final String verb;
    private final List<Object> args;
    private final Map<String, Object> namedArgs;
    private final String policy;
    private final String policyExpr;
    private final String priority;
    private final String reason;

    public ActionStatement(String verb, List<Object> args, Map<String, Object> namedArgs,
                          String policy, String policyExpr, String priority, String reason) {
        this.verb = verb;
        this.args = args;
        this.namedArgs = namedArgs;
        this.policy = policy;
        this.policyExpr = policyExpr;
        this.priority = priority;
        this.reason = reason;
    }

    public String getVerb() {
        return verb;
    }

    public List<Object> getArgs() {
        return args;
    }

    public Map<String, Object> getNamedArgs() {
        return namedArgs;
    }

    public String getPolicy() {
        return policy;
    }

    public String getPolicyExpr() {
        return policyExpr;
    }

    public String getPriority() {
        return priority;
    }

    public String getReason() {
        return reason;
    }

    @Override
    public String toString() {
        return "ActionStatement{verb='" + verb + "', args=" + args + ", namedArgs=" + namedArgs +
               ", policy='" + policy + "', policyExpr='" + policyExpr + "', priority='" + priority + 
               "', reason='" + reason + "'}";
    }
}
