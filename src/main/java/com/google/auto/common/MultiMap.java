package com.google.auto.common;

import java.util.AbstractMap.SimpleImmutableEntry;
import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.Spliterator;
import java.util.Spliterators;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

/**
 * A simple multimap that remembers the order in which elements were added.
 *
 * @param <K> key type
 * @param <V> value type
 */
final class MultiMap<K, V> {

    // set of all values, in insertion order
    private final Set<V> set = new LinkedHashSet<>();
    private final Map<K, Set<V>> map = new LinkedHashMap<>();

    static <K, V> MultiMap<K, V> create() {
        return new MultiMap<>();
    }

    static <K, V> MultiMap<K, V> copyOf(Map<K, Set<V>> map) {
        MultiMap<K, V> result = new MultiMap<>();
        for (Map.Entry<K, Set<V>> e : map.entrySet()) {
            for (V v : e.getValue()) {
                result.put(e.getKey(), v);
            }
        }
        return result;
    }

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

    Stream<Map.Entry<K, V>> stream() {
        Iterator<Map.Entry<K, Set<V>>> iterator = map.entrySet().iterator();
        if (!iterator.hasNext()) {
            return Stream.empty();
        }
        return StreamSupport.stream(Spliterators.spliteratorUnknownSize(new Iterator<>() {

            Map.Entry<K, Set<V>> current = iterator.next();
            Iterator<V> setIterator = current.getValue().iterator();

            @Override
            public boolean hasNext() {
                return iterator.hasNext() || setIterator.hasNext();
            }

            @Override
            public Map.Entry<K, V> next() {
                if (!setIterator.hasNext()) {
                    current = iterator.next();
                    setIterator = current.getValue().iterator();
                }
                return new SimpleImmutableEntry<>(current.getKey(), setIterator.next());
            }
        }, Spliterator.ORDERED), false);
    }

    boolean isEmpty() {
        return map.isEmpty();
    }

    Map<K, Set<V>> toMap() {
        return map;
    }
}
