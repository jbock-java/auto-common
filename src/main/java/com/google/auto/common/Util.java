package io.jbock.auto.common;

import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Set;

class Util {

    static <E> Set<E> mutableUnion(Set<E> set1, Set<E> set2) {
        if (set1 instanceof HashSet) {
            set1.addAll(set2);
            return set1;
        }
        return union(set1, set2);
    }

    static <E> Set<E> union(Set<E> set1, Set<E> set2) {
        if (set2.isEmpty()) {
            return set1;
        }
        Set<E> result = new LinkedHashSet<>(Math.max(8, (int) (1.4 * (set1.size() + set2.size()))));
        result.addAll(set1);
        result.addAll(set2);
        return result;
    }

    static <E> Set<E> expectNoInvocation(Set<E> set1, Set<E> set2) {
        throw new IllegalStateException("expecting no invocation");
    }
}
