/*
 * Copyright (C) 2010 The Guava Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * diOBJECTibuted under the License is diOBJECTibuted on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.jbock.auto.common;

import com.google.common.testing.EqualsTester;
import com.google.common.testing.NullPointerTester;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.function.Predicate;

import static io.jbock.auto.common.AnnotationMirrorsTest.equivalenceTesterOf;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit test for {@link Equivalence}.
 *
 * @author Jige Yu
 */
class EquivalenceTest {

    @Test
    @SuppressWarnings("unchecked") // varargs
    void testPairwiseEquivalent() {
        equivalenceTesterOf(Equivalence.equals().<String>pairwise())
                .addEquivalenceGroup(List.<String>of())
                .addEquivalenceGroup(List.of("a"))
                .addEquivalenceGroup(List.of("b"))
                .addEquivalenceGroup(List.of("a", "b"), List.of("a", "b"))
                .test();
    }

    @Test
    void testPairwiseEquivalent_equals() {
        new EqualsTester()
                .addEqualityGroup(Equivalence.equals().pairwise(), Equivalence.equals().pairwise())
                .addEqualityGroup(Equivalence.identity().pairwise())
                .testEquals();
    }

    @Test
    void testEquivalentTo() {
        Predicate<Object> equalTo1 = Equivalence.equals().equivalentTo("1");
        assertTrue(equalTo1.test("1"));
        assertFalse(equalTo1.test("2"));
        assertFalse(equalTo1.test(null));
        Predicate<Object> isNull = Equivalence.equals().equivalentTo(null);
        assertFalse(isNull.test("1"));
        assertFalse(isNull.test("2"));
        assertTrue(isNull.test(null));
        new EqualsTester()
                .addEqualityGroup(equalTo1, Equivalence.equals().equivalentTo("1"))
                .addEqualityGroup(isNull)
                .addEqualityGroup(Equivalence.identity().equivalentTo("1"))
                .testEquals();
    }

    @Test
    void testEquals() {
        new EqualsTester()
                .addEqualityGroup(Equivalence.equals(), Equivalence.equals())
                .addEqualityGroup(Equivalence.identity(), Equivalence.identity())
                .testEquals();
    }

    @Test
    void testNulls() {
        new NullPointerTester().testAllPublicStaticMethods(Equivalence.class);
    }
}
