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
 * JVM saw of {@code katachi.report.sarif} and {@code katachi.report.sourceRoots}.
 *
 * <p>The fixture's test runs on JUnit Jupiter, taken from this class's own classpath rather than
 * from a repository, so the build resolves nothing over the network.
 */
class KatachiReportPropertiesFunctionalTest {

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
    @DisplayName("report { sarif = true } では Test の JVM に出力パスとテストのソースディレクトリが届く")
    void passesTheReportPropertiesToTheTestJvm(String gradleVersion, @TempDir Path projectDir) throws IOException {
        writeFixture(projectDir, "sarif = true");

        runner(projectDir, gradleVersion).build();

        String[] recorded = recorded(projectDir, "test").split("\n");
        assertEquals("sarif=" + projectDir.toRealPath().resolve("build/reports/katachi/katachi.sarif"), recorded[0]);
        assertTrue(recorded[1].startsWith("roots="), recorded[1]);
        assertTrue(
                recorded[1].contains(projectDir.toRealPath().resolve("src/test/java").toString()),
                "the test source directory is missing: " + recorded[1]);
    }

    @ParameterizedTest(name = "Gradle {0}")
    @MethodSource("gradleVersions")
    @DisplayName("sarif = false でも report を書かなくても、プロパティは 1 つも届かない")
    void passesNothingWhileTheReportIsOff(String gradleVersion, @TempDir Path projectDir) throws IOException {
        writeFixture(projectDir, "sarif = false");
        runner(projectDir, gradleVersion).build();
        assertEquals("sarif=null\nroots=null", recorded(projectDir, "test"));

        writeFixture(projectDir, null);
        runner(projectDir, gradleVersion).build();
        assertEquals("sarif=null\nroots=null", recorded(projectDir, "test"));
    }

    @ParameterizedTest(name = "Gradle {0}")
    @MethodSource("gradleVersions")
    @DisplayName("test 以外の Test タスクは build/reports/katachi/<タスク名>/ の下に書き、test とは別の出力になる")
    void otherTestTasksWriteUnderTheirOwnName(String gradleVersion, @TempDir Path projectDir) throws IOException {
        writeFixture(projectDir, "sarif = true");

        runner(projectDir, gradleVersion, "architectureTest").build();

        assertTrue(recorded(projectDir, "architectureTest")
                .startsWith("sarif=" + projectDir.toRealPath().resolve("build/reports/katachi/architectureTest/katachi.sarif")));
        assertTrue(Files.exists(projectDir.resolve("build/reports/katachi/architectureTest/katachi.sarif")));
        assertTrue(Files.exists(projectDir.resolve("build/reports/katachi/katachi.sarif")));
    }

    @ParameterizedTest(name = "Gradle {0}")
    @MethodSource("gradleVersions")
    @DisplayName("SARIF ファイルは test の出力に宣言され、消すと test が再実行され、report を切り替えても再実行される")
    void theSarifFileIsAnOutputOfTheTestTask(String gradleVersion, @TempDir Path projectDir) throws IOException {
        writeFixture(projectDir, "sarif = true");
        Path sarif = projectDir.resolve("build/reports/katachi/katachi.sarif");

        assertEquals(TaskOutcome.SUCCESS, testOutcome(runner(projectDir, gradleVersion).build()));
        assertTrue(Files.exists(sarif), "the fixture's test did not write the SARIF file");
        assertEquals(TaskOutcome.UP_TO_DATE, testOutcome(runner(projectDir, gradleVersion).build()));

        Files.delete(sarif);
        assertEquals(
                TaskOutcome.SUCCESS,
                testOutcome(runner(projectDir, gradleVersion).build()),
                "the test task stayed up to date although its SARIF output was deleted");
        assertTrue(Files.exists(sarif));

        writeFixture(projectDir, "sarif = false");
        assertEquals(
                TaskOutcome.SUCCESS,
                testOutcome(runner(projectDir, gradleVersion).build()),
                "the test task stayed up to date although the report was turned off");
    }

    @ParameterizedTest(name = "Gradle {0}")
    @MethodSource("gradleVersions")
    @DisplayName("前回の katachi.sarif と連番の katachi-2.sarif が残っていても、test の開始時に消える")
    void deletesTheReportsOfAnEarlierRunWhenTheTestStarts(String gradleVersion, @TempDir Path projectDir) throws IOException {
        writeFixture(projectDir, "sarif = true");
        Path reports = projectDir.resolve("build/reports/katachi");
        write(reports.resolve("katachi.sarif"), "stale");
        write(reports.resolve("katachi-2.sarif"), "stale");
        write(reports.resolve("katachi-3.sarif"), "stale");

        runner(projectDir, gradleVersion).build();

        assertEquals(
                "{}",
                new String(Files.readAllBytes(reports.resolve("katachi.sarif")), StandardCharsets.UTF_8),
                "the stale katachi.sarif was not replaced by this run's");
        assertTrue(Files.notExists(reports.resolve("katachi-2.sarif")), "katachi-2.sarif of an earlier run survived");
        assertTrue(Files.notExists(reports.resolve("katachi-3.sarif")), "katachi-3.sarif of an earlier run survived");
    }

    @ParameterizedTest(name = "Gradle {0}")
    @MethodSource("gradleVersions")
    @DisplayName("sarif = false なら残っているファイルに触らない")
    void leavesOldReportsAloneWhileTheReportIsOff(String gradleVersion, @TempDir Path projectDir) throws IOException {
        writeFixture(projectDir, "sarif = false");
        Path stale = projectDir.resolve("build/reports/katachi/katachi.sarif");
        write(stale, "stale");

        runner(projectDir, gradleVersion).build();

        assertEquals("stale", new String(Files.readAllBytes(stale), StandardCharsets.UTF_8));
    }

    /** What the fixture's test saw of the two properties, when run by the Test task {@code task}. */
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

    /** The fixture; {@code reportBody} goes into {@code katachi { report { } }}, or no block at all when null. */
    private static void writeFixture(Path projectDir, String reportBody) throws IOException {
        write(projectDir.resolve("settings.gradle.kts"), "rootProject.name = \"katachi-report-fixture\"\n");
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
                        + (reportBody == null ? "" : "    report {\n        " + reportBody + "\n    }\n")
                        + "}\n");
        write(
                projectDir.resolve("src/test/java/RecordTest.java"),
                "import org.junit.jupiter.api.Test;\n"
                        + "import java.nio.file.*;\n"
                        + "\n"
                        + "public class RecordTest {\n"
                        + "    @Test\n"
                        + "    public void record() throws Exception {\n"
                        + "        String sarif = System.getProperty(\"katachi.report.sarif\");\n"
                        + "        String text = \"sarif=\" + sarif + \"\\nroots=\" + System.getProperty(\"katachi.report.sourceRoots\");\n"
                        + "        Files.write(Paths.get(System.getProperty(\"recorded.file\")), text.getBytes(\"UTF-8\"));\n"
                        + "        if (sarif != null) {\n"
                        + "            Files.createDirectories(Paths.get(sarif).getParent());\n"
                        + "            Files.write(Paths.get(sarif), \"{}\".getBytes(\"UTF-8\"));\n"
                        + "        }\n"
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
