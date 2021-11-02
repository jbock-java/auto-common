package com.google.auto.common;

import java.util.AbstractMap.SimpleImmutableEntry;
import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.Spliterator;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import static java.util.Spliterators.spliteratorUnknownSize;

/**
 * A simple multimap that remembers the insertion order,
 * as an alternative to a full copy of guava's {@code ImmutableMultimap}.
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
        for (Entry<K, Set<V>> e : map.entrySet()) {
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

    /* Insertion order. */
    Set<V> flatValues() {
        return set;
    }

    Collection<Set<V>> values() {
        return map.values();
    }

    /* Entries in no particular order. */
    Stream<Entry<K, V>> stream() {
        Iterator<Entry<K, Set<V>>> iterator = map.entrySet().iterator();
        if (!iterator.hasNext()) {
            return Stream.empty();
        }
        return StreamSupport.stream(spliteratorUnknownSize(new Iterator<>() {

            Entry<K, Set<V>> current = iterator.next();
            Iterator<V> setIterator = current.getValue().iterator();

            @Override
            public boolean hasNext() {
                if (setIterator.hasNext()) {
                    return true;
                }
                if (!iterator.hasNext()) {
                    return false;
                }
                current = iterator.next();
                setIterator = current.getValue().iterator();
                return setIterator.hasNext();
            }

            @Override
            public Entry<K, V> next() {
                return new SimpleImmutableEntry<>(current.getKey(), setIterator.next());
            }
        }, Spliterator.ORDERED), false);
    }

    boolean isEmpty() {
        return map.isEmpty();
    }

    Map<K, Set<V>> asMap() {
        return map;
    }
}
