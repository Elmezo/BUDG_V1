package com.example.unisonsearch.util;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * A simple thread-safe LRU cache with TTL. Not for distributed use.
 */
public class SimpleCache<K, V> {
    private final long ttlMillis;
    private final int maxEntries;
    private final Map<K, Entry<V>> map;

    public SimpleCache(Duration ttl, int maxEntries) {
        this.ttlMillis = ttl.toMillis();
        this.maxEntries = Math.max(16, maxEntries);
        this.map = new LinkedHashMap<>(64, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<K, Entry<V>> eldest) {
                return size() > SimpleCache.this.maxEntries;
            }
        };
    }

    public synchronized V get(K key) {
        Entry<V> entry = map.get(key);
        if (entry == null)
            return null;
        if (isExpired(entry)) {
            map.remove(key);
            return null;
        }
        return entry.value;
    }

    public synchronized void put(K key, V value) {
        map.put(key, new Entry<>(value, System.currentTimeMillis()));
    }

    private boolean isExpired(Entry<V> entry) {
        return (System.currentTimeMillis() - entry.createdAtMillis) > ttlMillis;
    }

    public synchronized void clear() {
        map.clear();
    }

    private static final class Entry<V> {
        final V value;
        final long createdAtMillis;

        Entry(V value, long createdAtMillis) {
            this.value = value;
            this.createdAtMillis = createdAtMillis;
        }
    }
}
