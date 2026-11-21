package me.tbsten.katachi.test.gradle;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import me.tbsten.katachi.gradle.KatachiOnExisting;
import me.tbsten.katachi.gradle.KatachiProcessorOutputs;
import me.tbsten.katachi.gradle.KatachiProcessors;
import org.gradle.api.InvalidUserDataException;
import org.gradle.api.model.ObjectFactory;
import org.gradle.testfixtures.ProjectBuilder;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * What {@code katachi { processors { } } } answers with, including what it answers without
 * being asked.
 *
 * <p>No Gradle build is started: {@link KatachiProcessors} is made the way the extension makes
 * it, by the {@link ObjectFactory} of a project {@link ProjectBuilder} builds, and the registry
 * it produces is what the code generator reads. Whether the generated file then compiles is
 * {@code KatachiPluginFunctionalTest}'s question, whether a {@code build.gradle} reaches the
 * same blocks is {@code KatachiGroovyDslFunctionalTest}'s, and whether the registered class
 * actually exists is a sample run's.
 */
class KatachiProcessorsTest {

    /** One project for the whole class: nothing here writes to it, and building one is slow. */
    private static final ObjectFactory OBJECTS = ProjectBuilder.builder().build().getObjects();

    /** Where the default documentation processor lives, as the generated source has to spell it. */
    private static final String GENERATE_DOCUMENTATION = "me.tbsten.katachi.docs.GenerateDocumentation";
    private static final String DIFF_REPORT = "me.tbsten.katachi.diff.DiffReport";

    /** Where the default template processor lives, as the generated source has to spell it. */
    private static final String GENERATE_CODE_FROM_TEMPLATE =
            "me.tbsten.katachi.template.GenerateCodeFromTemplate";

    /** Where the default template describer lives, as the generated source has to spell it. */
    private static final String DESCRIBE_TEMPLATES = "me.tbsten.katachi.template.DescribeTemplates";

    /** Where the default definition describer lives, as the generated source has to spell it. */
    private static final String DESCRIBE_DEFINITION = "me.tbsten.katachi.ide.internal.DescribeDefinition";

    @Test
    @DisplayName("何も register しなくても docs が引ける")
    void docsIsRegisteredByDefault() {
        Map<String, String> registrations = registrations(newProcessors());

        assertEquals(GENERATE_DOCUMENTATION, registrations.get("docs"));
    }

    @Test
    @DisplayName("何も register しなくても template が引ける")
    void templateIsRegisteredByDefault() {
        Map<String, String> registrations = registrations(newProcessors());

        assertEquals(GENERATE_CODE_FROM_TEMPLATE, registrations.get("template"));
    }

    @Test
    @DisplayName("何も register しなくても templates が引ける")
    void templatesIsRegisteredByDefault() {
        Map<String, String> registrations = registrations(newProcessors());

        assertEquals(DESCRIBE_TEMPLATES, registrations.get("templates"));
    }

    @Test
    @DisplayName("何も register しなくても internalTemplatesJson が DescribeTemplates で引ける")
    void internalTemplatesJsonIsRegisteredByDefault() {
        Map<String, String> registrations = registrations(newProcessors());

        assertEquals(DESCRIBE_TEMPLATES, registrations.get("internalTemplatesJson"));
    }

    @Test
    @DisplayName("internalTemplatesJson には既定で format=json と output が渡る")
    void internalTemplatesJsonHasDefaultArgs() {
        Map<String, String> args = configuredArgs(newProcessors()).get("internalTemplatesJson");

        Map<String, String> expected = new HashMap<>();
        expected.put("format", "json");
        expected.put("output", "build/katachi/internalTemplatesJson/templateDescription.json");
        assertEquals(expected, args);
    }

    @Test
    @DisplayName("何も register しなくても internalDefinitionJson が DescribeDefinition で引ける")
    void internalDefinitionJsonIsRegisteredByDefault() {
        Map<String, String> registrations = registrations(newProcessors());

        assertEquals(DESCRIBE_DEFINITION, registrations.get("internalDefinitionJson"));
    }

    @Test
    @DisplayName("internalDefinitionJson には既定で output だけが渡る")
    void internalDefinitionJsonHasDefaultArgs() {
        Map<String, String> args = configuredArgs(newProcessors()).get("internalDefinitionJson");

        Map<String, String> expected = new HashMap<>();
        expected.put("output", "build/katachi/internalDefinitionJson/definition.json");
        assertEquals(expected, args);
    }

    @Test
    @DisplayName("args(\"internalTemplatesJson\") { } は既定の引数に名前ごとに重なり、利用者が勝つ")
    void userArgsWinOverTheDefaultArgsByName() {
        KatachiProcessors processors = newProcessors();
        processors.args("internalTemplatesJson", args -> args.arg("output", "build/elsewhere.json"));

        Map<String, String> args = configuredArgs(processors).get("internalTemplatesJson");

        assertEquals("json", args.get("format"), "the default the user did not touch was lost");
        assertEquals("build/elsewhere.json", args.get("output"), "the default beat the user's own value");
    }

    @Test
    @DisplayName("internalTemplatesJson を別のクラスで register し直すと、既定の引数は付かない")
    void aReplacedInternalKeyGetsNoDefaultArgs() {
        KatachiProcessors processors = newProcessors();
        processors.register("internalTemplatesJson", "com.example.processors.OurOwnJson");

        assertFalse(
                configuredArgs(processors).containsKey("internalTemplatesJson"),
                "katachi's arguments were handed to a processor that is not katachi's");
    }

    @Test
    @DisplayName("既定で登録されるのは docs と diff と template と templates と internalTemplatesJson と internalDefinitionJson だけ -- layout も konsist も入らない")
    void docsAndTemplateAreTheOnlyDefaults() {
        Map<String, String> registrations = registrations(newProcessors());

        // `konsist` would point into :katachi-konsist, which a module that does not depend on
        // it cannot compile a `::class.java` literal against. `layout` is left out with it so
        // that the rule stays "only what :katachi itself carries".
        Map<String, String> expected = new HashMap<>();
        expected.put("docs", GENERATE_DOCUMENTATION);
        expected.put("diff", DIFF_REPORT);
        expected.put("template", GENERATE_CODE_FROM_TEMPLATE);
        expected.put("templates", DESCRIBE_TEMPLATES);
        expected.put("internalTemplatesJson", DESCRIBE_TEMPLATES);
        expected.put("internalDefinitionJson", DESCRIBE_DEFINITION);
        assertEquals(
                expected,
                registrations,
                "only processors living in :katachi itself may be defaulted");
    }

    @Test
    @DisplayName("同じキーを register すると利用者の登録が勝つ")
    void aUserRegistrationOverridesADefault() {
        KatachiProcessors processors = newProcessors();
        processors.register("docs", "com.example.processors.OurOwnDocs");

        assertEquals("com.example.processors.OurOwnDocs", registrations(processors).get("docs"));
    }

    @Test
    @DisplayName("既定のキーを register しても「登録済み」で落ちない")
    void registeringADefaultKeyIsNotADuplicate() {
        KatachiProcessors processors = newProcessors();

        // Without this, swapping in a documentation processor of one's own would be impossible
        // without giving it a second name.
        processors.register("docs", "com.example.processors.OurOwnDocs");

        // Still the six defaults, with `docs` now pointing at the user's own class.
        assertEquals(6, registrations(processors).size());
    }

    @Test
    @DisplayName("同じキーを2回 register すると、これまでどおり落ちる")
    void registeringTheSameKeyTwiceStillFails() {
        KatachiProcessors processors = newProcessors();
        processors.register("docs", "com.example.processors.OurOwnDocs");

        InvalidUserDataException failure = assertThrows(
                InvalidUserDataException.class,
                () -> processors.register("docs", "com.example.processors.YetAnother"));

        assertTrue(
                failure.getMessage().contains("com.example.processors.OurOwnDocs"),
                "expected the message to name what the key already points at: " + failure.getMessage());
    }

    @Test
    @DisplayName("利用者の登録は既定に足される")
    void userRegistrationsAreAddedToTheDefaults() {
        KatachiProcessors processors = newProcessors();
        processors.register("layout", "me.tbsten.katachi.check.LayoutCheck");

        Map<String, String> registrations = registrations(processors);

        assertEquals(GENERATE_DOCUMENTATION, registrations.get("docs"));
        assertEquals("me.tbsten.katachi.check.LayoutCheck", registrations.get("layout"));
    }

    @Test
    @DisplayName("template { } と args(\"template\") { } の併用は二重設定として落ちる")
    void aTypedBlockStillCollidesWithTheStringForm() {
        KatachiProcessors processors = newProcessors();
        processors.template(template -> template.setTemplate(java.util.List.of("UseCase")));
        processors.args("template", args -> args.arg("template", "UseCase"));

        InvalidUserDataException failure =
                assertThrows(InvalidUserDataException.class, () -> configuredArgs(processors));

        assertTrue(
                failure.getMessage().contains("template { }"),
                "expected the message to name the typed block: " + failure.getMessage());
    }

    @Test
    @DisplayName("template { onExisting = SKIP } は --arg onExisting=skip になる")
    void onExistingTravelsAsItsWireName() {
        KatachiProcessors processors = newProcessors();
        processors.template(template -> template.setOnExisting(KatachiOnExisting.SKIP));

        assertEquals("skip", configuredArgs(processors).get("template").get("onExisting"));
    }

    @Test
    @DisplayName("template { template = listOf(a, b) } は --arg template=a,b になる")
    void templateListTravelsAsACommaJoinedArg() {
        KatachiProcessors processors = newProcessors();
        processors.template(template -> template.setTemplate(
                java.util.List.of("data.Repository.repository", "data.Repository.repositoryImpl")));

        assertEquals(
                "data.Repository.repository,data.Repository.repositoryImpl",
                configuredArgs(processors).get("template").get("template"));
    }

    @Test
    @DisplayName("template { template = ... } に不正な指定を渡すと落ちる")
    void anInvalidTemplateSpecifierFails() {
        KatachiProcessors processors = newProcessors();

        InvalidUserDataException failure = assertThrows(
                InvalidUserDataException.class,
                () -> processors.template(template -> template.setTemplate(java.util.List.of("Use Case"))));

        assertTrue(
                failure.getMessage().contains("Use Case"),
                "expected the message to name the offending entry: " + failure.getMessage());
    }

    @Test
    @DisplayName("template { template = emptyList() } は落ちる")
    void anEmptyTemplateListFails() {
        KatachiProcessors processors = newProcessors();

        assertThrows(
                InvalidUserDataException.class,
                () -> processors.template(template -> template.setTemplate(java.util.List.of())));
    }

    @Test
    @DisplayName("diff { } に書いた値はそれぞれの --arg になり、書かなかった値は送らない")
    void diffOptionsTravelAsTheirArgs() {
        KatachiProcessors processors = newProcessors();
        processors.diff(diff -> {
            diff.setBefore("origin/main");
            diff.setWeights(java.util.List.of("Domain=2", "Presentation.ViewModel=0.5"));
            diff.setMaxFiles(50);
            diff.markdown(markdown -> markdown.setOutputFile("diff-report.md"));
            diff.html(html -> html.setEnable(false));
        });

        java.util.Map<String, String> expected = new java.util.LinkedHashMap<>();
        expected.put("before", "origin/main");
        expected.put("weights", "Domain=2,Presentation.ViewModel=0.5");
        expected.put("maxFiles", "50");
        expected.put("markdownOutputFile", "diff-report.md");
        expected.put("html", "false");
        assertEquals(expected, configuredArgs(processors).get("diff"));
    }

    @Test
    @DisplayName("diff { } を書かなければ diff の引数は何も送らない")
    void anUntouchedDiffBlockSendsNothing() {
        assertEquals(null, configuredArgs(newProcessors()).get("diff"));
    }

    @Test
    @DisplayName("diff { } と args(\"diff\") { } の併用は二重設定として落ちる")
    void theDiffBlockCollidesWithTheStringForm() {
        KatachiProcessors processors = newProcessors();
        processors.diff(diff -> diff.setMaxFiles(10));
        processors.args("diff", args -> args.arg("maxFiles", "20"));

        InvalidUserDataException failure =
                assertThrows(InvalidUserDataException.class, () -> configuredArgs(processors));

        assertTrue(
                failure.getMessage().contains("diff { }"),
                "expected the message to name the typed block: " + failure.getMessage());
    }

    @Test
    @DisplayName("diff { weights = ... } に = の無い項目やカンマ入りの項目を渡すと落ちる")
    void anInvalidWeightEntryFails() {
        KatachiProcessors processors = newProcessors();

        InvalidUserDataException failure = assertThrows(
                InvalidUserDataException.class,
                () -> processors.diff(diff -> diff.setWeights(java.util.List.of("Domain"))));
        assertTrue(failure.getMessage().contains("\"Domain\""), failure.getMessage());
        assertThrows(
                InvalidUserDataException.class,
                () -> processors.diff(diff -> diff.setWeights(java.util.List.of("Domain=2,Data=3"))));
    }

    @Test
    @DisplayName("diff { markdown { outputFile = ... } } が .. でモジュールの外に出ると落ちる")
    void aDiffOutputFileClimbingOutFails() {
        KatachiProcessors processors = newProcessors();

        assertThrows(
                InvalidUserDataException.class,
                () -> processors.diff(diff -> diff.markdown(markdown -> markdown.setOutputFile("../diff-report.md"))));
    }

    @Test
    @DisplayName("diff { json { } yaml { } } に書いた値は json / jsonOutputFile / yaml / yamlOutputFile の --arg になる")
    void diffJsonAndYamlTravelAsTheirArgs() {
        KatachiProcessors processors = newProcessors();
        processors.diff(diff -> {
            diff.json(json -> json.setOutputFile("reports/diff-report.json"));
            diff.yaml(yaml -> yaml.setEnable(false));
        });

        java.util.Map<String, String> expected = new java.util.LinkedHashMap<>();
        expected.put("jsonOutputFile", "reports/diff-report.json");
        expected.put("yaml", "false");
        assertEquals(expected, configuredArgs(processors).get("diff"));
    }

    @Test
    @DisplayName("diff { yaml { outputFile = ... } } が .. でモジュールの外に出ると、.yaml の書き方を言って落ちる")
    void aYamlOutputFileClimbingOutNamesItsExtension() {
        KatachiProcessors processors = newProcessors();

        InvalidUserDataException failure = assertThrows(
                InvalidUserDataException.class,
                () -> processors.diff(diff -> diff.yaml(yaml -> yaml.setOutputFile("../diff-report.yaml"))));
        assertTrue(failure.getMessage().contains("file(\"diff-report.yaml\")"), failure.getMessage());
    }

    @Test
    @DisplayName("diff { html { outputFile = File } } は絶対パスで送る")
    void aDiffOutputFileGivenAsAFileTravelsAbsolute() {
        KatachiProcessors processors = newProcessors();
        java.io.File file = new java.io.File("reports/diff.html");
        processors.diff(diff -> diff.html(html -> html.setOutputFile(file)));

        assertEquals(file.getAbsolutePath(), configuredArgs(processors).get("diff").get("htmlOutputFile"));
    }

    @Test
    @DisplayName("outputs(...) を同じキーに2回書くと落ちる")
    void declaringOutputsTwiceFails() {
        KatachiProcessors processors = newProcessors();
        processors.outputs("docs", outputs -> outputs.setReadsProjectFiles(true));

        InvalidUserDataException failure = assertThrows(
                InvalidUserDataException.class,
                () -> processors.outputs("docs", outputs -> outputs.setReadsProjectFiles(false)));

        assertTrue(
                failure.getMessage().contains("\"docs\""),
                "expected the message to name the key: " + failure.getMessage());
    }

    @Test
    @DisplayName("outputs(...) の outputDir が .. でモジュールの外に出ると落ちる")
    void anOutputDirClimbingOutFails() {
        KatachiProcessors processors = newProcessors();

        InvalidUserDataException failure = assertThrows(
                InvalidUserDataException.class,
                () -> processors.outputs("roleNames", outputs -> outputs.setOutputDir("../elsewhere")));

        assertTrue(
                failure.getMessage().contains("\"..\""),
                "expected the message to point at the \"..\": " + failure.getMessage());
    }

    @Test
    @DisplayName("outputs(...) の readsProjectFiles に null を渡すと落ちる")
    void aNullReadsProjectFilesFails() {
        KatachiProcessors processors = newProcessors();

        assertThrows(
                InvalidUserDataException.class,
                () -> processors.outputs("roleNames", outputs -> outputs.setReadsProjectFiles(null)));
    }

    @Test
    @DisplayName("outputs(...) のキーが不正なら落ちる")
    void anInvalidOutputsKeyFails() {
        KatachiProcessors processors = newProcessors();

        assertThrows(
                InvalidUserDataException.class,
                () -> processors.outputs("role names", outputs -> outputs.setReadsProjectFiles(false)));
    }

    @Test
    @DisplayName("katachi の docs はプロジェクトを読むと宣言され、katachiDocs は毎回走る")
    void docsIsDeclaredAsReadingTheProject() {
        KatachiProcessors processors = newProcessors();

        assertEquals(
                Boolean.TRUE,
                resolvedOutputs(processors, "docs").getReadsProjectFiles(),
                "GenerateDocumentation lists the project's files to find an example file for a role "
                        + "with a ktFileContent { } and no example(...); declared as reading nothing, "
                        + "katachiDocs keeps a page naming a file that is gone (KATACHI-421).");
    }

    @Test
    @DisplayName("outputs(\"docs\") { readsProjectFiles = false } だけで、docs の出力先と mode=check の宣言がそろう")
    void docsDeclaredNotToReadKeepsItsOutputDeclaration() {
        KatachiProcessors processors = newProcessors();
        processors.outputs("docs", outputs -> outputs.setReadsProjectFiles(false));

        KatachiProcessorOutputs docs = resolvedOutputs(processors, "docs");

        assertEquals(Boolean.FALSE, docs.getReadsProjectFiles());
        assertEquals("build/katachi/docs", docs.getOutputDir());
        assertEquals("outputDir", docs.getOutputDirArg());
        assertEquals(Collections.singletonMap("mode", "check"), writesNothingWhen(docs));
    }

    @Test
    @DisplayName("katachi 自身の processor のうち、プロジェクトを読まないと宣言するのは internalTemplatesJson だけ")
    void internalTemplatesJsonIsTheOnlyDefaultDeclaredNotToRead() {
        KatachiProcessors processors = newProcessors();

        List<String> declaredNotToRead = new ArrayList<>();
        for (String key : registrations(processors).keySet()) {
            if (Boolean.FALSE.equals(resolvedOutputs(processors, key).getReadsProjectFiles())) {
                declaredNotToRead.add(key);
            }
        }

        assertEquals(
                Collections.singletonList("internalTemplatesJson"),
                declaredNotToRead,
                "Gradle skips the task of a processor declared as reading no project file, so the "
                        + "declaration has to be true. Before changing which of katachi's own "
                        + "processors make it, add each one, with the arguments its task runs it with, "
                        + "to katachi/src/test/kotlin/me/tbsten/katachi/test/processor/"
                        + "ReadsNoProjectFilesSpec.kt, which runs it over a file system that records "
                        + "every touch.");
    }

    @Test
    @DisplayName("誤りの JSON を書く場所は、既定の internalDefinitionJson にだけ答える")
    void definitionErrorsPathIsOnlyForTheDefaultDefinitionTask() {
        KatachiProcessors processors = newProcessors();

        assertEquals(
                "build/katachi/internalDefinitionJson/definition-errors.json",
                definitionErrorsPathOf(processors, "internalDefinitionJson"));
        assertEquals(null, definitionErrorsPathOf(processors, "internalTemplatesJson"));
        assertEquals(null, definitionErrorsPathOf(processors, "templates"));
        assertEquals(null, definitionErrorsPathOf(processors, "docs"));
    }

    @Test
    @DisplayName("internalDefinitionJson を利用者のクラスで register し直すと、誤りの JSON の場所は答えない")
    void aReplacedDefinitionTaskGetsNoDefinitionErrorsPath() {
        KatachiProcessors processors = newProcessors();
        processors.register("internalDefinitionJson", "com.example.processors.OurOwnJson");

        assertEquals(null, definitionErrorsPathOf(processors, "internalDefinitionJson"));
    }

    /** A fresh {@code processors { }}, as {@code KatachiExtension} gets it from Gradle. */
    private static KatachiProcessors newProcessors() {
        return OBJECTS.newInstance(KatachiProcessors.class);
    }

    /** Calls the package-private {@code KatachiProcessors.resolvedOutputs(String)}. */
    private static KatachiProcessorOutputs resolvedOutputs(KatachiProcessors processors, String key) {
        try {
            Method method = KatachiProcessors.class.getDeclaredMethod("resolvedOutputs", String.class);
            method.setAccessible(true);
            return (KatachiProcessorOutputs) method.invoke(processors, key);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }

    /** Calls the package-private {@code KatachiProcessorOutputs.getWritesNothingWhen()}. */
    @SuppressWarnings("unchecked")
    private static Map<String, String> writesNothingWhen(KatachiProcessorOutputs outputs) {
        try {
            Method method = KatachiProcessorOutputs.class.getDeclaredMethod("getWritesNothingWhen");
            method.setAccessible(true);
            return (Map<String, String>) method.invoke(outputs);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }

    /** Calls the package-private {@code KatachiProcessors.definitionErrorsPathOf(String)}. */
    private static String definitionErrorsPathOf(KatachiProcessors processors, String key) {
        try {
            Method method = KatachiProcessors.class.getDeclaredMethod("definitionErrorsPathOf", String.class);
            method.setAccessible(true);
            return (String) method.invoke(processors, key);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }

    /**
     * Calls the package-private {@code KatachiProcessors.getConfiguredArgs()}.
     *
     * <p>Reflection for the reason {@code KatachiEntryPointSourceTest} uses it: this test sits in
     * {@code me.tbsten.katachi.test.gradle} like every other test of this module, so it sees the
     * plugin under the same visibility a user's build does. Opening the method up for the test
     * would change what the Gradle DSL exposes.
     */
    @SuppressWarnings("unchecked")
    private static Map<String, Map<String, String>> configuredArgs(KatachiProcessors processors) {
        return (Map<String, Map<String, String>>) invoke(processors, "getConfiguredArgs");
    }

    /**
     * Calls the package-private {@code KatachiProcessors.getRegistrations()}, for the same reason
     * {@link #configuredArgs} does.
     */
    @SuppressWarnings("unchecked")
    private static Map<String, String> registrations(KatachiProcessors processors) {
        return (Map<String, String>) invoke(processors, "getRegistrations");
    }

    private static Object invoke(KatachiProcessors processors, String methodName) {
        try {
            Method method = KatachiProcessors.class.getDeclaredMethod(methodName);
            method.setAccessible(true);
            return method.invoke(processors);
        } catch (InvocationTargetException e) {
            if (e.getCause() instanceof RuntimeException) {
                throw (RuntimeException) e.getCause();
            }
            throw new IllegalStateException(e.getCause());
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }

    @Test
    @DisplayName("返る Map を書き換えても、次に引いたときに残らない")
    void theReturnedMapIsACopy() {
        KatachiProcessors processors = newProcessors();

        registrations(processors).put("docs", "com.example.Sneaky");

        assertEquals(GENERATE_DOCUMENTATION, registrations(processors).get("docs"));
    }
}
