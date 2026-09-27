package me.tbsten.katachi.test.gradle;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
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
 * Drives a real {@code test} task of a build applying the plugin, and reads back what the test
 * JVM saw of {@code katachi.baseline.update} and {@code katachi.baseline.prune}.
 *
 * <p>The fixture's test runs on JUnit Jupiter, taken from this class's own classpath rather than
 * from a repository, so the build resolves nothing over the network.
 */
class KatachiBaselinePropertiesFunctionalTest {

    /** Same matrix as {@link KatachiPluginFunctionalTest}. */
    static Stream<String> gradleVersions() {
        String configured = System.getProperty("katachi.testkit.gradleVersions");
        assertNotNull(configured, "katachi.testkit.gradleVersions is not set; run this through the `test` task.");
        return Arrays.stream(configured.split(","))
                .map(String::trim)
                .filter(version -> !version.isEmpty());
    }

    @ParameterizedTest(name = "Gradle {0}")
    @MethodSource("gradleVersions")
    @DisplayName("-Dkatachi.baseline.update=true をつけた test では Test の JVM にそのプロパティが届き、つけなければ届かない")
    void passesTheBaselinePropertiesToTheTestJvm(String gradleVersion, @TempDir Path projectDir) throws IOException {
        writeFixture(projectDir);

        runner(projectDir, gradleVersion, "-Dkatachi.baseline.update=true").build();
        assertEquals("update=true prune=null", recorded(projectDir, "test"));

        runner(projectDir, gradleVersion, "-Dkatachi.baseline.prune=true").build();
        assertEquals("update=null prune=true", recorded(projectDir, "test"));

        runner(projectDir, gradleVersion).build();
        assertEquals("update=null prune=null", recorded(projectDir, "test"));
    }

    @ParameterizedTest(name = "Gradle {0}")
    @MethodSource("gradleVersions")
    @DisplayName("値が変わると test が UP-TO-DATE にならない")
    void aChangedValueMakesTheTestTaskRunAgain(String gradleVersion, @TempDir Path projectDir) throws IOException {
        writeFixture(projectDir);

        assertEquals(TaskOutcome.SUCCESS, testOutcome(runner(projectDir, gradleVersion).build()));
        assertEquals(TaskOutcome.UP_TO_DATE, testOutcome(runner(projectDir, gradleVersion).build()));
        assertEquals(
                TaskOutcome.SUCCESS,
                testOutcome(runner(projectDir, gradleVersion, "-Dkatachi.baseline.update=true").build()),
                "the test task stayed up to date although katachi.baseline.update changed");
        assertEquals("update=true prune=null", recorded(projectDir, "test"));
        assertEquals(
                TaskOutcome.SUCCESS,
                testOutcome(runner(projectDir, gradleVersion).build()),
                "the test task stayed up to date although katachi.baseline.update was dropped");
    }

    @ParameterizedTest(name = "Gradle {0}")
    @MethodSource("gradleVersions")
    @DisplayName("同じ値の update を2回続けても、test は UP-TO-DATE にもビルドキャッシュからの取り出しにもならず実行される")
    void theSameValueTwiceStillRunsTheTestTask(String gradleVersion, @TempDir Path projectDir) throws IOException {
        writeFixture(projectDir);

        for (String property : Arrays.asList("-Dkatachi.baseline.update=true", "-Dkatachi.baseline.prune=true")) {
            for (int run = 1; run <= 2; run++) {
                assertEquals(
                        TaskOutcome.SUCCESS,
                        testOutcome(runner(projectDir, gradleVersion, "--build-cache", property).build()),
                        "run " + run + " with " + property + " did not execute the test task");
            }
        }
        // Without the properties the task is an ordinary, cacheable one again. The first run may
        // be taken from the cache: TestKit shares its build cache between runs of this class.
        TaskOutcome ordinary = testOutcome(runner(projectDir, gradleVersion, "--build-cache").build());
        assertTrue(ordinary == TaskOutcome.SUCCESS || ordinary == TaskOutcome.FROM_CACHE, "unexpected outcome " + ordinary);
        assertEquals(TaskOutcome.UP_TO_DATE, testOutcome(runner(projectDir, gradleVersion, "--build-cache").build()));
    }

    @ParameterizedTest(name = "Gradle {0}")
    @MethodSource("gradleVersions")
    @DisplayName("test 以外に登録した Test タスクにも、あとから登録したものにもプロパティが届く")
    void everyTestTaskGetsTheProperties(String gradleVersion, @TempDir Path projectDir) throws IOException {
        writeFixture(projectDir);

        // `test` is always requested by the runner; this adds the second task to the same build.
        runner(projectDir, gradleVersion, "architectureTest", "-Dkatachi.baseline.update=true").build();

        assertEquals("update=true prune=null", recorded(projectDir, "architectureTest"));
        assertEquals("update=true prune=null", recorded(projectDir, "test"));
    }

    /** What the fixture's test wrote of the two properties it saw, when run by the Test task {@code task}. */
    private static String recorded(Path projectDir, String task) throws IOException {
        Path file = projectDir.resolve("build/recorded-" + task + ".txt");
        assertTrue(Files.exists(file), "the fixture's test never ran");
        return new String(Files.readAllBytes(file), StandardCharsets.UTF_8).trim();
    }

    private static TaskOutcome testOutcome(BuildResult result) {
        BuildTask task = result.task(":test");
        assertNotNull(task, ":test never entered the task graph:\n" + result.getOutput());
        return task.getOutcome();
    }

    /** JUnit's own jars, off this JVM's classpath, as Kotlin string literals. */
    private static String junitJars() {
        return Arrays.stream(System.getProperty("java.class.path").split(File.pathSeparator))
                .filter(entry -> {
                    String name = new File(entry).getName();
                    return name.endsWith(".jar")
                            && (name.startsWith("junit-") || name.startsWith("opentest4j") || name.startsWith("apiguardian"));
                })
                .map(entry -> "\"" + entry.replace("\\", "\\\\") + "\"")
                .collect(Collectors.joining(", "));
    }

    private static void writeFixture(Path projectDir) throws IOException {
        write(projectDir.resolve("settings.gradle.kts"), "rootProject.name = \"katachi-baseline-fixture\"\n");
        write(
                projectDir.resolve("build.gradle.kts"),
                "plugins {\n"
                        + "    java\n"
                        + "    id(\"me.tbsten.katachi\")\n"
                        + "}\n"
                        + "\n"
                        + "dependencies {\n"
                        + "    testImplementation(files(" + junitJars() + "))\n"
                        + "}\n"
                        + "\n"
                        + "tasks.withType<Test>().configureEach {\n"
                        + "    useJUnitPlatform()\n"
                        + "    systemProperty(\"recorded.file\", \"build/recorded-$name.txt\")\n"
                        + "    outputs.file(\"build/recorded-$name.txt\")\n"
                        + "}\n"
                        + "\n"
                        + "// Registered after the plugin was applied, the way a project adds a task of its own.\n"
                        + "tasks.register<Test>(\"architectureTest\") {\n"
                        + "    testClassesDirs = sourceSets.test.get().output.classesDirs\n"
                        + "    classpath = sourceSets.test.get().runtimeClasspath\n"
                        + "}\n"
                        + "\n"
                        + "katachi {\n"
                        + "    architecture = \"fixture.ProjectArchitectureKt\"\n"
                        + "}\n");
        write(
                projectDir.resolve("src/test/java/RecordTest.java"),
                "import org.junit.jupiter.api.Test;\n"
                        + "\n"
                        + "public class RecordTest {\n"
                        + "    @Test\n"
                        + "    public void record() throws Exception {\n"
                        + "        String text = \"update=\" + System.getProperty(\"katachi.baseline.update\")\n"
                        + "                + \" prune=\" + System.getProperty(\"katachi.baseline.prune\");\n"
                        + "        java.nio.file.Files.write(\n"
                        + "                java.nio.file.Paths.get(System.getProperty(\"recorded.file\")), text.getBytes(\"UTF-8\"));\n"
                        + "    }\n"
                        + "}\n");
    }

    private static void write(Path path, String content) throws IOException {
        Files.createDirectories(path.getParent());
        Files.write(path, content.getBytes(StandardCharsets.UTF_8));
    }

    private static GradleRunner runner(Path projectDir, String gradleVersion, String... extraArguments) {
        List<String> arguments = new ArrayList<>();
        arguments.add("test");
        arguments.add("--stacktrace");
        arguments.add("--configuration-cache");
        arguments.addAll(Arrays.asList(extraArguments));
        return GradleRunner.create()
                .withProjectDir(projectDir.toFile())
                .withGradleVersion(gradleVersion)
                .withPluginClasspath()
                .forwardOutput()
                .withArguments(arguments);
    }
}
