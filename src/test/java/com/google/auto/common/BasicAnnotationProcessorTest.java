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

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.truth.Correspondence;
import com.google.testing.compile.Compilation;
import com.google.testing.compile.CompilationRule;
import com.google.testing.compile.JavaFileObjects;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import javax.annotation.processing.Filer;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.Element;
import javax.lang.model.element.TypeElement;
import javax.tools.JavaFileObject;
import java.io.IOException;
import java.io.PrintWriter;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import static com.google.common.truth.Truth.assertAbout;
import static com.google.common.truth.Truth.assertThat;
import static com.google.testing.compile.CompilationSubject.assertThat;
import static com.google.testing.compile.Compiler.javac;
import static com.google.testing.compile.JavaSourceSubjectFactory.javaSource;
import static com.google.testing.compile.JavaSourcesSubjectFactory.javaSources;
import static javax.tools.Diagnostic.Kind.ERROR;
import static javax.tools.StandardLocation.SOURCE_OUTPUT;

@RunWith(JUnit4.class)
public class BasicAnnotationProcessorTest {

    private abstract static class BaseAnnotationProcessor extends BasicAnnotationProcessor {

        static final String ENCLOSING_CLASS_NAME =
                BasicAnnotationProcessorTest.class.getCanonicalName();

        @Override
        public final SourceVersion getSupportedSourceVersion() {
            return SourceVersion.latestSupported();
        }
    }

    @Retention(RetentionPolicy.SOURCE)
    public @interface RequiresGeneratedCode {
    }

    /**
     * Rejects elements unless the class generated by {@link GeneratesCode}'s processor is present.
     */
    private static class RequiresGeneratedCodeProcessor extends BaseAnnotationProcessor {

        int rejectedRounds;
        final List<Map<String, Set<Element>>> processArguments =
                new ArrayList<>();

        @Override
        protected Iterable<? extends Step> steps() {
            return ImmutableSet.of(
                    new Step() {
                        @Override
                        public Set<? extends Element> process(
                                Map<String, Set<Element>> elementsByAnnotation) {
                            processArguments.add(elementsByAnnotation);
                            TypeElement requiredClass =
                                    processingEnv.getElementUtils().getTypeElement("test.SomeGeneratedClass");
                            if (requiredClass == null) {
                                rejectedRounds++;
                                return elementsByAnnotation.values().stream().flatMap(Collection::stream).collect(Collectors.toSet());
                            }
                            generateClass(processingEnv.getFiler(), "GeneratedByRequiresGeneratedCodeProcessor");
                            return Set.of();
                        }

                        @Override
                        public Set<String> annotations() {
                            return Set.of(ENCLOSING_CLASS_NAME + ".RequiresGeneratedCode");
                        }
                    },
                    new Step() {
                        @Override
                        public ImmutableSet<? extends Element> process(
                                Map<String, Set<Element>> elementsByAnnotation) {
                            return ImmutableSet.of();
                        }

                        @Override
                        public ImmutableSet<String> annotations() {
                            return ImmutableSet.of(ENCLOSING_CLASS_NAME + ".AnAnnotation");
                        }
                    });
        }

        List<Map<String, Set<Element>>> processArguments() {
            return processArguments;
        }
    }

    @Retention(RetentionPolicy.SOURCE)
    public @interface GeneratesCode {
    }

    /** Generates a class called {@code test.SomeGeneratedClass}. */
    public static class GeneratesCodeProcessor extends BaseAnnotationProcessor {
        @Override
        protected Iterable<? extends Step> steps() {
            return ImmutableSet.of(
                    new Step() {
                        @Override
                        public ImmutableSet<? extends Element> process(
                                Map<String, Set<Element>> elementsByAnnotation) {
                            generateClass(processingEnv.getFiler(), "SomeGeneratedClass");
                            return ImmutableSet.of();
                        }

                        @Override
                        public ImmutableSet<String> annotations() {
                            return ImmutableSet.of(ENCLOSING_CLASS_NAME + ".GeneratesCode");
                        }
                    });
        }
    }

    public @interface AnAnnotation {
    }

    /** When annotating a type {@code Foo}, generates a class called {@code FooXYZ}. */
    public static class AnAnnotationProcessor extends BaseAnnotationProcessor {

        @Override
        protected Iterable<? extends Step> steps() {
            return ImmutableSet.of(
                    new Step() {
                        @Override
                        public ImmutableSet<Element> process(
                                Map<String, Set<Element>> elementsByAnnotation) {
                            for (Element element : elementsByAnnotation.values().stream().flatMap(Collection::stream).collect(Collectors.toSet())) {
                                generateClass(processingEnv.getFiler(), element.getSimpleName() + "XYZ");
                            }
                            return ImmutableSet.of();
                        }

                        @Override
                        public ImmutableSet<String> annotations() {
                            return ImmutableSet.of(ENCLOSING_CLASS_NAME + ".AnAnnotation");
                        }
                    });
        }
    }

    /** An annotation which causes an annotation processing error. */
    public @interface CauseError {
    }

    /** Report an error for any class annotated. */
    public static class CauseErrorProcessor extends BaseAnnotationProcessor {

        @Override
        protected Iterable<? extends Step> steps() {
            return ImmutableSet.of(
                    new Step() {
                        @Override
                        public Set<Element> process(
                                Map<String, Set<Element>> elementsByAnnotation) {
                            Set<Element> allElements = elementsByAnnotation.values().stream()
                                    .flatMap(Collection::stream)
                                    .collect(Collectors.toSet());
                            for (Element e : allElements) {
                                processingEnv.getMessager().printMessage(ERROR, "purposeful error", e);
                            }
                            return allElements;
                        }

                        @Override
                        public Set<String> annotations() {
                            return ImmutableSet.of(ENCLOSING_CLASS_NAME + ".CauseError");
                        }
                    });
        }
    }

    public static class MissingAnnotationProcessor extends BaseAnnotationProcessor {

        private Map<String, Set<Element>> elementsByAnnotation;

        @Override
        protected Iterable<? extends Step> steps() {
            return ImmutableSet.of(
                    new Step() {
                        @Override
                        public Set<Element> process(
                                Map<String, Set<Element>> elementsByAnnotation) {
                            MissingAnnotationProcessor.this.elementsByAnnotation = elementsByAnnotation;
                            for (Element element : elementsByAnnotation.values().stream().flatMap(Collection::stream).collect(Collectors.toSet())) {
                                generateClass(processingEnv.getFiler(), element.getSimpleName() + "XYZ");
                            }
                            return Set.of();
                        }

                        @Override
                        public Set<String> annotations() {
                            return Set.of(
                                    "test.SomeNonExistentClass", ENCLOSING_CLASS_NAME + ".AnAnnotation");
                        }
                    });
        }

        Map<String, Set<Element>> getElementsByAnnotation() {
            return elementsByAnnotation;
        }
    }

    @Retention(RetentionPolicy.SOURCE)
    public @interface ReferencesAClass {
        Class<?> value();
    }

    @Rule
    public CompilationRule compilation = new CompilationRule();

    @Test
    public void properlyDefersProcessing_typeElement() {
        JavaFileObject classAFileObject =
                JavaFileObjects.forSourceLines(
                        "test.ClassA",
                        "package test;",
                        "",
                        "@" + RequiresGeneratedCode.class.getCanonicalName(),
                        "public class ClassA {",
                        "  SomeGeneratedClass sgc;",
                        "}");
        JavaFileObject classBFileObject =
                JavaFileObjects.forSourceLines(
                        "test.ClassB",
                        "package test;",
                        "",
                        "@" + GeneratesCode.class.getCanonicalName(),
                        "public class ClassB {}");
        RequiresGeneratedCodeProcessor requiresGeneratedCodeProcessor =
                new RequiresGeneratedCodeProcessor();
        assertAbout(javaSources())
                .that(ImmutableList.of(classAFileObject, classBFileObject))
                .processedWith(requiresGeneratedCodeProcessor, new GeneratesCodeProcessor())
                .compilesWithoutError()
                .and()
                .generatesFileNamed(
                        SOURCE_OUTPUT, "test", "GeneratedByRequiresGeneratedCodeProcessor.java");
        assertThat(requiresGeneratedCodeProcessor.rejectedRounds).isEqualTo(0);
    }

    @Test
    public void properlyDefersProcessing_nestedTypeValidBeforeOuterType() {
        JavaFileObject source =
                JavaFileObjects.forSourceLines(
                        "test.ValidInRound2",
                        "package test;",
                        "",
                        "@" + AnAnnotation.class.getCanonicalName(),
                        "public class ValidInRound2 {",
                        "  ValidInRound1XYZ vir1xyz;",
                        "  @" + AnAnnotation.class.getCanonicalName(),
                        "  static class ValidInRound1 {}",
                        "}");
        assertAbout(javaSource())
                .that(source)
                .processedWith(new AnAnnotationProcessor())
                .compilesWithoutError()
                .and()
                .generatesFileNamed(SOURCE_OUTPUT, "test", "ValidInRound2XYZ.java");
    }

    @Test
    public void properlyDefersProcessing_packageElement() {
        JavaFileObject classAFileObject =
                JavaFileObjects.forSourceLines(
                        "test.ClassA",
                        "package test;",
                        "",
                        "@" + GeneratesCode.class.getCanonicalName(),
                        "public class ClassA {",
                        "}");
        JavaFileObject packageFileObject =
                JavaFileObjects.forSourceLines(
                        "test.package-info",
                        "@" + RequiresGeneratedCode.class.getCanonicalName(),
                        "@" + ReferencesAClass.class.getCanonicalName() + "(SomeGeneratedClass.class)",
                        "package test;");
        RequiresGeneratedCodeProcessor requiresGeneratedCodeProcessor =
                new RequiresGeneratedCodeProcessor();
        assertAbout(javaSources())
                .that(ImmutableList.of(classAFileObject, packageFileObject))
                .processedWith(requiresGeneratedCodeProcessor, new GeneratesCodeProcessor())
                .compilesWithoutError()
                .and()
                .generatesFileNamed(
                        SOURCE_OUTPUT, "test", "GeneratedByRequiresGeneratedCodeProcessor.java");
        assertThat(requiresGeneratedCodeProcessor.rejectedRounds).isEqualTo(0);
    }

    @Test
    public void properlyDefersProcessing_argumentElement() {
        JavaFileObject classAFileObject =
                JavaFileObjects.forSourceLines(
                        "test.ClassA",
                        "package test;",
                        "",
                        "public class ClassA {",
                        "  SomeGeneratedClass sgc;",
                        "  public void myMethod(@"
                                + RequiresGeneratedCode.class.getCanonicalName()
                                + " int myInt)",
                        "  {}",
                        "}");
        JavaFileObject classBFileObject =
                JavaFileObjects.forSourceLines(
                        "test.ClassB",
                        "package test;",
                        "",
                        "public class ClassB {",
                        "  public void myMethod(@" + GeneratesCode.class.getCanonicalName() + " int myInt) {}",
                        "}");
        RequiresGeneratedCodeProcessor requiresGeneratedCodeProcessor =
                new RequiresGeneratedCodeProcessor();
        assertAbout(javaSources())
                .that(ImmutableList.of(classAFileObject, classBFileObject))
                .processedWith(requiresGeneratedCodeProcessor, new GeneratesCodeProcessor())
                .compilesWithoutError()
                .and()
                .generatesFileNamed(
                        SOURCE_OUTPUT, "test", "GeneratedByRequiresGeneratedCodeProcessor.java");
        assertThat(requiresGeneratedCodeProcessor.rejectedRounds).isEqualTo(0);
    }

    @Test
    public void properlyDefersProcessing_rejectsElement() {
        JavaFileObject classAFileObject =
                JavaFileObjects.forSourceLines(
                        "test.ClassA",
                        "package test;",
                        "",
                        "@" + RequiresGeneratedCode.class.getCanonicalName(),
                        "public class ClassA {",
                        "  @" + AnAnnotation.class.getCanonicalName(),
                        "  public void method() {}",
                        "}");
        JavaFileObject classBFileObject =
                JavaFileObjects.forSourceLines(
                        "test.ClassB",
                        "package test;",
                        "",
                        "@" + GeneratesCode.class.getCanonicalName(),
                        "public class ClassB {}");
        RequiresGeneratedCodeProcessor requiresGeneratedCodeProcessor =
                new RequiresGeneratedCodeProcessor();
        assertAbout(javaSources())
                .that(ImmutableList.of(classAFileObject, classBFileObject))
                .processedWith(requiresGeneratedCodeProcessor, new GeneratesCodeProcessor())
                .compilesWithoutError()
                .and()
                .generatesFileNamed(
                        SOURCE_OUTPUT, "test", "GeneratedByRequiresGeneratedCodeProcessor.java");
        assertThat(requiresGeneratedCodeProcessor.rejectedRounds).isEqualTo(1);

        // Re b/118372780: Assert that the right deferred elements are passed back, and not any enclosed
        // elements annotated with annotations from a different step.
        List<Map<String, Set<Element>>> actual = requiresGeneratedCodeProcessor.processArguments();
        assertThat(actual)
                .comparingElementsUsing(setMultimapValuesByString())
                .containsExactly(
                        Map.of(RequiresGeneratedCode.class.getCanonicalName(), Set.of("test.ClassA")),
                        Map.of(RequiresGeneratedCode.class.getCanonicalName(), Set.of("test.ClassA")))
                .inOrder();
    }

    private static <K, V>
    Correspondence<Map<K, Set<V>>, Map<K, Set<String>>> setMultimapValuesByString() {
        return Correspondence.from(
                (actual, expected) -> {
                    Map<K, Set<String>> transformed = new LinkedHashMap<>();
                    for (Map.Entry<K, Set<V>> e : actual.entrySet()) {
                        transformed.put(e.getKey(), e.getValue().stream()
                                .map(Objects::toString)
                                .collect(Collectors.toCollection(LinkedHashSet::new)));
                    }
                    return transformed.equals(expected);
                },
                "is equivalent comparing multimap values by `toString()` to");
    }

    @Test
    public void properlySkipsMissingAnnotations_generatesClass() {
        JavaFileObject source =
                JavaFileObjects.forSourceLines(
                        "test.ValidInRound2",
                        "package test;",
                        "",
                        "@" + AnAnnotation.class.getCanonicalName(),
                        "public class ValidInRound2 {",
                        "  ValidInRound1XYZ vir1xyz;",
                        "  @" + AnAnnotation.class.getCanonicalName(),
                        "  static class ValidInRound1 {}",
                        "}");
        Compilation compilation =
                javac().withProcessors(new MissingAnnotationProcessor()).compile(source);
        assertThat(compilation).succeeded();
        assertThat(compilation).generatedSourceFile("test.ValidInRound2XYZ");
    }

    @Test
    public void properlySkipsMissingAnnotations_passesValidAnnotationsToProcess() {
        JavaFileObject source =
                JavaFileObjects.forSourceLines(
                        "test.ClassA",
                        "package test;",
                        "",
                        "@" + AnAnnotation.class.getCanonicalName(),
                        "public class ClassA {",
                        "}");
        MissingAnnotationProcessor missingAnnotationProcessor = new MissingAnnotationProcessor();
        assertThat(javac().withProcessors(missingAnnotationProcessor).compile(source)).succeeded();
        assertThat(missingAnnotationProcessor.getElementsByAnnotation().keySet())
                .containsExactly(AnAnnotation.class.getCanonicalName());
        assertThat(missingAnnotationProcessor.getElementsByAnnotation().values()).hasSize(1);
    }

    @Test
    public void reportsMissingType() {
        JavaFileObject classAFileObject =
                JavaFileObjects.forSourceLines(
                        "test.ClassA",
                        "package test;",
                        "",
                        "@" + RequiresGeneratedCode.class.getCanonicalName(),
                        "public class ClassA {",
                        "  SomeGeneratedClass bar;",
                        "}");
        assertAbout(javaSources())
                .that(ImmutableList.of(classAFileObject))
                .processedWith(new RequiresGeneratedCodeProcessor())
                .failsToCompile()
                .withErrorContaining(RequiresGeneratedCodeProcessor.class.getCanonicalName())
                .in(classAFileObject)
                .onLine(4);
    }

    @Test
    public void reportsMissingTypeSuppressedWhenOtherErrors() {
        JavaFileObject classAFileObject =
                JavaFileObjects.forSourceLines(
                        "test.ClassA",
                        "package test;",
                        "",
                        "@" + CauseError.class.getCanonicalName(),
                        "public class ClassA {}");
        assertAbout(javaSources())
                .that(ImmutableList.of(classAFileObject))
                .processedWith(new CauseErrorProcessor())
                .failsToCompile()
                .withErrorCount(1)
                .withErrorContaining("purposeful");
    }

    private static void generateClass(Filer filer, String generatedClassName) {
        PrintWriter writer = null;
        try {
            writer = new PrintWriter(filer.createSourceFile("test." + generatedClassName).openWriter());
            writer.println("package test;");
            writer.println("public class " + generatedClassName + " {}");
        } catch (IOException e) {
            throw new RuntimeException(e);
        } finally {
            if (writer != null) {
                writer.close();
            }
        }
    }
}
