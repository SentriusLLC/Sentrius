package com.sentrius.sag;

import java.util.Map;

/**
 * Context interface for providing data to the expression evaluator.
 * Implementations can wrap Maps, Databases, or other data sources.
 */
public interface Context {
    /**
     * Get a value from the context by path (e.g., "balance", "user.name").
     * @param path The path to the value
     * @return The value at the path, or null if not found
     */
    Object get(String path);
    
    /**
     * Check if a path exists in the context.
     * @param path The path to check
     * @return true if the path exists, false otherwise
     */
    boolean has(String path);
    
    /**
     * Set a value in the context.
     * @param path The path to set
     * @param value The value to set
     */
    void set(String path, Object value);
    
    /**
     * Get all data as a map.
     * @return A map representation of the context
     */
    Map<String, Object> asMap();
}
