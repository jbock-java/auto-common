package com.google.auto.common;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * A simple multimap that remembers the order in which elements were added.
 *
 * @param <K> key type
 * @param <V> value type
 */
final class MultiMap<K, V> {

    private final Set<V> set = new LinkedHashSet<>();
    private final Map<K, Set<V>> map = new LinkedHashMap<>();

    void put(K key, V value) {
        map.compute(key, (k, v) -> {
            if (v == null) {
                v = new LinkedHashSet<>();
            }
            if (v.add(value)) {
                set.add(value);
            }
            return v;
        });
    }

    Set<V> flatValues() {
        return set;
    }

    Collection<Set<V>> values() {
        return map.values();
    }
}
