package io.jbock.auto.common;

import java.util.Iterator;
import java.util.Objects;

final class PairwiseEquivalence<E, T extends E> extends Equivalence<Iterable<T>> {
    final Equivalence<E> elementEquivalence;

    PairwiseEquivalence(Equivalence<E> elementEquivalence) {
        this.elementEquivalence = Objects.requireNonNull(elementEquivalence);
    }

    @Override
    protected boolean doEquivalent(Iterable<T> iterableA, Iterable<T> iterableB) {
        Iterator<T> iteratorA = iterableA.iterator();
        Iterator<T> iteratorB = iterableB.iterator();

        while (iteratorA.hasNext() && iteratorB.hasNext()) {
            if (!elementEquivalence.equivalent(iteratorA.next(), iteratorB.next())) {
                return false;
            }
        }

        return !iteratorA.hasNext() && !iteratorB.hasNext();
    }

    @Override
    protected int doHash(Iterable<T> iterable) {
        int hash = 78721;
        for (T element : iterable) {
            hash = hash * 24943 + elementEquivalence.hash(element);
        }
        return hash;
    }

    @Override
    public boolean equals(Object object) {
        if (object instanceof PairwiseEquivalence) {
            PairwiseEquivalence<?, ?> that = (PairwiseEquivalence<?, ?>) object;
            return this.elementEquivalence.equals(that.elementEquivalence);
        }

        return false;
    }

    @Override
    public int hashCode() {
        return elementEquivalence.hashCode() ^ 0x46a3eb07;
    }

    @Override
    public String toString() {
        return elementEquivalence + ".pairwise()";
    }
}
