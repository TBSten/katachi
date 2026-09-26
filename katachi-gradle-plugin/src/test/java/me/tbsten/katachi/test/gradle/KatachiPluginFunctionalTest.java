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

    /** The task of the default {@code docs} processor, the one most tests run. */
    private static final String TASK_PATH = ":katachiDocs";

    /** The entry point the task starts unless a build script says otherwise. */
    private static final String KATACHI_MAIN_CLASS = "me.tbsten.katachi.processor.internal.MainKt";

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
    @DisplayName("plugin を適用すると katachiDocs と katachiTemplate と katachiTemplates が test の runtimeClasspath つきで登録される")
    void registersTheTaskWithTheTestRuntimeClasspath(String gradleVersion, @TempDir Path projectDir)
            throws IOException {
        writeFixture(projectDir, "");
        // A task of the fixture's own, reading the registered task at configuration time and
        // printing what it found. Asking Gradle itself is the only way to see the wiring the
        // plugin did; `tasks --all` would only prove the name exists.
        appendBuildScript(
                projectDir,
                "\n"
                        + "val katachiDocs = tasks.named<KatachiProcessorTask>(\"katachiDocs\")\n"
                        + "val katachiTemplate = tasks.named<KatachiProcessorTask>(\"katachiTemplate\")\n"
                        + "val katachiTemplates = tasks.named<KatachiProcessorTask>(\"katachiTemplates\")\n"
                        + "\n"
                        + "tasks.register(\"reportKatachiWiring\") {\n"
                        + "    // Read into plain values at configuration time: a `doLast` that\n"
                        + "    // reached for the task itself would not survive the configuration cache.\n"
                        + "    val task = katachiDocs.get()\n"
                        + "    val mainClass = task.mainClass.get()\n"
                        + "    val taskGroup = task.group\n"
                        + "    val ignoreExitValue = task.isIgnoreExitValue\n"
                        + "    val classpath = task.classpath.files.map { it.absolutePath }.sorted()\n"
                        + "    val docsKey = task.processorKey.get()\n"
                        + "    val templateKey = katachiTemplate.get().processorKey.get()\n"
                        + "    val templateClasspath = katachiTemplate.get().classpath.files.size\n"
                        + "    val templatesKey = katachiTemplates.get().processorKey.get()\n"
                        + "    val templatesClasspath = katachiTemplates.get().classpath.files.size\n"
                        + "    doLast {\n"
                        + "        println(\"katachi.mainClass=\" + mainClass)\n"
                        + "        println(\"katachi.group=\" + taskGroup)\n"
                        + "        println(\"katachi.ignoreExitValue=\" + ignoreExitValue)\n"
                        + "        println(\"katachi.docsKey=\" + docsKey)\n"
                        + "        println(\"katachi.templateKey=\" + templateKey)\n"
                        + "        println(\"katachi.templateClasspath=\" + templateClasspath)\n"
                        + "        println(\"katachi.templatesKey=\" + templatesKey)\n"
                        + "        println(\"katachi.templatesClasspath=\" + templatesClasspath)\n"
                        + "        classpath.forEach { println(\"katachi.classpath=\" + it) }\n"
                        + "    }\n"
                        + "}\n");

        BuildResult result = runner(projectDir, gradleVersion, "reportKatachiWiring").build();

        assertEquals(KATACHI_MAIN_CLASS, single(result, "katachi.mainClass="));
        assertEquals("docs", single(result, "katachi.docsKey="));
        assertEquals("template", single(result, "katachi.templateKey="));
        assertFalse(
                "0".equals(single(result, "katachi.templateClasspath=")),
                "katachiTemplate was registered without the test runtime classpath");
        assertEquals("templates", single(result, "katachi.templatesKey="));
        assertFalse(
                "0".equals(single(result, "katachi.templatesClasspath=")),
                "katachiTemplates was registered without the test runtime classpath");
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
    @DisplayName("katachiDocs が test sourceSet のクラスを実行し、テストランナーは起動しない")
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
                runner(projectDir, gradleVersion, "katachiDocs").build();

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
                runner(projectDir, gradleVersion, "katachiDocs").buildAndFail();

        assertEquals(TaskOutcome.FAILED, outcomeOf(result));
        assertTrue(
                result.getOutput().contains("non-zero exit value"),
                "the task failed for some other reason than the entry point's exit status:\n"
                        + result.getOutput());
    }

    @ParameterizedTest(name = "Gradle {0}")
    @MethodSource("gradleVersions")
    @DisplayName("docs はルートページの名前に関する --arg を一切送らない")
    void docsSendsNothingAboutTheRootPageName(String gradleVersion, @TempDir Path projectDir)
            throws IOException {
        writeFixture(projectDir, "Args");
        writeTestJavaClass(
                projectDir,
                "Args",
                "        java.io.File out = new java.io.File(\"build/katachi/args.txt\");\n"
                        + "        out.getParentFile().mkdirs();\n"
                        + "        java.nio.file.Files.write(\n"
                        + "                out.toPath(), String.join(\"\\n\", args).getBytes(\"UTF-8\"));\n");

        runner(projectDir, gradleVersion, "katachiDocs").build();

        // The root page names itself from `architecture { title = ... }`. The plugin used to send
        // the root project's name as `--arg rootTitle`, which beat what the definition wrote on
        // itself -- so the one place a reader would look was the one place that lost.
        assertFalse(
                argsOf(projectDir).contains("rootTitle"),
                "the plugin still sends a rootTitle argument:\n" + argsOf(projectDir));
        assertFalse(
                argsOf(projectDir).contains("rootDescription"),
                "the plugin still sends a rootDescription argument:\n" + argsOf(projectDir));
    }

    @ParameterizedTest(name = "Gradle {0}")
    @MethodSource("gradleVersions")
    @DisplayName("各タスクは自分の processor を決め打ちで渡し、--arg と build script の既定値を添える")
    void eachTaskPassesItsOwnProcessorAndArgs(String gradleVersion, @TempDir Path projectDir)
            throws IOException {
        writeFixture(projectDir, "Args");
        writeTestJavaClass(projectDir, "Args", RECORD_ARGS);
        appendBuildScript(
                projectDir,
                "\n"
                        + "katachi {\n"
                        + "    processors {\n"
                        + "        register(\"roleNames\", \"fixture.RoleNames\") {\n"
                        + "            arg(\"prefix\", \"domain\")\n"
                        + "            arg(\"suffix\", \"Impl\")\n"
                        + "        }\n"
                        + "    }\n"
                        + "}\n");

        runner(projectDir, gradleVersion, "katachiRoleNames", "--arg", "suffix=Repository").build();

        List<String> args = Arrays.asList(argsOf(projectDir).split("\n"));
        assertTrue(args.contains("--processor=roleNames"), "the task did not pass its own key:\n" + args);
        assertEquals(
                1,
                args.stream().filter(arg -> arg.startsWith("--processor=")).count(),
                "a processor task passes exactly one processor:\n" + args);
        assertTrue(args.contains("--arg=suffix=Repository"), "--arg did not reach the entry point:\n" + args);
        assertTrue(args.contains("--arg=prefix=domain"), "the build script default was lost:\n" + args);
        assertFalse(args.contains("--arg=suffix=Impl"), "the build script beat --arg:\n" + args);
    }

    @ParameterizedTest(name = "Gradle {0}")
    @MethodSource("gradleVersions")
    @DisplayName("register したキーごとに katachi + 先頭大文字のタスクが説明付きででき、katachi グループにはそれ以外のタスクが無い")
    void registersOneTaskPerRegisteredKey(String gradleVersion, @TempDir Path projectDir)
            throws IOException {
        writeFixture(projectDir, "");
        appendBuildScript(
                projectDir,
                "\n"
                        + "katachi {\n"
                        + "    processors {\n"
                        + "        register(\"layout\", \"me.tbsten.katachi.check.LayoutCheck\")\n"
                        + "        register(\"layoutCheck\", \"fixture.LayoutCheckTwo\")\n"
                        + "    }\n"
                        + "}\n");

        BuildResult result = runner(projectDir, gradleVersion, "tasks", "--group", "katachi").build();
        String output = result.getOutput();

        // The whole group, not only the names asked for: the task that used to take the
        // processor as an option must be gone, and nothing else may have crept in.
        assertEquals(
                Arrays.asList(
                        "generateKatachiEntryPoint",
                        "katachiDocs",
                        "katachiLayout",
                        "katachiLayoutCheck",
                        "katachiTemplate",
                        "katachiTemplates"),
                tasksOfGroup(output, "Katachi tasks"),
                output);

        assertTrue(
                output.contains("katachiLayout - Runs the katachi processor me.tbsten.katachi.check.LayoutCheck"),
                "katachiLayout is missing, or its description does not name the processor:\n" + output);
        assertTrue(
                output.contains("katachiLayoutCheck - Runs the katachi processor fixture.LayoutCheckTwo"),
                "katachiLayoutCheck is missing:\n" + output);
        assertTrue(
                output.contains("katachiDocs - Runs the katachi processor me.tbsten.katachi.docs.GenerateDocumentation"),
                "katachiDocs is missing:\n" + output);
        assertTrue(
                output.contains(
                        "katachiTemplate - Runs the katachi processor "
                                + "me.tbsten.katachi.template.GenerateCodeFromTemplate"),
                "katachiTemplate is missing:\n" + output);
        assertTrue(
                output.contains(
                        "katachiTemplates - Runs the katachi processor "
                                + "me.tbsten.katachi.template.DescribeTemplates"),
                "katachiTemplates is missing:\n" + output);
    }

    @ParameterizedTest(name = "Gradle {0}")
    @MethodSource("gradleVersions")
    @DisplayName("docs を register し直すとタスクは増えず、説明がその processor を指す")
    void replacingADefaultKeyKeepsOneTask(String gradleVersion, @TempDir Path projectDir)
            throws IOException {
        writeFixture(projectDir, "");
        appendBuildScript(
                projectDir,
                "\n"
                        + "katachi {\n"
                        + "    processors {\n"
                        + "        register(\"docs\", \"fixture.OurOwnDocs\")\n"
                        + "    }\n"
                        + "}\n");

        String output = runner(projectDir, gradleVersion, "tasks", "--all").build().getOutput();

        assertTrue(
                output.contains("katachiDocs - Runs the katachi processor fixture.OurOwnDocs"),
                "katachiDocs does not name the replacing processor:\n" + output);
    }

    @ParameterizedTest(name = "Gradle {0}")
    @MethodSource("gradleVersions")
    @DisplayName("既存のタスクと名前がぶつかるキーは、どのタスクとぶつかったかを言って止まる")
    void refusesAKeyWhoseTaskNameIsTaken(String gradleVersion, @TempDir Path projectDir)
            throws IOException {
        writeFixture(projectDir, "");
        appendBuildScript(
                projectDir,
                "\n"
                        + "tasks.register(\"katachiLayout\")\n"
                        + "\n"
                        + "katachi {\n"
                        + "    processors {\n"
                        + "        register(\"layout\", \"me.tbsten.katachi.check.LayoutCheck\")\n"
                        + "    }\n"
                        + "}\n");

        String output = runner(projectDir, gradleVersion, "tasks").buildAndFail().getOutput();

        assertTrue(
                output.contains("katachi processor key \"layout\" would register the task \"katachiLayout\"")
                        && output.contains("this project already has a task named \"katachiLayout\"")
                        && output.contains("under a different key"),
                "the failure does not say which key hit which task, or how to fix it:\n" + output);
    }

    @ParameterizedTest(name = "Gradle {0}")
    @MethodSource("gradleVersions")
    @DisplayName("大文字小文字だけ違う2つのキーは、先に登録したキーの名前を出して止まる")
    void refusesTwoKeysThatShareATaskName(String gradleVersion, @TempDir Path projectDir)
            throws IOException {
        writeFixture(projectDir, "");
        appendBuildScript(
                projectDir,
                "\n"
                        + "katachi {\n"
                        + "    processors {\n"
                        + "        register(\"layout\", \"me.tbsten.katachi.check.LayoutCheck\")\n"
                        + "        register(\"Layout\", \"fixture.OtherLayout\")\n"
                        + "    }\n"
                        + "}\n");

        String output = runner(projectDir, gradleVersion, "tasks").buildAndFail().getOutput();

        assertTrue(
                output.contains("the katachi processor key \"layout\" already registered the task \"katachiLayout\""),
                "the failure does not name the key that took the task first:\n" + output);
    }

    /** A fixture entry point body that records its command line to {@code build/katachi/args.txt}. */
    private static final String RECORD_ARGS =
            "        java.io.File out = new java.io.File(\"build/katachi/args.txt\");\n"
                    + "        out.getParentFile().mkdirs();\n"
                    + "        java.nio.file.Files.write(\n"
                    + "                out.toPath(), String.join(\"\\n\", args).getBytes(\"UTF-8\"));\n";

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
                .append("import me.tbsten.katachi.gradle.KatachiProcessorTask\n")
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
                    .append("tasks.withType<KatachiProcessorTask>().configureEach {\n")
                    .append("    mainClass.set(\"")
                    .append(mainClass)
                    .append("\")\n")
                    .append("}\n");
        }
        // `exec()` refuses to run without an architecture.
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

    /** The task names {@code ./gradlew tasks} lists under {@code header}, in the order listed. */
    private static List<String> tasksOfGroup(String output, String header) {
        List<String> lines = Arrays.asList(output.split("\\R"));
        int start = lines.indexOf(header);
        assertTrue(start >= 0, "no \"" + header + "\" section in:\n" + output);
        List<String> names = new ArrayList<>();
        // The header, its underline, then one "name - description" line per task up to a blank.
        for (String line : lines.subList(start + 2, lines.size())) {
            if (line.trim().isEmpty()) {
                break;
            }
            names.add(line.split(" ", 2)[0]);
        }
        return names;
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
