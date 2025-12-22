package com.sentrius.sag;

import java.util.*;

/**
 * Defines the schema for a verb's arguments.
 * Like an API specification for SAG verbs.
 */
public class VerbSchema {
    private final String verbName;
    private final List<ArgumentSpec> positionalArgs;
    private final Map<String, ArgumentSpec> namedArgs;
    private final boolean allowExtraArgs;
    
    private VerbSchema(Builder builder) {
        this.verbName = builder.verbName;
        this.positionalArgs = new ArrayList<>(builder.positionalArgs);
        this.namedArgs = new HashMap<>(builder.namedArgs);
        this.allowExtraArgs = builder.allowExtraArgs;
    }
    
    public String getVerbName() {
        return verbName;
    }
    
    public List<ArgumentSpec> getPositionalArgs() {
        return Collections.unmodifiableList(positionalArgs);
    }
    
    public Map<String, ArgumentSpec> getNamedArgs() {
        return Collections.unmodifiableMap(namedArgs);
    }
    
    public boolean isAllowExtraArgs() {
        return allowExtraArgs;
    }
    
    /**
     * Specification for an argument.
     */
    public static class ArgumentSpec {
        private final String name;
        private final ArgType type;
        private final boolean required;
        private final String description;
        
        public ArgumentSpec(String name, ArgType type, boolean required, String description) {
            this.name = name;
            this.type = type;
            this.required = required;
            this.description = description;
        }
        
        public String getName() { return name; }
        public ArgType getType() { return type; }
        public boolean isRequired() { return required; }
        public String getDescription() { return description; }
    }
    
    /**
     * Supported argument types.
     */
    public enum ArgType {
        STRING, INTEGER, FLOAT, BOOLEAN, LIST, OBJECT, ANY
    }
    
    /**
     * Builder for VerbSchema.
     */
    public static class Builder {
        private final String verbName;
        private final List<ArgumentSpec> positionalArgs = new ArrayList<>();
        private final Map<String, ArgumentSpec> namedArgs = new HashMap<>();
        private boolean allowExtraArgs = false;
        
        public Builder(String verbName) {
            this.verbName = verbName;
        }
        
        public Builder addPositionalArg(String name, ArgType type, boolean required, String description) {
            positionalArgs.add(new ArgumentSpec(name, type, required, description));
            return this;
        }
        
        public Builder addNamedArg(String name, ArgType type, boolean required, String description) {
            namedArgs.put(name, new ArgumentSpec(name, type, required, description));
            return this;
        }
        
        public Builder allowExtraArgs(boolean allow) {
            this.allowExtraArgs = allow;
            return this;
        }
        
        public VerbSchema build() {
            return new VerbSchema(this);
        }
    }
    
    @Override
    public String toString() {
        return "VerbSchema{" +
               "verbName='" + verbName + '\'' +
               ", positionalArgs=" + positionalArgs.size() +
               ", namedArgs=" + namedArgs.size() +
               ", allowExtraArgs=" + allowExtraArgs +
               '}';
    }
}
