package com.google.auto.common;

import java.util.Objects;
import java.util.function.BiPredicate;
import java.util.function.Predicate;

import static java.util.Objects.requireNonNull;

/**
 * A strategy for determining whether two instances are considered equivalent, and for computing
 * hash codes in a manner consistent with that equivalence. Two examples of equivalences are the
 * {@linkplain #identity() identity equivalence} and the {@linkplain #equals "equals" equivalence}.
 *
 * @author Bob Lee
 * @author Ben Yu
 * @author Gregory Kick
 * @since 10.0 (<a href="https://github.com/google/guava/wiki/Compatibility">mostly
 *     source-compatible</a> since 4.0)
 */
public abstract class Equivalence<T> implements BiPredicate<T, T> {
    /** Constructor for use by subclasses. */
    protected Equivalence() {
    }

    /**
     * Returns {@code true} if the given objects are considered equivalent.
     *
     * <p>This method describes an <i>equivalence relation</i> on object references, meaning that for
     * all references {@code x}, {@code y}, and {@code z} (any of which may be null):
     *
     * <ul>
     *   <li>{@code equivalent(x, x)} is true (<i>reflexive</i> property)
     *   <li>{@code equivalent(x, y)} and {@code equivalent(y, x)} each return the same result
     *       (<i>symmetric</i> property)
     *   <li>If {@code equivalent(x, y)} and {@code equivalent(y, z)} are both true, then {@code
     *       equivalent(x, z)} is also true (<i>transitive</i> property)
     * </ul>
     *
     * <p>Note that all calls to {@code equivalent(x, y)} are expected to return the same result as
     * long as neither {@code x} nor {@code y} is modified.
     */
    public final boolean equivalent(T a, T b) {
        if (a == b) {
            return true;
        }
        if (a == null || b == null) {
            return false;
        }
        return doEquivalent(a, b);
    }

    @Override
    public final boolean test(T t, T u) {
        return equivalent(t, u);
    }

    /**
     * Implemented by the user to determine whether {@code a} and {@code b} are considered equivalent,
     * subject to the requirements specified in {@link #equivalent}.
     *
     * <p>This method should not be called except by {@link #equivalent}. When {@link #equivalent}
     * calls this method, {@code a} and {@code b} are guaranteed to be distinct, non-null instances.
     */
    protected abstract boolean doEquivalent(T a, T b);

    /**
     * Returns a hash code for {@code t}.
     *
     * <p>The {@code hash} has the following properties:
     *
     * <ul>
     *   <li>It is <i>consistent</i>: for any reference {@code x}, multiple invocations of {@code
     *       hash(x}} consistently return the same value provided {@code x} remains unchanged
     *       according to the definition of the equivalence. The hash need not remain consistent from
     *       one execution of an application to another execution of the same application.
     *   <li>It is <i>distributable across equivalence</i>: for any references {@code x} and {@code
     *       y}, if {@code equivalent(x, y)}, then {@code hash(x) == hash(y)}. It is <i>not</i>
     *       necessary that the hash be distributable across <i>inequivalence</i>. If {@code
     *       equivalence(x, y)} is false, {@code hash(x) == hash(y)} may still be true.
     *   <li>{@code hash(null)} is {@code 0}.
     * </ul>
     */
    public final int hash(T t) {
        if (t == null) {
            return 0;
        }
        return doHash(t);
    }

    /**
     * Implemented by the user to return a hash code for {@code t}, subject to the requirements
     * specified in {@link #hash}.
     *
     * <p>This method should not be called except by {@link #hash}. When {@link #hash} calls this
     * method, {@code t} is guaranteed to be non-null.
     */
    protected abstract int doHash(T t);

    /**
     * Returns a wrapper of {@code reference} that implements {@link Wrapper#equals(Object)
     * Object.equals()} such that {@code wrap(a).equals(wrap(b))} if and only if {@code equivalent(a,
     * b)}.
     */
    public final <S extends T> Wrapper<S> wrap(S reference) {
        return new Wrapper<>(this, reference);
    }

    /**
     * Wraps an object so that {@link #equals(Object)} and {@link #hashCode()} delegate to an {@link
     * Equivalence}.
     *
     * <p>For example, given an {@link Equivalence} for {@link String strings} named {@code equiv}
     * that tests equivalence using their lengths:
     *
     * <pre>{@code
     * equiv.wrap("a").equals(equiv.wrap("b")) // true
     * equiv.wrap("a").equals(equiv.wrap("hello")) // false
     * }</pre>
     *
     * <p>Note in particular that an equivalence wrapper is never equal to the object it wraps.
     *
     * <pre>{@code
     * equiv.wrap(obj).equals(obj) // always false
     * }</pre>
     */
    public static final class Wrapper<T> {
        private final Equivalence<? super T> equivalence;
        private final T reference;

        private Wrapper(Equivalence<? super T> equivalence, T reference) {
            this.equivalence = requireNonNull(equivalence);
            this.reference = reference;
        }

        /** Returns the (possibly null) reference wrapped by this instance. */
        public T get() {
            return reference;
        }

        /**
         * Returns {@code true} if {@link Equivalence#equivalent(Object, Object)} applied to the wrapped
         * references is {@code true} and both wrappers use the {@link Object#equals(Object) same}
         * equivalence.
         */
        @Override
        public boolean equals(Object obj) {
            if (obj == this) {
                return true;
            }
            if (obj instanceof Wrapper) {
                Wrapper<?> that = (Wrapper<?>) obj; // note: not necessarily a Wrapper<T>

                if (this.equivalence.equals(that.equivalence)) {
                    /*
                     * We'll accept that as sufficient "proof" that either equivalence should be able to
                     * handle either reference, so it's safe to circumvent compile-time type checking.
                     */
                    @SuppressWarnings("unchecked")
                    Equivalence<Object> equivalence = (Equivalence<Object>) this.equivalence;
                    return equivalence.equivalent(this.reference, that.reference);
                }
            }
            return false;
        }

        /** Returns the result of {@link Equivalence#hash(Object)} applied to the wrapped reference. */
        @Override
        public int hashCode() {
            return equivalence.hash(reference);
        }

        /**
         * Returns a string representation for this equivalence wrapper. The form of this string
         * representation is not specified.
         */
        @Override
        public String toString() {
            return equivalence + ".wrap(" + reference + ")";
        }

    }

    /**
     * Returns an equivalence over iterables based on the equivalence of their elements. More
     * specifically, two iterables are considered equivalent if they both contain the same number of
     * elements, and each pair of corresponding elements is equivalent according to {@code this}. Null
     * iterables are equivalent to one another.
     */
    public final <S extends T> Equivalence<Iterable<S>> pairwise() {
        // Ideally, the returned equivalence would support Iterable<? extends T>. However,
        // the need for this is so rare that it's not worth making callers deal with the ugly wildcard.
        return new PairwiseEquivalence<>(this);
    }

    /**
     * Returns a predicate that evaluates to true if and only if the input is equivalent to {@code
     * target} according to this equivalence relation.
     */
    public final Predicate<T> equivalentTo(T target) {
        return new EquivalentToPredicate<>(this, target);
    }

    private static final class EquivalentToPredicate<T> implements Predicate<T> {

        private final Equivalence<T> equivalence;
        private final T target;

        EquivalentToPredicate(Equivalence<T> equivalence, T target) {
            this.equivalence = requireNonNull(equivalence);
            this.target = target;
        }

        @Override
        public boolean test(T input) {
            return equivalence.equivalent(input, target);
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }
            if (obj instanceof EquivalentToPredicate) {
                EquivalentToPredicate<?> that = (EquivalentToPredicate<?>) obj;
                return equivalence.equals(that.equivalence) && Objects.equals(target, that.target);
            }
            return false;
        }

        @Override
        public int hashCode() {
            return Objects.hash(equivalence, target);
        }

        @Override
        public String toString() {
            return equivalence + ".equivalentTo(" + target + ")";
        }
    }

    /**
     * Returns an equivalence that delegates to {@link Object#equals} and {@link Object#hashCode}.
     * {@link Equivalence#equivalent} returns {@code true} if both values are null, or if neither
     * value is null and {@link Object#equals} returns {@code true}. {@link Equivalence#hash} returns
     * {@code 0} if passed a null value.
     *
     * @since 13.0
     * @since 8.0 (in Equivalences with null-friendly behavior)
     * @since 4.0 (in Equivalences)
     */
    public static Equivalence<Object> equals() {
        return Equals.INSTANCE;
    }

    /**
     * Returns an equivalence that uses {@code ==} to compare values and {@link
     * System#identityHashCode(Object)} to compute the hash code. {@link Equivalence#equivalent}
     * returns {@code true} if {@code a == b}, including in the case that a and b are both null.
     *
     * @since 13.0
     * @since 4.0 (in Equivalences)
     */
    public static Equivalence<Object> identity() {
        return Identity.INSTANCE;
    }

    static final class Equals extends Equivalence<Object> {

        static final Equals INSTANCE = new Equals();

        @Override
        protected boolean doEquivalent(Object a, Object b) {
            return a.equals(b);
        }

        @Override
        protected int doHash(Object o) {
            return o.hashCode();
        }
    }

    static final class Identity extends Equivalence<Object> {

        static final Identity INSTANCE = new Identity();

        @Override
        protected boolean doEquivalent(Object a, Object b) {
            return false;
        }

        @Override
        protected int doHash(Object o) {
            return System.identityHashCode(o);
        }
    }
}
