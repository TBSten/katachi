package me.tbsten.katachi.test.gradle;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.gradle.testkit.runner.BuildResult;
import org.gradle.testkit.runner.BuildTask;
import org.gradle.testkit.runner.GradleRunner;
import org.gradle.testkit.runner.TaskOutcome;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * Drives a real Gradle build that applies the plugin, on every Gradle version katachi claims
 * to support.
 *
 * <p>The version matrix is the point of this class. "Gradle 8.0 through the latest" is a claim
 * nothing else in the repository can check: the plugin compiles against the Gradle API of
 * whatever version builds it, so a method that exists only in 9.x compiles and runs here while
 * failing with {@code NoSuchMethodError} on a user's Gradle 8.0. Only a run on 8.0 catches it.
 *
 * <p>The fixture build scripts are Kotlin DSL rather than Groovy, deliberately. A user's
 * {@code build.gradle.kts} is compiled by the <em>embedded</em> Kotlin compiler of their own
 * Gradle -- 1.8.10 on Gradle 8.0 -- and that compiler has to be able to read every plugin type
 * the script names. That is the whole reason this module is written in Java, and this is the
 * test that holds the line.
 */
class KatachiPluginFunctionalTest {

    /** The plugin id, as a user writes it. */
    private static final String PLUGIN_ID = "me.tbsten.katachi";

    /** The one task the plugin registers. */
    private static final String TASK_PATH = ":runKatachiProcessor";

    /** The entry point the task starts unless a build script says otherwise. */
    private static final String KATACHI_MAIN_CLASS = "me.tbsten.katachi.processor.MainKt";

    /**
     * The Gradle versions to run every test of this class against.
     *
     * <p>Set by the {@code test} task from {@code katachi.testkit.gradleVersions}, so the
     * matrix is one place in the build script rather than a list in here. The floor and the
     * version this repository itself builds with are what it holds; the distributions are
     * downloaded on first use.
     */
    static Stream<String> gradleVersions() {
        String configured = System.getProperty("katachi.testkit.gradleVersions");
        assertNotNull(
                configured,
                "katachi.testkit.gradleVersions is not set. The `test` task of "
                        + ":katachi-gradle-plugin sets it; running this class without that task "
                        + "would silently test one Gradle version instead of the matrix.");
        return Arrays.stream(configured.split(","))
                .map(String::trim)
                .filter(version -> !version.isEmpty());
    }

    @ParameterizedTest(name = "Gradle {0}")
    @MethodSource("gradleVersions")
    @DisplayName("plugin を適用すると runKatachiProcessor が test の runtimeClasspath つきで登録される")
    void registersTheTaskWithTheTestRuntimeClasspath(String gradleVersion, @TempDir Path projectDir)
            throws IOException {
        writeFixture(projectDir, "");
        // A task of the fixture's own, reading the registered task at configuration time and
        // printing what it found. Asking Gradle itself is the only way to see the wiring the
        // plugin did; `tasks --all` would only prove the name exists.
        appendBuildScript(
                projectDir,
                "\n"
                        + "val runKatachiProcessor = tasks.named<RunKatachiProcessorTask>(\"runKatachiProcessor\")\n"
                        + "\n"
                        + "tasks.register(\"reportKatachiWiring\") {\n"
                        + "    // Read into plain values at configuration time: a `doLast` that\n"
                        + "    // reached for the task itself would not survive the configuration cache.\n"
                        + "    val task = runKatachiProcessor.get()\n"
                        + "    val mainClass = task.mainClass.get()\n"
                        + "    val taskGroup = task.group\n"
                        + "    val ignoreExitValue = task.isIgnoreExitValue\n"
                        + "    val classpath = task.classpath.files.map { it.absolutePath }.sorted()\n"
                        + "    doLast {\n"
                        + "        println(\"katachi.mainClass=\" + mainClass)\n"
                        + "        println(\"katachi.group=\" + taskGroup)\n"
                        + "        println(\"katachi.ignoreExitValue=\" + ignoreExitValue)\n"
                        + "        classpath.forEach { println(\"katachi.classpath=\" + it) }\n"
                        + "    }\n"
                        + "}\n");

        BuildResult result = runner(projectDir, gradleVersion, "reportKatachiWiring").build();

        assertEquals(KATACHI_MAIN_CLASS, single(result, "katachi.mainClass="));
        assertEquals("katachi", single(result, "katachi.group="));
        assertEquals(
                "false",
                single(result, "katachi.ignoreExitValue="),
                "ignoreExitValue has to stay false: it is what turns a failing entry point into "
                        + "a failing task.");

        List<String> classpath = all(result, "katachi.classpath=");
        assertTrue(
                classpath.stream().anyMatch(entry -> slashed(entry).endsWith("build/classes/java/test")),
                "the test source set's compiled classes are missing from the classpath: " + classpath);
    }

    @ParameterizedTest(name = "Gradle {0}")
    @MethodSource("gradleVersions")
    @DisplayName("runKatachiProcessor が test sourceSet のクラスを実行し、テストランナーは起動しない")
    void runsAnEntryPointFromTheTestSourceSetWithoutStartingATestRunner(
            String gradleVersion, @TempDir Path projectDir) throws IOException {
        writeFixture(projectDir, "Skeleton");
        writeTestJavaClass(
                projectDir,
                "Skeleton",
                "        java.io.File out = new java.io.File(\"build/katachi/WalkingSkeleton/skeleton.txt\");\n"
                        + "        out.getParentFile().mkdirs();\n"
                        + "        java.nio.file.Files.write(out.toPath(), \"walking skeleton\".getBytes(\"UTF-8\"));\n");

        BuildResult result =
                runner(projectDir, gradleVersion, "runKatachiProcessor", "--processor=skeleton").build();

        assertEquals(TaskOutcome.SUCCESS, outcomeOf(result));
        assertTrue(
                Files.exists(projectDir.resolve("build/katachi/WalkingSkeleton/skeleton.txt")),
                "the entry point did not write its file; the working directory is not the project "
                        + "directory, or the class never ran");
        // A `Test` task writes this directory on every run, whether or not it found anything to
        // run. Its absence is the mechanical proof that no test engine was started -- grepping
        // the log for "kotest" or "JUnit" would rot the moment the wording changed.
        assertFalse(
                Files.exists(projectDir.resolve("build/test-results")),
                "build/test-results exists, so a test runner was started after all");
    }

    @ParameterizedTest(name = "Gradle {0}")
    @MethodSource("gradleVersions")
    @DisplayName("エントリポイントが例外を投げるとタスクが失敗する")
    void failsWhenTheEntryPointThrows(String gradleVersion, @TempDir Path projectDir) throws IOException {
        writeFixture(projectDir, "Boom");
        writeTestJavaClass(
                projectDir, "Boom", "        throw new IllegalStateException(\"boom from the fixture\");\n");

        BuildResult result =
                runner(projectDir, gradleVersion, "runKatachiProcessor", "--processor=skeleton")
                        .buildAndFail();

        assertEquals(TaskOutcome.FAILED, outcomeOf(result));
        assertTrue(
                result.getOutput().contains("non-zero exit value"),
                "the task failed for some other reason than the entry point's exit status:\n"
                        + result.getOutput());
    }

    @ParameterizedTest(name = "Gradle {0}")
    @MethodSource("gradleVersions")
    @DisplayName("docs には rootTitle として root project の名前が渡り、docs { } に書けばそちらが勝つ")
    void docsIsGivenTheRootProjectNameAsItsRootTitle(String gradleVersion, @TempDir Path projectDir)
            throws IOException {
        writeFixture(projectDir, "Args");
        writeTestJavaClass(
                projectDir,
                "Args",
                "        java.io.File out = new java.io.File(\"build/katachi/args.txt\");\n"
                        + "        out.getParentFile().mkdirs();\n"
                        + "        java.nio.file.Files.write(\n"
                        + "                out.toPath(), String.join(\"\\n\", args).getBytes(\"UTF-8\"));\n");

        runner(projectDir, gradleVersion, "runKatachiProcessor", "--processor=docs").build();

        // The name of the repository is a value only Gradle has, so the plugin is the only layer
        // that can send it. Without it the generated root page would be headed "アーキテクチャ".
        assertTrue(
                argsOf(projectDir).contains("--arg=rootTitle=katachi-fixture"),
                "the plugin did not send the root project's name as rootTitle:\n" + argsOf(projectDir));

        appendBuildScript(
                projectDir,
                "\nkatachi {\n"
                        + "    processors {\n"
                        + "        docs {\n"
                        + "            rootTitle = \"written-in-the-build-script\"\n"
                        + "        }\n"
                        + "    }\n"
                        + "}\n");

        runner(projectDir, gradleVersion, "runKatachiProcessor", "--processor=docs").build();

        assertTrue(
                argsOf(projectDir).contains("--arg=rootTitle=written-in-the-build-script"),
                "docs { rootTitle = ... } lost to the convention:\n" + argsOf(projectDir));
        assertFalse(
                argsOf(projectDir).contains("katachi-fixture"),
                "both the written value and the convention were sent:\n" + argsOf(projectDir));
    }

    /** What the fixture's {@code Args} entry point recorded of the command line it was given. */
    private static String argsOf(Path projectDir) throws IOException {
        Path recorded = projectDir.resolve("build/katachi/args.txt");
        assertTrue(Files.exists(recorded), "the fixture entry point never ran");
        return new String(Files.readAllBytes(recorded), StandardCharsets.UTF_8);
    }

    /**
     * Writes a fixture project applying the plugin, optionally pointing the task elsewhere.
     *
     * @param projectDir an empty directory to become the build's root.
     * @param mainClass the entry point to run, or the empty string to leave the plugin's default.
     */
    private static void writeFixture(Path projectDir, String mainClass) throws IOException {
        write(projectDir.resolve("settings.gradle.kts"), "rootProject.name = \"katachi-fixture\"\n");

        StringBuilder buildScript = new StringBuilder();
        buildScript
                .append("import me.tbsten.katachi.gradle.RunKatachiProcessorTask\n")
                .append("\n")
                .append("plugins {\n")
                // `java` rather than `kotlin(\"jvm\")`: the plugin reads its classpath through
                // `JavaPluginExtension`, which is what kotlin("jvm") applies anyway, and this
                // way the fixture resolves no dependencies and needs no repository.
                .append("    java\n")
                .append("    id(\"")
                .append(PLUGIN_ID)
                .append("\")\n")
                .append("}\n");
        if (!mainClass.isEmpty()) {
            buildScript
                    .append("\n")
                    .append("tasks.named<RunKatachiProcessorTask>(\"runKatachiProcessor\") {\n")
                    .append("    mainClass.set(\"")
                    .append(mainClass)
                    .append("\")\n")
                    .append("}\n");
        }
        // `exec()` refuses to run without an architecture and at least one processor key.
        // These two tests are about the JavaExec pipe itself -- they override `mainClass` to a
        // plain Java class in the fixture, so the generated entry point is never loaded and the
        // value here is only ever checked for being a well-formed name.
        buildScript
                .append("\n")
                .append("katachi {\n")
                .append("    architecture = \"fixture.ProjectArchitectureKt\"\n")
                .append("}\n");

        write(projectDir.resolve("build.gradle.kts"), buildScript.toString());
    }

    /** Writes a `src/test/java` class whose {@code main} runs {@code body}. */
    private static void writeTestJavaClass(Path projectDir, String name, String body) throws IOException {
        write(
                projectDir.resolve("src/test/java/" + name + ".java"),
                "public final class "
                        + name
                        + " {\n"
                        + "    public static void main(String[] args) throws Exception {\n"
                        + body
                        + "    }\n"
                        + "}\n");
    }

    private static void appendBuildScript(Path projectDir, String text) throws IOException {
        Path buildScript = projectDir.resolve("build.gradle.kts");
        String existing = new String(Files.readAllBytes(buildScript), StandardCharsets.UTF_8);
        write(buildScript, existing + text);
    }

    private static void write(Path path, String content) throws IOException {
        Files.createDirectories(path.getParent());
        Files.write(path, content.getBytes(StandardCharsets.UTF_8));
    }

    private static GradleRunner runner(
            Path projectDir, String gradleVersion, String task, String... extraArguments) {
        List<String> arguments = new ArrayList<>();
        arguments.add(task);
        arguments.add("--stacktrace");
        arguments.add("--configuration-cache");
        arguments.addAll(Arrays.asList(extraArguments));
        return GradleRunner.create()
                .withProjectDir(projectDir.toFile())
                .withGradleVersion(gradleVersion)
                // Puts this module's own classes on the fixture's buildscript classpath, which
                // is what makes `id("me.tbsten.katachi")` resolve without a repository.
                .withPluginClasspath()
                .forwardOutput()
                .withArguments(arguments);
    }

    private static TaskOutcome outcomeOf(BuildResult result) {
        BuildTask task = result.task(TASK_PATH);
        assertNotNull(task, TASK_PATH + " never entered the task graph:\n" + result.getOutput());
        return task.getOutcome();
    }

    /** Every value the fixture printed under {@code prefix}, in the order it printed them. */
    private static List<String> all(BuildResult result, String prefix) {
        return Arrays.stream(result.getOutput().split("\\R"))
                .map(String::trim)
                .filter(line -> line.startsWith(prefix))
                .map(line -> line.substring(prefix.length()))
                .collect(Collectors.toList());
    }

    /** The one value the fixture printed under {@code prefix}. */
    private static String single(BuildResult result, String prefix) {
        List<String> values = all(result, prefix);
        assertEquals(1, values.size(), "expected exactly one line starting with " + prefix + ", got " + values);
        return values.get(0);
    }

    /** {@code path} with Windows separators folded to `/`, so one assertion covers both. */
    private static String slashed(String path) {
        return path.replace('\\', '/');
    }
}
