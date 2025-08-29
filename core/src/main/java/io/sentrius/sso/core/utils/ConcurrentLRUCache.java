package io.sentrius.sso.core.utils;

import java.util.LinkedHashMap;
import java.util.Map;

public class ConcurrentLRUCache<K, V> {

  private final Map<K, V> cache;

  public ConcurrentLRUCache(final int cacheSize) {

    // true = use access order instead of insertion order.
    this.cache =
        new LinkedHashMap<K, V>(cacheSize, 0.75f, true) {
          @Override
          protected boolean removeEldestEntry(Map.Entry<K, V> eldest) {
            // When to remove the eldest entry.
            return size() > cacheSize; // Size exceeded the max allowed.
          }
        };
  }

  public void put(K key, V value) {
    synchronized (cache) {
      cache.put(key, value);
    }
  }

  public V get(K key) {
    synchronized (cache) {
      return cache.get(key);
    }
  }

  public void clear() {
    synchronized (cache) {
      cache.clear();
    }
  }
}
