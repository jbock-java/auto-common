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

import com.google.common.base.Ascii;
import com.google.common.base.Optional;
import com.google.common.base.Predicates;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableSetMultimap;
import com.google.common.collect.Iterables;
import com.google.common.collect.LinkedHashMultimap;
import com.google.common.collect.SetMultimap;
import com.google.common.collect.Sets;

import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.Messager;
import javax.annotation.processing.ProcessingEnvironment;
import javax.annotation.processing.Processor;
import javax.annotation.processing.RoundEnvironment;
import javax.lang.model.element.Element;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Name;
import javax.lang.model.element.PackageElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.ErrorType;
import javax.lang.model.util.Elements;
import javax.lang.model.util.SimpleElementVisitor8;
import java.lang.annotation.Annotation;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;

import static com.google.auto.common.MoreElements.asExecutable;
import static com.google.auto.common.MoreElements.asPackage;
import static com.google.auto.common.MoreStreams.toImmutableMap;
import static com.google.auto.common.MoreStreams.toImmutableSet;
import static com.google.auto.common.SuperficialValidation.validateElement;
import static com.google.common.collect.Iterables.transform;
import static com.google.common.collect.Multimaps.filterKeys;
import static java.util.Objects.requireNonNull;
import static javax.lang.model.element.ElementKind.PACKAGE;
import static javax.tools.Diagnostic.Kind.ERROR;

/**
 * An abstract {@link Processor} implementation that defers processing of {@link Element}s to later
 * rounds if they cannot be processed.
 *
 * <p>Subclasses put their processing logic in {@link Step} implementations. The steps are passed to
 * the processor by returning them in the {@link #steps()} method, and can access the {@link
 * ProcessingEnvironment} using {@link #processingEnv}.
 *
 * <p>Any logic that needs to happen once per round can be specified by overriding {@link
 * #postRound(RoundEnvironment)}.
 *
 * <h3>Ill-formed elements are deferred</h3>
 *
 * Any annotated element whose nearest enclosing type is not well-formed is deferred, and not passed
 * to any {@code Step}. This helps processors to avoid many common pitfalls, such as {@link
 * ErrorType} instances, {@link ClassCastException}s and badly coerced types.
 *
 * <p>A non-package element is considered well-formed if its type, type parameters, parameters,
 * default values, supertypes, annotations, and enclosed elements are. Package elements are treated
 * similarly, except that their enclosed elements are not validated. See {@link
 * SuperficialValidation#validateElement(Element)} for details.
 *
 * <p>The primary disadvantage to this validation is that any element that forms a circular
 * dependency with a type generated by another {@code BasicAnnotationProcessor} will never compile
 * because the element will never be fully complete. All such compilations will fail with an error
 * message on the offending type that describes the issue.
 *
 * <h3>Each {@code Step} can defer elements</h3>
 *
 * <p>Each {@code Step} can defer elements by including them in the set returned by {@link
 * Step#process(ImmutableSetMultimap)}; elements deferred by a step will be passed back to that step
 * in a later round of processing.
 *
 * <p>This feature is useful when one processor may depend on code generated by another, independent
 * processor, in a way that isn't caught by the well-formedness check described above. For example,
 * if an element {@code A} cannot be processed because processing it depends on the existence of
 * some class {@code B}, then {@code A} should be deferred until a later round of processing, when
 * {@code B} will have been generated by another processor.
 *
 * <p>If {@code A} directly references {@code B}, then the well-formedness check will correctly
 * defer processing of {@code A} until {@code B} has been generated.
 *
 * <p>However, if {@code A} references {@code B} only indirectly (for example, from within a method
 * body), then the well-formedness check will not defer processing {@code A}, but a processing step
 * can reject {@code A}.
 */
public abstract class BasicAnnotationProcessor extends AbstractProcessor {

    private final Set<ElementName> deferredElementNames = new LinkedHashSet<>();
    private final SetMultimap<Step, ElementName> elementsDeferredBySteps =
            LinkedHashMultimap.create();

    private Elements elements;
    private Messager messager;
    private ImmutableList<? extends Step> steps;

    @Override
    public final synchronized void init(ProcessingEnvironment processingEnv) {
        super.init(processingEnv);
        this.elements = processingEnv.getElementUtils();
        this.messager = processingEnv.getMessager();
        this.steps = ImmutableList.copyOf(steps());
    }

    /**
     * Creates {@linkplain ProcessingStep processing steps} for this processor. {@link #processingEnv}
     * is guaranteed to be set when this method is invoked.
     *
     * @deprecated Implement {@link #steps()} instead.
     */
    @Deprecated
    protected Iterable<? extends ProcessingStep> initSteps() {
        throw new AssertionError("If steps() is not implemented, initSteps() must be.");
    }

    /**
     * Creates {@linkplain Step processing steps} for this processor. {@link #processingEnv} is
     * guaranteed to be set when this method is invoked.
     *
     * <p>Note: If you are migrating some steps from {@link ProcessingStep} to {@link Step}, then you
     * can call {@link #asStep(ProcessingStep)} on any unmigrated steps.
     */
    protected Iterable<? extends Step> steps() {
        return Iterables.transform(initSteps(), BasicAnnotationProcessor::asStep);
    }

    /**
     * An optional hook for logic to be executed at the end of each round.
     *
     * @deprecated use {@link #postRound(RoundEnvironment)} instead
     */
    @Deprecated
    protected void postProcess() {
    }

    /** An optional hook for logic to be executed at the end of each round. */
    protected void postRound(RoundEnvironment roundEnv) {
        if (!roundEnv.processingOver()) {
            postProcess();
        }
    }

    private ImmutableSet<TypeElement> getSupportedAnnotationTypeElements() {
        Preconditions.checkState(steps != null);
        return steps.stream()
                .flatMap(step -> getSupportedAnnotationTypeElements(step).stream())
                .collect(toImmutableSet());
    }

    private ImmutableSet<TypeElement> getSupportedAnnotationTypeElements(Step step) {
        return step.annotations().stream()
                .map(elements::getTypeElement)
                .filter(Objects::nonNull)
                .collect(toImmutableSet());
    }

    /**
     * Returns the set of supported annotation types as collected from registered {@linkplain Step
     * processing steps}.
     */
    @Override
    public final ImmutableSet<String> getSupportedAnnotationTypes() {
        Preconditions.checkState(steps != null);
        return steps.stream()
                .flatMap(step -> step.annotations().stream())
                .collect(toImmutableSet());
    }

    @Override
    public final boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
        Preconditions.checkState(elements != null);
        Preconditions.checkState(messager != null);
        Preconditions.checkState(steps != null);

        // If this is the last round, report all of the missing elements if there
        // were no errors raised in the round; otherwise reporting the missing
        // elements just adds noise the output.
        if (roundEnv.processingOver()) {
            postRound(roundEnv);
            if (!roundEnv.errorRaised()) {
                reportMissingElements(
                        ImmutableSet.<ElementName>builder()
                                .addAll(deferredElementNames)
                                .addAll(elementsDeferredBySteps.values())
                                .build());
            }
            return false;
        }

        process(validElements(roundEnv));

        postRound(roundEnv);

        return false;
    }

    /** Processes the valid elements, including those previously deferred by each step. */
    private void process(ImmutableSetMultimap<TypeElement, Element> validElements) {
        for (Step step : steps) {
            ImmutableSet<TypeElement> annotationTypes = getSupportedAnnotationTypeElements(step);
            ImmutableSetMultimap<TypeElement, Element> stepElements =
                    new ImmutableSetMultimap.Builder<TypeElement, Element>()
                            .putAll(indexByAnnotation(elementsDeferredBySteps.get(step), annotationTypes))
                            .putAll(filterKeys(validElements, Predicates.in(annotationTypes)))
                            .build();
            if (stepElements.isEmpty()) {
                elementsDeferredBySteps.removeAll(step);
            } else {
                Set<? extends Element> rejectedElements =
                        step.process(toClassNameKeyedMultimap(stepElements));
                elementsDeferredBySteps.replaceValues(
                        step, transform(rejectedElements, ElementName::forAnnotatedElement));
            }
        }
    }

    private void reportMissingElements(Set<ElementName> missingElementNames) {
        for (ElementName missingElementName : missingElementNames) {
            Optional<? extends Element> missingElement = missingElementName.getElement(elements);
            if (missingElement.isPresent()) {
                messager.printMessage(
                        ERROR,
                        processingErrorMessage(
                                "this " + Ascii.toLowerCase(missingElement.get().getKind().name())),
                        missingElement.get());
            } else {
                messager.printMessage(ERROR, processingErrorMessage(missingElementName.name()));
            }
        }
    }

    private String processingErrorMessage(String target) {
        return String.format(
                "[%s:MiscError] %s was unable to process %s because not all of its dependencies could be "
                        + "resolved. Check for compilation errors or a circular dependency with generated "
                        + "code.",
                getClass().getSimpleName(), getClass().getCanonicalName(), target);
    }

    /**
     * Returns the valid annotated elements contained in all of the deferred elements. If none are
     * found for a deferred element, defers it again.
     */
    private ImmutableSetMultimap<TypeElement, Element> validElements(RoundEnvironment roundEnv) {
        ImmutableSet<ElementName> prevDeferredElementNames = ImmutableSet.copyOf(deferredElementNames);
        deferredElementNames.clear();

        ImmutableSetMultimap.Builder<TypeElement, Element> deferredElementsByAnnotationBuilder =
                ImmutableSetMultimap.builder();
        for (ElementName deferredElementName : prevDeferredElementNames) {
            Optional<? extends Element> deferredElement = deferredElementName.getElement(elements);
            if (deferredElement.isPresent()) {
                findAnnotatedElements(
                        deferredElement.get(),
                        getSupportedAnnotationTypeElements(),
                        deferredElementsByAnnotationBuilder);
            } else {
                deferredElementNames.add(deferredElementName);
            }
        }

        ImmutableSetMultimap<TypeElement, Element> deferredElementsByAnnotation =
                deferredElementsByAnnotationBuilder.build();

        ImmutableSetMultimap.Builder<TypeElement, Element> validElements =
                ImmutableSetMultimap.builder();

        Set<ElementName> validElementNames = new LinkedHashSet<>();

        // Look at the elements we've found and the new elements from this round and validate them.
        for (TypeElement annotationType : getSupportedAnnotationTypeElements()) {
            Set<? extends Element> roundElements = roundEnv.getElementsAnnotatedWith(annotationType);
            ImmutableSet<Element> prevRoundElements = deferredElementsByAnnotation.get(annotationType);
            for (Element element : Sets.union(roundElements, prevRoundElements)) {
                ElementName elementName = ElementName.forAnnotatedElement(element);
                boolean isValidElement =
                        validElementNames.contains(elementName)
                                || (!deferredElementNames.contains(elementName)
                                && validateElement(
                                element.getKind().equals(PACKAGE) ? element : getEnclosingType(element)));
                if (isValidElement) {
                    validElements.put(annotationType, element);
                    validElementNames.add(elementName);
                } else {
                    deferredElementNames.add(elementName);
                }
            }
        }

        return validElements.build();
    }

    private ImmutableSetMultimap<TypeElement, Element> indexByAnnotation(
            Set<ElementName> annotatedElements, ImmutableSet<TypeElement> annotationTypes) {
        ImmutableSetMultimap.Builder<TypeElement, Element> deferredElements =
                ImmutableSetMultimap.builder();
        for (ElementName elementName : annotatedElements) {
            Optional<? extends Element> element = elementName.getElement(elements);
            if (element.isPresent()) {
                findAnnotatedElements(element.get(), annotationTypes, deferredElements);
            }
        }
        return deferredElements.build();
    }

    /**
     * Adds {@code element} and its enclosed elements to {@code annotatedElements} if they are
     * annotated with any annotations in {@code annotationTypes}. Does not traverse to member types of
     * {@code element}, so that if {@code Outer} is passed in the example below, looking for
     * {@code @X}, then {@code Outer}, {@code Outer.foo}, and {@code Outer.foo()} will be added to the
     * multimap, but neither {@code Inner} nor its members will.
     *
     * <pre><code>
     *   {@literal @}X class Outer {
     *     {@literal @}X Object foo;
     *     {@literal @}X void foo() {}
     *     {@literal @}X static class Inner {
     *       {@literal @}X Object bar;
     *       {@literal @}X void bar() {}
     *     }
     *   }
     * </code></pre>
     */
    private static void findAnnotatedElements(
            Element element,
            ImmutableSet<TypeElement> annotationTypes,
            ImmutableSetMultimap.Builder<TypeElement, Element> annotatedElements) {
        for (Element enclosedElement : element.getEnclosedElements()) {
            if (!enclosedElement.getKind().isClass() && !enclosedElement.getKind().isInterface()) {
                findAnnotatedElements(enclosedElement, annotationTypes, annotatedElements);
            }
        }

        // element.getEnclosedElements() does NOT return parameter elements
        if (element instanceof ExecutableElement) {
            for (Element parameterElement : asExecutable(element).getParameters()) {
                findAnnotatedElements(parameterElement, annotationTypes, annotatedElements);
            }
        }
        for (TypeElement annotationType : annotationTypes) {
            if (isAnnotationPresent(element, annotationType)) {
                annotatedElements.put(annotationType, element);
            }
        }
    }

    private static boolean isAnnotationPresent(Element element, TypeElement annotationType) {
        return element.getAnnotationMirrors().stream()
                .anyMatch(
                        mirror -> MoreTypes.asTypeElement(mirror.getAnnotationType()).equals(annotationType));
    }

    /**
     * Returns the nearest enclosing {@link TypeElement} to the current element, throwing an {@link
     * IllegalArgumentException} if the provided {@link Element} is a {@link PackageElement} or is
     * otherwise not enclosed by a type.
     */
    // TODO(user) move to MoreElements and make public.
    private static TypeElement getEnclosingType(Element element) {
        return element.accept(
                new SimpleElementVisitor8<TypeElement, Void>() {
                    @Override
                    protected TypeElement defaultAction(Element e, Void p) {
                        return e.getEnclosingElement().accept(this, p);
                    }

                    @Override
                    public TypeElement visitType(TypeElement e, Void p) {
                        return e;
                    }

                    @Override
                    public TypeElement visitPackage(PackageElement e, Void p) {
                        throw new IllegalArgumentException();
                    }
                },
                null);
    }

    private static ImmutableSetMultimap<String, Element> toClassNameKeyedMultimap(
            SetMultimap<TypeElement, Element> elements) {
        ImmutableSetMultimap.Builder<String, Element> builder = ImmutableSetMultimap.builder();
        elements
                .asMap()
                .forEach(
                        (annotation, element) ->
                                builder.putAll(annotation.getQualifiedName().toString(), element));
        return builder.build();
    }

    /**
     * Wraps the passed {@link ProcessingStep} in a {@link Step}. This is a convenience method to
     * allow incremental migration to a String-based API. This method can be used to return a not yet
     * converted {@link ProcessingStep} from {@link BasicAnnotationProcessor#steps()}.
     */
    protected static Step asStep(ProcessingStep processingStep) {
        return new ProcessingStepAsStep(processingStep);
    }

    /**
     * The unit of processing logic that runs under the guarantee that all elements are complete and
     * well-formed. A step may reject elements that are not ready for processing but may be at a later
     * round.
     */
    public interface Step {

        /**
         * The set of fully-qualified annotation type names processed by this step.
         *
         * <p>Warning: If the returned names are not names of annotations, they'll be ignored.
         */
        Set<String> annotations();

        /**
         * The implementation of processing logic for the step. It is guaranteed that the keys in {@code
         * elementsByAnnotation} will be a subset of the set returned by {@link #annotations()}.
         *
         * @return the elements (a subset of the values of {@code elementsByAnnotation}) that this step
         *     is unable to process, possibly until a later processing round. These elements will be
         *     passed back to this step at the next round of processing.
         */
        Set<? extends Element> process(ImmutableSetMultimap<String, Element> elementsByAnnotation);
    }

    /**
     * The unit of processing logic that runs under the guarantee that all elements are complete and
     * well-formed. A step may reject elements that are not ready for processing but may be at a later
     * round.
     *
     * @deprecated Implement {@link Step} instead. See {@link BasicAnnotationProcessor#steps()}.
     */
    @Deprecated
    public interface ProcessingStep {

        /** The set of annotation types processed by this step. */
        Set<? extends Class<? extends Annotation>> annotations();

        /**
         * The implementation of processing logic for the step. It is guaranteed that the keys in {@code
         * elementsByAnnotation} will be a subset of the set returned by {@link #annotations()}.
         *
         * @return the elements (a subset of the values of {@code elementsByAnnotation}) that this step
         *     is unable to process, possibly until a later processing round. These elements will be
         *     passed back to this step at the next round of processing.
         */
        Set<? extends Element> process(
                SetMultimap<Class<? extends Annotation>, Element> elementsByAnnotation);
    }

    private static class ProcessingStepAsStep implements Step {

        private final ProcessingStep processingStep;
        private final ImmutableMap<String, Class<? extends Annotation>> annotationsByName;

        ProcessingStepAsStep(ProcessingStep processingStep) {
            this.processingStep = processingStep;
            this.annotationsByName =
                    processingStep.annotations().stream()
                            .collect(
                                    toImmutableMap(
                                            c -> requireNonNull(c.getCanonicalName()),
                                            (Class<? extends Annotation> aClass) -> aClass));
        }

        @Override
        public Set<String> annotations() {
            return annotationsByName.keySet();
        }

        @Override
        public Set<? extends Element> process(
                ImmutableSetMultimap<String, Element> elementsByAnnotation) {
            return processingStep.process(toClassKeyedMultimap(elementsByAnnotation));
        }

        private ImmutableSetMultimap<Class<? extends Annotation>, Element> toClassKeyedMultimap(
                SetMultimap<String, Element> elements) {
            ImmutableSetMultimap.Builder<Class<? extends Annotation>, Element> builder =
                    ImmutableSetMultimap.builder();
            elements
                    .asMap()
                    .forEach(
                            (annotationName, annotatedElements) -> {
                                Class<? extends Annotation> annotation = annotationsByName.get(annotationName);
                                if (annotation != null) { // should not be null
                                    builder.putAll(annotation, annotatedElements);
                                }
                            });
            return builder.build();
        }
    }

    /**
     * A package or type name.
     *
     * <p>It's unfortunate that we have to track types and packages separately, but since there are
     * two different methods to look them up in {@link Elements}, we end up with a lot of parallel
     * logic. :(
     *
     * <p>Packages declared (and annotated) in {@code package-info.java} are tracked as deferred
     * packages, type elements are tracked directly, and all other elements are tracked via their
     * nearest enclosing type.
     */
    private static final class ElementName {
        private enum Kind {
            PACKAGE_NAME,
            TYPE_NAME,
        }

        private final Kind kind;
        private final String name;

        private ElementName(Kind kind, Name name) {
            this.kind = requireNonNull(kind);
            this.name = name.toString();
        }

        /**
         * An {@link ElementName} for an annotated element. If {@code element} is a package, uses the
         * fully qualified name of the package. If it's a type, uses its fully qualified name.
         * Otherwise, uses the fully-qualified name of the nearest enclosing type.
         */
        static ElementName forAnnotatedElement(Element element) {
            return element.getKind() == PACKAGE
                    ? new ElementName(Kind.PACKAGE_NAME, asPackage(element).getQualifiedName())
                    : new ElementName(Kind.TYPE_NAME, getEnclosingType(element).getQualifiedName());
        }

        /** The fully-qualified name of the element. */
        String name() {
            return name;
        }

        /**
         * The {@link Element} whose fully-qualified name is {@link #name()}. Absent if the relevant
         * method on {@link Elements} returns {@code null}.
         */
        Optional<? extends Element> getElement(Elements elements) {
            return Optional.fromNullable(
                    kind == Kind.PACKAGE_NAME
                            ? elements.getPackageElement(name)
                            : elements.getTypeElement(name));
        }

        @Override
        public boolean equals(Object object) {
            if (!(object instanceof ElementName)) {
                return false;
            }

            ElementName that = (ElementName) object;
            return this.kind == that.kind && this.name.equals(that.name);
        }

        @Override
        public int hashCode() {
            return Objects.hash(kind, name);
        }
    }
}
