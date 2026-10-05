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
 * Drives a real build applying the plugin, and reads back what the JVM of a {@code Test} task and
 * of a {@code katachi<Key>} task saw of {@code katachi.maxViolations}.
 *
 * <p>The fixture's test runs on JUnit Jupiter, taken from this class's own classpath rather than
 * from a repository, so the build resolves nothing over the network. Its processor task starts a
 * {@code main} of its own that records the same property, in place of katachi's entry point.
 */
class KatachiMaxViolationsPropertyFunctionalTest {

    /** Same matrix as {@link KatachiPluginFunctionalTest}. */
    static Stream<String> gradleVersions() {
        return KatachiPluginFunctionalTest.gradleVersions();
    }

    @ParameterizedTest(name = "Gradle {0}")
    @MethodSource("gradleVersions")
    @DisplayName("-Dkatachi.maxViolations=all をつけた test では Test の JVM にそのプロパティが届き、つけなければ届かない")
    void passesThePropertyToTheTestJvm(String gradleVersion, @TempDir Path projectDir) throws IOException {
        writeFixture(projectDir);

        runner(projectDir, gradleVersion, "test", "-Dkatachi.maxViolations=all").build();
        assertEquals("maxViolations=all", recorded(projectDir, "test"));

        runner(projectDir, gradleVersion, "test", "-Dkatachi.maxViolations=50").build();
        assertEquals("maxViolations=50", recorded(projectDir, "test"));

        runner(projectDir, gradleVersion, "test").build();
        assertEquals("maxViolations=null", recorded(projectDir, "test"));
    }

    @ParameterizedTest(name = "Gradle {0}")
    @MethodSource("gradleVersions")
    @DisplayName("値が変わると test が UP-TO-DATE にならない")
    void aChangedValueMakesTheTestTaskRunAgain(String gradleVersion, @TempDir Path projectDir) throws IOException {
        writeFixture(projectDir);

        assertEquals(TaskOutcome.SUCCESS, outcome(runner(projectDir, gradleVersion, "test").build(), ":test"));
        assertEquals(TaskOutcome.UP_TO_DATE, outcome(runner(projectDir, gradleVersion, "test").build(), ":test"));
        assertEquals(
                TaskOutcome.SUCCESS,
                outcome(runner(projectDir, gradleVersion, "test", "-Dkatachi.maxViolations=all").build(), ":test"),
                "the test task stayed up to date although katachi.maxViolations was given");
        assertEquals("maxViolations=all", recorded(projectDir, "test"));
        // Same value again: nothing is written anywhere, so UP-TO-DATE is right this time.
        assertEquals(
                TaskOutcome.UP_TO_DATE,
                outcome(runner(projectDir, gradleVersion, "test", "-Dkatachi.maxViolations=all").build(), ":test"));
        assertEquals(
                TaskOutcome.SUCCESS,
                outcome(runner(projectDir, gradleVersion, "test").build(), ":test"),
                "the test task stayed up to date although katachi.maxViolations was dropped");
        assertEquals("maxViolations=null", recorded(projectDir, "test"));
    }

    @ParameterizedTest(name = "Gradle {0}")
    @MethodSource("gradleVersions")
    @DisplayName("test 以外に登録した Test タスクにもプロパティが届く")
    void everyTestTaskGetsTheProperty(String gradleVersion, @TempDir Path projectDir) throws IOException {
        writeFixture(projectDir);

        runner(projectDir, gradleVersion, "test", "architectureTest", "-Dkatachi.maxViolations=all").build();

        assertEquals("maxViolations=all", recorded(projectDir, "architectureTest"));
        assertEquals("maxViolations=all", recorded(projectDir, "test"));
    }

    @ParameterizedTest(name = "Gradle {0}")
    @MethodSource("gradleVersions")
    @DisplayName("katachi<Key> タスクの JVM にもプロパティが届く")
    void theProcessorTaskGetsTheProperty(String gradleVersion, @TempDir Path projectDir) throws IOException {
        writeFixture(projectDir);

        runner(projectDir, gradleVersion, ":katachiLayout", "-Dkatachi.maxViolations=all").build();
        assertEquals("maxViolations=all", recorded(projectDir, "katachiLayout"));

        runner(projectDir, gradleVersion, ":katachiLayout").build();
        assertEquals("maxViolations=null", recorded(projectDir, "katachiLayout"));
    }

    /** What the fixture wrote of the property it saw, when run by the task {@code task}. */
    private static String recorded(Path projectDir, String task) throws IOException {
        Path file = projectDir.resolve("build/recorded-" + task + ".txt");
        assertTrue(Files.exists(file), "the fixture never ran for " + task);
        return new String(Files.readAllBytes(file), StandardCharsets.UTF_8).trim();
    }

    private static TaskOutcome outcome(BuildResult result, String taskPath) {
        BuildTask task = result.task(taskPath);
        assertNotNull(task, taskPath + " never entered the task graph:\n" + result.getOutput());
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
        write(projectDir.resolve("settings.gradle.kts"), "rootProject.name = \"katachi-max-violations-fixture\"\n");
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
                        + "// The processor task starts the fixture's own recorder instead of katachi's entry point.\n"
                        + "// The file goes in as a system property: the task sets its own program arguments.\n"
                        + "tasks.withType<JavaExec>().configureEach {\n"
                        + "    mainClass.set(\"Recorder\")\n"
                        + "    systemProperty(\"recorded.file\", \"build/recorded-$name.txt\")\n"
                        + "}\n"
                        + "\n"
                        + "katachi {\n"
                        + "    architecture = \"fixture.ProjectArchitectureKt\"\n"
                        + "    processors {\n"
                        + "        register(\"layout\", \"fixture.Layout\")\n"
                        + "    }\n"
                        + "}\n");
        write(
                projectDir.resolve("src/test/java/RecordTest.java"),
                "import org.junit.jupiter.api.Test;\n"
                        + "\n"
                        + "public class RecordTest {\n"
                        + "    @Test\n"
                        + "    public void record() throws Exception {\n"
                        + "        Recorder.record(System.getProperty(\"recorded.file\"));\n"
                        + "    }\n"
                        + "}\n");
        write(
                projectDir.resolve("src/test/java/Recorder.java"),
                "public final class Recorder {\n"
                        + "    public static void main(String[] args) throws Exception {\n"
                        + "        record(System.getProperty(\"recorded.file\"));\n"
                        + "    }\n"
                        + "\n"
                        + "    static void record(String file) throws Exception {\n"
                        + "        String text = \"maxViolations=\" + System.getProperty(\"katachi.maxViolations\");\n"
                        + "        java.nio.file.Path path = java.nio.file.Paths.get(file);\n"
                        + "        java.nio.file.Files.createDirectories(path.getParent());\n"
                        + "        java.nio.file.Files.write(path, text.getBytes(\"UTF-8\"));\n"
                        + "    }\n"
                        + "}\n");
    }

    private static void write(Path path, String content) throws IOException {
        Files.createDirectories(path.getParent());
        Files.write(path, content.getBytes(StandardCharsets.UTF_8));
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
}
