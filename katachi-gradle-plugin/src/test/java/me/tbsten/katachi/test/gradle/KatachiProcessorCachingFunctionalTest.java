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
 * Which processor tasks Gradle may skip or restore from the build cache, and which it must run
 * every time.
 *
 * <p>A processor task is only skippable when it declared that it reads no project file and
 * said where it writes. Everything else -- a processor that walks the project, one that
 * declared nothing, a documentation run in {@code mode=check} that writes nothing -- has to
 * keep running every time, because Gradle has no input that would tell it the project changed.
 * An up-to-date answer there would be a check passing on files it never looked at.
 *
 * <p>The fixture's entry point imitates the documentation processor: it writes one page to the
 * directory {@code --arg=outputDir=} names, {@code build/katachi/docs} otherwise, and writes
 * nothing under {@code --arg=mode=check}.
 */
class KatachiProcessorCachingFunctionalTest {

    private static final String PLUGIN_ID = "me.tbsten.katachi";

    /** Where the fixture's entry point writes when no {@code outputDir} is given. */
    private static final String DEFAULT_PAGE = "build/katachi/docs/index.md";

    static Stream<String> gradleVersions() {
        return KatachiPluginFunctionalTest.gradleVersions();
    }

    @ParameterizedTest(name = "Gradle {0}")
    @MethodSource("gradleVersions")
    @DisplayName("docs は2回目が UP-TO-DATE になり、出力を消すと build cache から戻る")
    void docsIsUpToDateAndRestoredFromTheCache(String gradleVersion, @TempDir Path projectDir)
            throws IOException {
        writeFixture(projectDir, "");

        assertEquals(TaskOutcome.SUCCESS, outcome(run(projectDir, gradleVersion, ":katachiDocs"), ":katachiDocs"));
        assertEquals(
                TaskOutcome.UP_TO_DATE,
                outcome(run(projectDir, gradleVersion, ":katachiDocs"), ":katachiDocs"));

        deleteRecursively(projectDir.resolve("build/katachi/docs"));
        assertEquals(
                TaskOutcome.FROM_CACHE,
                outcome(run(projectDir, gradleVersion, ":katachiDocs"), ":katachiDocs"));
        assertEquals("page of v1", read(projectDir.resolve(DEFAULT_PAGE)));
    }

    @ParameterizedTest(name = "Gradle {0}")
    @MethodSource("gradleVersions")
    @DisplayName("定義（test のクラス）か --arg が変われば docs は再実行される")
    void docsRunsAgainWhenAnInputChanges(String gradleVersion, @TempDir Path projectDir)
            throws IOException {
        writeFixture(projectDir, "");
        run(projectDir, gradleVersion, ":katachiDocs");

        writeEntryPoint(projectDir, "v2");
        assertEquals(TaskOutcome.SUCCESS, outcome(run(projectDir, gradleVersion, ":katachiDocs"), ":katachiDocs"));
        assertEquals("page of v2", read(projectDir.resolve(DEFAULT_PAGE)));

        assertEquals(
                TaskOutcome.SUCCESS,
                outcome(run(projectDir, gradleVersion, ":katachiDocs", "--arg", "note=x"), ":katachiDocs"));
    }

    @ParameterizedTest(name = "Gradle {0}")
    @MethodSource("gradleVersions")
    @DisplayName("--arg outputDir= で出力先を変えると、Gradle が見る出力もそこに移る")
    void theOutputFollowsTheOutputDirArg(String gradleVersion, @TempDir Path projectDir)
            throws IOException {
        writeFixture(projectDir, "");
        String[] toElsewhere = {":katachiDocs", "--arg", "outputDir=build/elsewhere"};

        run(projectDir, gradleVersion, toElsewhere);
        assertTrue(Files.exists(projectDir.resolve("build/elsewhere/index.md")));
        assertEquals(
                TaskOutcome.UP_TO_DATE,
                outcome(run(projectDir, gradleVersion, toElsewhere), ":katachiDocs"));

        // Had Gradle been watching build/katachi/docs instead, this would still be UP-TO-DATE
        // and the page would stay missing. FROM-CACHE is as good as a run: the page is back.
        Files.delete(projectDir.resolve("build/elsewhere/index.md"));
        TaskOutcome third = outcome(run(projectDir, gradleVersion, toElsewhere), ":katachiDocs");
        assertTrue(
                third == TaskOutcome.SUCCESS || third == TaskOutcome.FROM_CACHE,
                "the deleted page was not noticed: " + third);
        assertEquals("page of v1", read(projectDir.resolve("build/elsewhere/index.md")));
    }

    @ParameterizedTest(name = "Gradle {0}")
    @MethodSource("gradleVersions")
    @DisplayName("mode=check の docs は何も書かないので毎回実行され、出力先も作られない")
    void docsInCheckModeRunsEveryTime(String gradleVersion, @TempDir Path projectDir) throws IOException {
        writeFixture(projectDir, "");
        appendBuildScript(
                projectDir,
                "katachi { processors { docs { mode = me.tbsten.katachi.gradle.KatachiDocsMode.CHECK } } }\n");

        assertEquals(TaskOutcome.SUCCESS, outcome(run(projectDir, gradleVersion, ":katachiDocs"), ":katachiDocs"));
        assertEquals(TaskOutcome.SUCCESS, outcome(run(projectDir, gradleVersion, ":katachiDocs"), ":katachiDocs"));
        assertFalse(
                Files.exists(projectDir.resolve("build/katachi/docs")),
                "Gradle created the output directory of a run that writes nothing");
    }

    @ParameterizedTest(name = "Gradle {0}")
    @MethodSource("gradleVersions")
    @DisplayName("--arg mode=check も同じく毎回実行される")
    void docsWithTheCheckArgRunsEveryTime(String gradleVersion, @TempDir Path projectDir) throws IOException {
        writeFixture(projectDir, "");

        run(projectDir, gradleVersion, ":katachiDocs", "--arg", "mode=check");
        assertEquals(
                TaskOutcome.SUCCESS,
                outcome(run(projectDir, gradleVersion, ":katachiDocs", "--arg", "mode=check"), ":katachiDocs"));
    }

    @ParameterizedTest(name = "Gradle {0}")
    @MethodSource("gradleVersions")
    @DisplayName("何も宣言していない processor はこれまでどおり毎回実行される")
    void anUndeclaredProcessorRunsEveryTime(String gradleVersion, @TempDir Path projectDir)
            throws IOException {
        writeFixture(projectDir, "register(\"roleNames\", \"fixture.RoleNames\")\n");

        run(projectDir, gradleVersion, ":katachiRoleNames");
        assertEquals(
                TaskOutcome.SUCCESS,
                outcome(run(projectDir, gradleVersion, ":katachiRoleNames"), ":katachiRoleNames"));
    }

    @ParameterizedTest(name = "Gradle {0}")
    @MethodSource("gradleVersions")
    @DisplayName("walk しないと宣言し出力先を言った processor は UP-TO-DATE になる")
    void aDeclaredNonWalkingProcessorIsUpToDate(String gradleVersion, @TempDir Path projectDir)
            throws IOException {
        writeFixture(
                projectDir,
                "register(\"roleNames\", \"fixture.RoleNames\")\n"
                        + "outputs(\"roleNames\") {\n"
                        + "    readsProjectFiles = false\n"
                        + "    outputDir = \"build/katachi/docs\"\n"
                        + "}\n");

        run(projectDir, gradleVersion, ":katachiRoleNames");
        assertEquals(
                TaskOutcome.UP_TO_DATE,
                outcome(run(projectDir, gradleVersion, ":katachiRoleNames"), ":katachiRoleNames"));
    }

    @ParameterizedTest(name = "Gradle {0}")
    @MethodSource("gradleVersions")
    @DisplayName("walk すると宣言した processor は出力先を言っても毎回実行される")
    void aWalkingProcessorRunsEveryTime(String gradleVersion, @TempDir Path projectDir)
            throws IOException {
        writeFixture(
                projectDir,
                "register(\"layout\", \"me.tbsten.katachi.check.LayoutCheck\")\n"
                        + "outputs(\"layout\") {\n"
                        + "    readsProjectFiles = true\n"
                        + "    outputDir = \"build/katachi/docs\"\n"
                        + "}\n"
                        + "outputs(\"docs\") {\n"
                        + "    readsProjectFiles = true\n"
                        + "}\n");

        run(projectDir, gradleVersion, ":katachiLayout", ":katachiDocs");
        BuildResult second = run(projectDir, gradleVersion, ":katachiLayout", ":katachiDocs");
        assertEquals(TaskOutcome.SUCCESS, outcome(second, ":katachiLayout"));
        assertEquals(TaskOutcome.SUCCESS, outcome(second, ":katachiDocs"));
    }

    @ParameterizedTest(name = "Gradle {0}")
    @MethodSource("gradleVersions")
    @DisplayName("docs を自前の processor に差し替えると、katachi の docs の宣言は引き継がれない")
    void aReplacedDocsProcessorRunsEveryTime(String gradleVersion, @TempDir Path projectDir)
            throws IOException {
        writeFixture(projectDir, "register(\"docs\", \"fixture.OurOwnDocs\")\n");

        run(projectDir, gradleVersion, ":katachiDocs");
        assertEquals(
                TaskOutcome.SUCCESS,
                outcome(run(projectDir, gradleVersion, ":katachiDocs"), ":katachiDocs"));
    }

    /**
     * Writes a fixture project whose every processor task starts the fixture's own entry point.
     *
     * @param processors the body of {@code katachi { processors { } } }, or the empty string.
     */
    private static void writeFixture(Path projectDir, String processors) throws IOException {
        // A build cache of the fixture's own, so that a FROM-CACHE answer cannot come from an
        // entry some earlier test left in TestKit's shared Gradle user home.
        write(
                projectDir.resolve("settings.gradle.kts"),
                "rootProject.name = \"katachi-caching-fixture\"\n"
                        + "buildCache {\n"
                        + "    local {\n"
                        + "        directory = File(rootDir, \"build-cache\")\n"
                        + "    }\n"
                        + "}\n");
        write(
                projectDir.resolve("build.gradle.kts"),
                "import me.tbsten.katachi.gradle.KatachiProcessorTask\n"
                        + "\n"
                        + "plugins {\n"
                        + "    java\n"
                        + "    id(\"" + PLUGIN_ID + "\")\n"
                        + "}\n"
                        + "\n"
                        + "tasks.withType<KatachiProcessorTask>().configureEach {\n"
                        + "    mainClass.set(\"Docs\")\n"
                        + "}\n"
                        + "\n"
                        + "katachi {\n"
                        + "    architecture = \"fixture.ProjectArchitectureKt\"\n"
                        + "    processors {\n"
                        + processors
                        + "    }\n"
                        + "}\n");
        writeEntryPoint(projectDir, "v1");
    }

    /**
     * Writes the fixture's entry point, which stands in for the definition: changing
     * {@code version} is changing what the test source set compiles to.
     */
    private static void writeEntryPoint(Path projectDir, String version) throws IOException {
        write(
                projectDir.resolve("src/test/java/Docs.java"),
                "public final class Docs {\n"
                        + "    public static void main(String[] args) throws Exception {\n"
                        + "        String outputDir = \"build/katachi/docs\";\n"
                        + "        for (String arg : args) {\n"
                        + "            if (arg.equals(\"--arg=mode=check\")) return;\n"
                        + "            if (arg.startsWith(\"--arg=outputDir=\")) {\n"
                        + "                outputDir = arg.substring(\"--arg=outputDir=\".length());\n"
                        + "            }\n"
                        + "        }\n"
                        + "        java.io.File page = new java.io.File(outputDir, \"index.md\");\n"
                        + "        page.getParentFile().mkdirs();\n"
                        + "        java.nio.file.Files.write(\n"
                        + "                page.toPath(), \"page of " + version + "\".getBytes(\"UTF-8\"));\n"
                        + "    }\n"
                        + "}\n");
    }

    private static void appendBuildScript(Path projectDir, String text) throws IOException {
        Path buildScript = projectDir.resolve("build.gradle.kts");
        write(buildScript, read(buildScript) + "\n" + text);
    }

    private static BuildResult run(Path projectDir, String gradleVersion, String... arguments) {
        List<String> all = new ArrayList<>(Arrays.asList(arguments));
        all.add("--stacktrace");
        all.add("--configuration-cache");
        all.add("--build-cache");
        return GradleRunner.create()
                .withProjectDir(projectDir.toFile())
                .withGradleVersion(gradleVersion)
                .withPluginClasspath()
                .forwardOutput()
                .withArguments(all)
                .build();
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

    private static void deleteRecursively(Path path) throws IOException {
        if (!Files.exists(path)) {
            return;
        }
        try (Stream<Path> walk = Files.walk(path)) {
            walk.sorted((a, b) -> b.compareTo(a)).forEach(each -> each.toFile().delete());
        }
    }
}
