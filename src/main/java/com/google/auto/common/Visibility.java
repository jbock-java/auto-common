/*
 * Copyright 2014 Google LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.google.auto.common;

import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.Modifier;
import java.util.Set;

import static java.util.Objects.requireNonNull;
import static javax.lang.model.element.ElementKind.PACKAGE;

/**
 * Represents the visibility of a given {@link Element}: {@code public}, {@code protected},
 * {@code private} or default/package-private.
 *
 * <p>The constants for this enum are ordered according by increasing visibility.
 *
 * @author Gregory Kick
 */
public enum Visibility {
    PRIVATE,
    DEFAULT,
    PROTECTED,
    PUBLIC;

    /**
     * Returns the visibility of the given {@link Element}. While package and module elements don't
     * technically have a visibility associated with them, this method returns {@link #PUBLIC} for
     * them.
     */
    public static Visibility ofElement(Element element) {
        requireNonNull(element);
        // packages and module don't have modifiers, but they're obviously "public"
        if (element.getKind().equals(PACKAGE) || element.getKind().equals(ElementKind.MODULE)) {
            return PUBLIC;
        }
        Set<Modifier> modifiers = element.getModifiers();
        if (modifiers.contains(Modifier.PRIVATE)) {
            return PRIVATE;
        } else if (modifiers.contains(Modifier.PROTECTED)) {
            return PROTECTED;
        } else if (modifiers.contains(Modifier.PUBLIC)) {
            return PUBLIC;
        } else {
            return DEFAULT;
        }
    }

    /**
     * Returns effective visibility of the given element meaning that it takes into account the
     * visibility of its enclosing elements.
     */
    public static Visibility effectiveVisibilityOfElement(Element element) {
        requireNonNull(element);
        Visibility effectiveVisibility = PUBLIC;
        Element currentElement = element;
        while (currentElement != null) {
            effectiveVisibility = min(effectiveVisibility, ofElement(currentElement));
            currentElement = currentElement.getEnclosingElement();
        }
        return effectiveVisibility;
    }

    /**
     * Returns the minimum of the two values. If the values compare as 0, the first is returned.
     *
     * <p>The recommended solution for finding the {@code minimum} of some values depends on the type
     * of your data and the number of elements you have. Read more in the Guava User Guide article on
     * <a href="https://github.com/google/guava/wiki/CollectionUtilitiesExplained#comparators">{@code
     * Comparators}</a>.
     *
     * @param a first value to compare, returned if less than or equal to b.
     * @param b second value to compare.
     * @throws ClassCastException if the parameters are not <i>mutually comparable</i>.
     * @since 30.0
     */
    private static <T extends Comparable<? super T>> T min(T a, T b) {
        return (a.compareTo(b) <= 0) ? a : b;
    }
}
