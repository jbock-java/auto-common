package com.google.auto.common;

final class GuavaEquivalence {

    private GuavaEquivalence() {
    }

    static <T> com.google.common.base.Equivalence<T> of(Equivalence<T> equivalence) {
        return new com.google.common.base.Equivalence<>() {

            @Override
            protected boolean doEquivalent(T a, T b) {
                return equivalence.doEquivalent(a, b);
            }

            @Override
            protected int doHash(T o) {
                return equivalence.doHash(o);
            }
        };
    }
}
