package me.tbsten.katachi.test.gradle;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.LinkedHashMap;
import java.util.Map;

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

    /** Calls the package-private {@code KatachiEntryPointSource.render(...)} through reflection. */
    private static String render(String architectureClassName, Map<String, String> processors)
            throws ReflectiveOperationException {
        Class<?> type = Class.forName(CLASS_NAME);
        Method render = type.getDeclaredMethod("render", String.class, Map.class);
        render.setAccessible(true);
        try {
            return (String) render.invoke(null, architectureClassName, processors);
        } catch (InvocationTargetException e) {
            if (e.getCause() instanceof RuntimeException) {
                throw (RuntimeException) e.getCause();
            }
            throw e;
        }
    }
}
