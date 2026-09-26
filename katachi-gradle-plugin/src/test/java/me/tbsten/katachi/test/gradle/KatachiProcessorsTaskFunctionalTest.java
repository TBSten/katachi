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
 * {@code katachiProcessors}: several processors in one JVM, each given only its own build-script
 * arguments.
 *
 * <p>The fixture's entry point records the command line of every JVM it is started in, one run
 * per block in {@code build/runs.txt}, so a test can count the JVMs and read what each was given.
 */
class KatachiProcessorsTaskFunctionalTest {

    private static final String PLUGIN_ID = "me.tbsten.katachi";

    private static final String TASK = ":katachiProcessors";

    static Stream<String> gradleVersions() {
        return KatachiPluginFunctionalTest.gradleVersions();
    }

    @ParameterizedTest(name = "Gradle {0}")
    @MethodSource("gradleVersions")
    @DisplayName("--processor で選んだ processor を1つの JVM に全部渡す")
    void selectedProcessorsShareOneJvm(String gradleVersion, @TempDir Path projectDir) throws IOException {
        writeFixture(projectDir, "register(\"layout\", \"fixture.Layout\")\n");

        run(projectDir, gradleVersion, TASK, "--processor", "layout", "--processor", "docs");

        List<List<String>> runs = runs(projectDir);
        assertEquals(1, runs.size(), "expected one JVM, got " + runs);
        assertTrue(runs.get(0).contains("--processor=layout"), runs.toString());
        assertTrue(runs.get(0).contains("--processor=docs"), runs.toString());
        assertTrue(runs.get(0).get(0).startsWith("--entry-point="), runs.toString());
    }

    @ParameterizedTest(name = "Gradle {0}")
    @MethodSource("gradleVersions")
    @DisplayName("--processor a,b のカンマ区切りも同じく1つの JVM に渡り、重複は1つにまとまる")
    void commaSeparatedKeysAreAccepted(String gradleVersion, @TempDir Path projectDir) throws IOException {
        writeFixture(projectDir, "register(\"layout\", \"fixture.Layout\")\n");

        run(projectDir, gradleVersion, TASK, "--processor", "layout,docs", "--processor", "layout");

        List<String> run = runs(projectDir).get(0);
        assertEquals(1, run.stream().filter("--processor=layout"::equals).count(), run.toString());
        assertTrue(run.contains("--processor=docs"), run.toString());
    }

    @ParameterizedTest(name = "Gradle {0}")
    @MethodSource("gradleVersions")
    @DisplayName("build script の引数は --arg-for でその processor にだけ渡り、--arg は全体に渡る")
    void buildScriptArgumentsReachTheirOwnProcessorOnly(String gradleVersion, @TempDir Path projectDir)
            throws IOException {
        writeFixture(
                projectDir,
                "register(\"layout\", \"fixture.Layout\")\n"
                        + "docs { outputDir = \"docs/architecture\" }\n");

        run(projectDir, gradleVersion, TASK, "--processor", "layout", "--processor", "docs", "--arg", "note=x");

        List<String> run = runs(projectDir).get(0);
        assertTrue(run.contains("--arg-for=docs:outputDir=docs/architecture"), run.toString());
        assertFalse(run.contains("--arg=outputDir=docs/architecture"), run.toString());
        assertTrue(run.contains("--arg=note=x"), run.toString());
    }

    @ParameterizedTest(name = "Gradle {0}")
    @MethodSource("gradleVersions")
    @DisplayName("選ばれていない processor の build script の引数は送らない")
    void argumentsOfUnselectedProcessorsAreNotSent(String gradleVersion, @TempDir Path projectDir)
            throws IOException {
        writeFixture(
                projectDir,
                "register(\"layout\", \"fixture.Layout\")\n"
                        + "docs { outputDir = \"docs/architecture\" }\n");

        run(projectDir, gradleVersion, TASK, "--processor", "layout");

        List<String> run = runs(projectDir).get(0);
        assertFalse(run.stream().anyMatch(arg -> arg.startsWith("--arg-for=")), run.toString());
    }

    @ParameterizedTest(name = "Gradle {0}")
    @MethodSource("gradleVersions")
    @DisplayName("build script の processorKeys が既定になり、--processor はそれを置き換える")
    void processorKeysFromTheBuildScript(String gradleVersion, @TempDir Path projectDir) throws IOException {
        writeFixture(projectDir, "register(\"layout\", \"fixture.Layout\")\n");
        appendBuildScript(
                projectDir,
                "tasks.named<me.tbsten.katachi.gradle.KatachiProcessorsTask>(\"katachiProcessors\") {\n"
                        + "    processorKeys = listOf(\"layout\", \"docs\")\n"
                        + "}\n");

        run(projectDir, gradleVersion, TASK);
        List<String> fromBuildScript = runs(projectDir).get(0);
        assertTrue(fromBuildScript.contains("--processor=layout"), fromBuildScript.toString());
        assertTrue(fromBuildScript.contains("--processor=docs"), fromBuildScript.toString());

        Files.delete(projectDir.resolve("build/runs.txt"));
        run(projectDir, gradleVersion, TASK, "--processor", "docs");
        List<String> fromCommandLine = runs(projectDir).get(0);
        assertFalse(fromCommandLine.contains("--processor=layout"), fromCommandLine.toString());
        assertTrue(fromCommandLine.contains("--processor=docs"), fromCommandLine.toString());
    }

    @ParameterizedTest(name = "Gradle {0}")
    @MethodSource("gradleVersions")
    @DisplayName("出力を持たないので毎回実行され、UP-TO-DATE にならない")
    void runsEveryTime(String gradleVersion, @TempDir Path projectDir) throws IOException {
        writeFixture(projectDir, "register(\"layout\", \"fixture.Layout\")\n");

        run(projectDir, gradleVersion, TASK, "--processor", "docs");
        BuildResult second = run(projectDir, gradleVersion, TASK, "--processor", "docs");

        assertEquals(TaskOutcome.SUCCESS, outcome(second, TASK));
        assertEquals(2, runs(projectDir).size());
    }

    @ParameterizedTest(name = "Gradle {0}")
    @MethodSource("gradleVersions")
    @DisplayName("processor を1つも選ばなければ JVM を起動する前に落ちる")
    void failsWithoutAnyProcessor(String gradleVersion, @TempDir Path projectDir) throws IOException {
        writeFixture(projectDir, "");

        BuildResult result = runAndFail(projectDir, gradleVersion, TASK);

        assertTrue(result.getOutput().contains("--processor"), result.getOutput());
        assertFalse(Files.exists(projectDir.resolve("build/runs.txt")), "a JVM was started");
    }

    @ParameterizedTest(name = "Gradle {0}")
    @MethodSource("gradleVersions")
    @DisplayName("登録されていないキーは登録済みキーの一覧つきで、JVM を起動する前に落ちる")
    void failsOnAnUnregisteredKey(String gradleVersion, @TempDir Path projectDir) throws IOException {
        writeFixture(projectDir, "");

        BuildResult result = runAndFail(projectDir, gradleVersion, TASK, "--processor", "layuot");

        assertTrue(result.getOutput().contains("\"layuot\""), result.getOutput());
        assertTrue(result.getOutput().contains("docs, template, templates"), result.getOutput());
        assertFalse(Files.exists(projectDir.resolve("build/runs.txt")), "a JVM was started");
    }

    @ParameterizedTest(name = "Gradle {0}")
    @MethodSource("gradleVersions")
    @DisplayName("キー processors は katachiProcessors と名前がぶつかるので登録時に落ちる")
    void theKeyProcessorsIsRefused(String gradleVersion, @TempDir Path projectDir) throws IOException {
        writeFixture(projectDir, "register(\"processors\", \"fixture.Layout\")\n");

        BuildResult result = runAndFail(projectDir, gradleVersion, "tasks");

        assertTrue(result.getOutput().contains("katachiProcessors"), result.getOutput());
        assertTrue(result.getOutput().contains("processorsCheck"), result.getOutput());
    }

    /**
     * Writes a fixture project whose processor tasks start the fixture's own recording entry
     * point.
     *
     * @param processors the body of {@code katachi { processors { } } }, or the empty string.
     */
    private static void writeFixture(Path projectDir, String processors) throws IOException {
        write(projectDir.resolve("settings.gradle.kts"), "rootProject.name = \"katachi-processors-fixture\"\n");
        write(
                projectDir.resolve("build.gradle.kts"),
                "plugins {\n"
                        + "    java\n"
                        + "    id(\"" + PLUGIN_ID + "\")\n"
                        + "}\n"
                        + "\n"
                        + "tasks.withType<JavaExec>().configureEach {\n"
                        + "    mainClass.set(\"Recorder\")\n"
                        + "}\n"
                        + "\n"
                        + "katachi {\n"
                        + "    architecture = \"fixture.ProjectArchitectureKt\"\n"
                        + "    processors {\n"
                        + processors
                        + "    }\n"
                        + "}\n");
        write(
                projectDir.resolve("src/test/java/Recorder.java"),
                "public final class Recorder {\n"
                        + "    public static void main(String[] args) throws Exception {\n"
                        + "        java.io.File runs = new java.io.File(\"build/runs.txt\");\n"
                        + "        runs.getParentFile().mkdirs();\n"
                        + "        StringBuilder text = new StringBuilder();\n"
                        + "        for (String arg : args) text.append(arg).append('\\n');\n"
                        + "        text.append(\"--end--\\n\");\n"
                        + "        java.nio.file.Files.write(runs.toPath(), text.toString().getBytes(\"UTF-8\"),\n"
                        + "                java.nio.file.StandardOpenOption.CREATE,\n"
                        + "                java.nio.file.StandardOpenOption.APPEND);\n"
                        + "    }\n"
                        + "}\n");
    }

    /** The command line of every JVM the fixture's entry point was started in, oldest first. */
    private static List<List<String>> runs(Path projectDir) throws IOException {
        List<List<String>> runs = new ArrayList<>();
        List<String> current = new ArrayList<>();
        for (String line : read(projectDir.resolve("build/runs.txt")).split("\n")) {
            if (line.equals("--end--")) {
                runs.add(current);
                current = new ArrayList<>();
            } else {
                current.add(line);
            }
        }
        return runs.stream().map(ArrayList::new).collect(Collectors.toList());
    }

    private static void appendBuildScript(Path projectDir, String text) throws IOException {
        Path buildScript = projectDir.resolve("build.gradle.kts");
        write(buildScript, read(buildScript) + "\n" + text);
    }

    private static GradleRunner runner(Path projectDir, String gradleVersion, String... arguments) {
        List<String> all = new ArrayList<>(Arrays.asList(arguments));
        all.add("--stacktrace");
        all.add("--configuration-cache");
        return GradleRunner.create()
                .withProjectDir(projectDir.toFile())
                .withGradleVersion(gradleVersion)
                .withPluginClasspath()
                .forwardOutput()
                .withArguments(all);
    }

    private static BuildResult run(Path projectDir, String gradleVersion, String... arguments) {
        return runner(projectDir, gradleVersion, arguments).build();
    }

    private static BuildResult runAndFail(Path projectDir, String gradleVersion, String... arguments) {
        return runner(projectDir, gradleVersion, arguments).buildAndFail();
    }

    private static TaskOutcome outcome(BuildResult result, String taskPath) {
        BuildTask task = result.task(taskPath);
        assertNotNull(task, taskPath + " never entered the task graph:\n" + result.getOutput());
        return task.getOutcome();
    }

    private static String read(Path path) throws IOException {
        return new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
    }

    private static void write(Path path, String content) throws IOException {
        Files.createDirectories(path.getParent());
        Files.write(path, content.getBytes(StandardCharsets.UTF_8));
    }
}
