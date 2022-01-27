package io.jbock.auto.common;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.util.AbstractMap.SimpleImmutableEntry;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.Assert.assertEquals;

@RunWith(JUnit4.class)
public class MultimapTest {

    @Test
    public void stream() {
        Multimap<String, Integer> m = new Multimap<>();
        m.put("a", 1);
        m.put("a", 2);
        m.put("b", 1);
        List<Map.Entry<String, Integer>> entries = stream(m).collect(Collectors.toList());
        assertEquals(List.of(
                new SimpleImmutableEntry<>("a", 1),
                new SimpleImmutableEntry<>("a", 2),
                new SimpleImmutableEntry<>("b", 1)), entries);
    }

    @Test
    public void copy() {
        Map<String, Set<Integer>> linkedMap = new LinkedHashMap<>();
        LinkedHashSet<Integer> aValues = new LinkedHashSet<>();
        aValues.add(1);
        aValues.add(2);
        linkedMap.put("a", aValues);
        linkedMap.put("b", Set.of(1));
        Multimap<String, Integer> m = copyOf(linkedMap);
        List<Map.Entry<String, Integer>> entries = stream(m).collect(Collectors.toList());
        assertEquals(List.of(
                new SimpleImmutableEntry<>("a", 1),
                new SimpleImmutableEntry<>("a", 2),
                new SimpleImmutableEntry<>("b", 1)), entries);
    }

    private static <K, V> Stream<Map.Entry<K, V>> stream(Multimap<K, V> map) {
        return map.asMap().entrySet().stream()
                .flatMap(e -> e.getValue().stream().map(v -> new SimpleImmutableEntry<>(e.getKey(), v)));
    }

    private static <K, V> Multimap<K, V> copyOf(Map<K, Set<V>> map) {
        Multimap<K, V> result = new Multimap<>();
        map.forEach((k, values) ->
                values.forEach(v -> result.put(k, v)));
        return result;
    }
}