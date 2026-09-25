package me.tbsten.katachi.test.gradle;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Pins down {@code KatachiEntryPointSource.render(...)}'s output as text, without starting
 * Gradle or compiling any Kotlin.
 *
 * <p>{@code KatachiEntryPointSource} is package-private, and this test lives in a different
 * package ({@code me.tbsten.katachi.test.gradle}, matching every other test of this module) so
 * that it exercises the same visibility a generated build would. It reaches the class through
 * reflection for that reason -- the alternative, moving this test into
 * {@code me.tbsten.katachi.gradle}, would read the source under different rules than production
 * code does.
 */
class KatachiEntryPointSourceTest {

    private static final String CLASS_NAME = "me.tbsten.katachi.gradle.KatachiEntryPointSource";

    @Test
    @DisplayName("architecture の import 行が出る")
    void architectureImportLineIsPresent() throws ReflectiveOperationException {
        String source = render("com.example.projectArchitecture", new LinkedHashMap<>());
        assertTrue(
                source.contains("import com.example.projectArchitecture"),
                "expected an import of the architecture class:\n" + source);
    }

    @Test
    @DisplayName("architecture の override は型注釈つきで出る")
    void architecturePropertyCarriesATypeAnnotation() throws ReflectiveOperationException {
        String source = render("com.example.projectArchitecture", new LinkedHashMap<>());
        assertTrue(
                source.contains(
                        "public override val architecture: me.tbsten.katachi.dsl.Architecture = "
                                + "projectArchitecture"),
                "expected a type-annotated architecture property:\n" + source);
    }

    @Test
    @DisplayName("processor はキー順にソートされて出る -- 登録順を入れ替えても同じテキストになる")
    void processorsAreSortedByKey() throws ReflectiveOperationException {
        Map<String, String> inOrder = new LinkedHashMap<>();
        inOrder.put("layout", "me.tbsten.katachi.check.LayoutCheck");
        inOrder.put("konsist", "me.tbsten.katachi.check.KonsistCheck");

        Map<String, String> reversed = new LinkedHashMap<>();
        reversed.put("konsist", "me.tbsten.katachi.check.KonsistCheck");
        reversed.put("layout", "me.tbsten.katachi.check.LayoutCheck");

        String fromInOrder = render("com.example.projectArchitecture", inOrder);
        String fromReversed = render("com.example.projectArchitecture", reversed);

        assertEquals(fromInOrder, fromReversed);
        int konsistIndex = fromInOrder.indexOf("\"konsist\"");
        int layoutIndex = fromInOrder.indexOf("\"layout\"");
        assertTrue(konsistIndex >= 0 && konsistIndex < layoutIndex, "expected konsist before layout:\n" + fromInOrder);
    }

    @Test
    @DisplayName("登録が0件のとき emptyMap() になる")
    void noRegistrationsRendersEmptyMap() throws ReflectiveOperationException {
        String source = render("com.example.projectArchitecture", new LinkedHashMap<>());
        assertTrue(
                source.contains("public override val processors: Map<String, Class<*>> = emptyMap()"),
                "expected emptyMap():\n" + source);
    }

    @Test
    @DisplayName("生成された object は @Deprecated(HIDDEN) で、ソースからは解決できない")
    void generatedObjectIsHidden() throws ReflectiveOperationException {
        String source = render("com.example.projectArchitecture", new LinkedHashMap<>());
        int deprecatedIndex = source.indexOf("@Deprecated(");
        int levelIndex = source.indexOf("level = DeprecationLevel.HIDDEN");
        int objectIndex = source.indexOf("public object GeneratedKatachiEntryPoint");

        assertTrue(deprecatedIndex >= 0, "expected a @Deprecated annotation:\n" + source);
        assertTrue(levelIndex > deprecatedIndex, "expected HIDDEN inside it:\n" + source);
        // The annotation has to sit on the object, not on the file: a file-level `@Deprecated`
        // says something different and would not take the object out of resolution.
        assertTrue(
                objectIndex > levelIndex,
                "expected the annotation to be on the object:\n" + source);
    }

    @DisplayName("@file:OptIn は package 行より前にある")
    void optInIsBeforePackageDirective() throws ReflectiveOperationException {
        String source = render("com.example.projectArchitecture", new LinkedHashMap<>());
        // Both walls have to be opted in: the generated file names `KatachiEntryPoint` and the
        // registered processors, and all of them carry `@ExperimentalKatachiApi` (level ERROR).
        assertTrue(
                source.contains("me.tbsten.katachi.ExperimentalKatachiApi::class"),
                "the generated file must opt in to ExperimentalKatachiApi as well, or the user's "
                        + "own test compilation fails on it:\n" + source);
        int optInIndex = source.indexOf("@file:OptIn(me.tbsten.katachi.InternalKatachiApi::class,");
        int packageIndex = source.indexOf("package me.tbsten.katachi.generated");
        assertTrue(optInIndex >= 0, "expected the file-level OptIn annotation:\n" + source);
        assertTrue(packageIndex >= 0, "expected the package directive:\n" + source);
        assertTrue(optInIndex < packageIndex, "OptIn must come before package:\n" + source);
    }

    @Test
    @DisplayName("手で編集しない旨の先頭コメントが出る")
    void headerWarnsAgainstEditing() throws ReflectiveOperationException {
        String source = render("com.example.projectArchitecture", new LinkedHashMap<>());
        assertTrue(
                source.startsWith("// Generated by the katachi Gradle plugin"),
                "expected a generated-file header:\n" + source);
        assertTrue(source.contains("Do not edit"), "expected a \"do not edit\" warning:\n" + source);
    }

    @Test
    @DisplayName("手を挙げた processor が1つも無いと acceptsUndeclaredArgs は emptySet() になる")
    void noAcceptorsRendersEmptySet() throws ReflectiveOperationException {
        String source = render("com.example.projectArchitecture", new LinkedHashMap<>());

        assertTrue(
                source.contains(
                        "public override val acceptsUndeclaredArgs: Set<String> = emptySet()"),
                "expected emptySet():\n" + source);
    }

    @Test
    @DisplayName("手を挙げた processor は生成コードの acceptsUndeclaredArgs に出る")
    void acceptorsAreRenderedAsASetLiteral() throws ReflectiveOperationException {
        Set<String> acceptors = new LinkedHashSet<>();
        acceptors.add("template");

        String source = render("com.example.projectArchitecture", new LinkedHashMap<>(), acceptors);

        // This is the whole road the flag travels: build script -> registry-shaped generated
        // source -> KatachiEntryPoint -> checkNoUnknownArgs. Nothing carries it over the command
        // line, so if it is missing here it is missing everywhere.
        assertTrue(
                source.contains("public override val acceptsUndeclaredArgs: Set<String> = setOf(")
                        && source.contains("\"template\","),
                "expected the key in a setOf(...) literal:\n" + source);
    }

    @Test
    @DisplayName("acceptsUndeclaredArgs もキー順にソートされる -- 書いた順を変えても同じテキスト")
    void acceptorsAreSortedByKey() throws ReflectiveOperationException {
        Set<String> inOrder = new LinkedHashSet<>();
        inOrder.add("template");
        inOrder.add("scaffold");

        Set<String> reversed = new LinkedHashSet<>();
        reversed.add("scaffold");
        reversed.add("template");

        String fromInOrder = render("com.example.projectArchitecture", new LinkedHashMap<>(), inOrder);
        String fromReversed = render("com.example.projectArchitecture", new LinkedHashMap<>(), reversed);

        assertEquals(fromInOrder, fromReversed);
        assertTrue(
                fromInOrder.indexOf("\"scaffold\"") < fromInOrder.indexOf("\"template\""),
                "expected scaffold before template:\n" + fromInOrder);
    }

    /** Calls the package-private {@code KatachiEntryPointSource.render(...)} through reflection. */
    private static String render(String architectureClassName, Map<String, String> processors)
            throws ReflectiveOperationException {
        return render(architectureClassName, processors, Collections.<String>emptySet());
    }

    /** As above, with the keys that were let ask for extra {@code --arg} names. */
    private static String render(
            String architectureClassName,
            Map<String, String> processors,
            Set<String> acceptsUndeclaredArgs)
            throws ReflectiveOperationException {
        Class<?> type = Class.forName(CLASS_NAME);
        // Named with every parameter type: a `render` that grows an argument would otherwise not
        // fail to compile here, it would fail at run time with NoSuchMethodException.
        Method render = type.getDeclaredMethod("render", String.class, Map.class, Set.class);
        render.setAccessible(true);
        try {
            return (String) render.invoke(
                    null, architectureClassName, processors, acceptsUndeclaredArgs);
        } catch (InvocationTargetException e) {
            if (e.getCause() instanceof RuntimeException) {
                throw (RuntimeException) e.getCause();
            }
            throw e;
        }
    }
}
