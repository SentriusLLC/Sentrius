package com.sentrius.sag;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Registry for storing and retrieving verb schemas.
 * Provides a centralized place to define all verb specifications.
 */
public class SchemaRegistry {
    private final Map<String, VerbSchema> schemas = new ConcurrentHashMap<>();
    
    /**
     * Register a verb schema.
     * @param schema The schema to register
     */
    public void register(VerbSchema schema) {
        if (schema == null || schema.getVerbName() == null) {
            throw new IllegalArgumentException("Schema and verb name cannot be null");
        }
        schemas.put(schema.getVerbName(), schema);
    }
    
    /**
     * Get a registered schema by verb name.
     * @param verbName The verb name
     * @return The schema, or null if not found
     */
    public VerbSchema getSchema(String verbName) {
        return schemas.get(verbName);
    }
    
    /**
     * Check if a verb has a registered schema.
     * @param verbName The verb name
     * @return true if a schema exists
     */
    public boolean hasSchema(String verbName) {
        return schemas.containsKey(verbName);
    }
    
    /**
     * Remove a schema from the registry.
     * @param verbName The verb name
     * @return The removed schema, or null if not found
     */
    public VerbSchema unregister(String verbName) {
        return schemas.remove(verbName);
    }
    
    /**
     * Clear all registered schemas.
     */
    public void clear() {
        schemas.clear();
    }
    
    /**
     * Get all registered verb names.
     * @return A set of verb names
     */
    public Set<String> getRegisteredVerbs() {
        return new HashSet<>(schemas.keySet());
    }
    
    /**
     * Get the number of registered schemas.
     * @return The count
     */
    public int size() {
        return schemas.size();
    }
}
