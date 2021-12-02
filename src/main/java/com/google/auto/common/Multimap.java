package com.google.auto.common;

import java.util.AbstractMap.SimpleImmutableEntry;
import java.util.Collection;
import java.util.HashMap;
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
final class Multimap<K, V>  {

    // set of all values, in insertion order
    private final Set<V> set;
    private final Map<K, Set<V>> map;

    // visible for testing
    Multimap(Set<V> set, Map<K, Set<V>> map) {
        this.set = set;
        this.map = map;
    }

    static <K, V> Multimap<K, V> create() {
        return new Multimap<>(new LinkedHashSet<>(), new LinkedHashMap<>());
    }

    static <K, V> Multimap<K, V> copyOf(Map<K, Set<V>> map) {
        Multimap<K, V> result = create();
        map.forEach((k, values) ->
                values.forEach(v -> result.put(k, v)));
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

    /* Values in insertion order. */
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
                while (iterator.hasNext()) {
                    current = iterator.next();
                    setIterator = current.getValue().iterator();
                    if (setIterator.hasNext()) {
                        return true;
                    }
                }
                return false;
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

    Map<K, Set<V>> toMap() {
        return new HashMap<>(map);
    }
}
