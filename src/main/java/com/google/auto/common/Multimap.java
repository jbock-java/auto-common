package io.jbock.auto.common;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * A simple multimap that remembers the insertion order.
 *
 * @param <K> key type
 * @param <V> value type
 */
final class Multimap<K, V> {

    // set of all values, in insertion order
    private final Set<V> set = new LinkedHashSet<>();
    private final Map<K, Set<V>> map = new LinkedHashMap<>();

    void put(K key, V value) {
        map.merge(key, Set.of(value), Util::mutableUnion);
        set.add(value);
    }

    /* Values in insertion order. */
    Set<V> flatValues() {
        return set;
    }

    Map<K, Set<V>> asMap() {
        return map;
    }
}
