package com.sentrius.sag.model;

import java.util.List;
import java.util.Map;

public class EventStatement implements Statement {
    private final String eventName;
    private final List<Object> args;
    private final Map<String, Object> namedArgs;

    public EventStatement(String eventName, List<Object> args, Map<String, Object> namedArgs) {
        this.eventName = eventName;
        this.args = args;
        this.namedArgs = namedArgs;
    }

    public String getEventName() {
        return eventName;
    }

    public List<Object> getArgs() {
        return args;
    }

    public Map<String, Object> getNamedArgs() {
        return namedArgs;
    }

    @Override
    public String toString() {
        return "EventStatement{eventName='" + eventName + "', args=" + args + 
               ", namedArgs=" + namedArgs + "}";
    }
}
