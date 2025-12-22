package com.sentrius.sag;

import java.util.HashMap;
import java.util.Map;

/**
 * A simple Map-based implementation of Context.
 */
public class MapContext implements Context {
    private final Map<String, Object> data;
    
    public MapContext() {
        this.data = new HashMap<>();
    }
    
    public MapContext(Map<String, Object> data) {
        this.data = new HashMap<>(data);
    }
    
    @Override
    public Object get(String path) {
        if (path == null || path.isEmpty()) {
            return null;
        }
        
        String[] parts = path.split("\\.");
        Object current = data;
        
        for (String part : parts) {
            if (current instanceof Map) {
                @SuppressWarnings("unchecked")
                Map<String, Object> map = (Map<String, Object>) current;
                current = map.get(part);
                if (current == null) {
                    return null;
                }
            } else {
                return null;
            }
        }
        
        return current;
    }
    
    @Override
    public boolean has(String path) {
        return get(path) != null;
    }
    
    @Override
    public void set(String path, Object value) {
        if (path == null || path.isEmpty()) {
            return;
        }
        
        String[] parts = path.split("\\.");
        if (parts.length == 1) {
            data.put(path, value);
            return;
        }
        
        Map<String, Object> current = data;
        for (int i = 0; i < parts.length - 1; i++) {
            String part = parts[i];
            Object next = current.get(part);
            if (!(next instanceof Map)) {
                @SuppressWarnings("unchecked")
                Map<String, Object> newMap = new HashMap<>();
                current.put(part, newMap);
                current = newMap;
            } else {
                @SuppressWarnings("unchecked")
                Map<String, Object> map = (Map<String, Object>) next;
                current = map;
            }
        }
        current.put(parts[parts.length - 1], value);
    }
    
    @Override
    public Map<String, Object> asMap() {
        return new HashMap<>(data);
    }
}
