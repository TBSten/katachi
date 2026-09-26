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
 * {@code katachiInternalTemplatesJson}: the task the katachi IDE plugin runs to read the
 * templates of a module, registered for every module and kept out of the way of people.
 *
 * <p>The fixture's entry point imitates {@code DescribeTemplates} with {@code format=json}: it
 * writes a file to wherever {@code --arg=output=} points, and records its command line next to
 * it. What the real processor writes into that file is {@code :katachi}'s own spec.
 */
class KatachiInternalTemplatesJsonFunctionalTest {

    private static final String PLUGIN_ID = "me.tbsten.katachi";

    private static final String TASK_NAME = "katachiInternalTemplatesJson";

    private static final String JSON_PATH = "build/katachi/internalTemplatesJson/templateDescription.json";

    private static final String DESCRIPTION =
            "Internal: writes the template list as JSON for the katachi IDE plugin. "
                    + "Not meant to be run by hand.";

    static Stream<String> gradleVersions() {
        return KatachiPluginFunctionalTest.gradleVersions();
    }

    @ParameterizedTest(name = "Gradle {0}")
    @MethodSource("gradleVersions")
    @DisplayName("group なし・IDE 用の説明つきで登録され、tasks には出ず tasks --all にだけ出る")
    void isRegisteredWithoutAGroup(String gradleVersion, @TempDir Path projectDir) throws IOException {
        writeFixture(projectDir, "");

        String tasks = runner(projectDir, gradleVersion, "tasks").build().getOutput();
        assertFalse(tasks.contains(TASK_NAME), TASK_NAME + " is listed without --all:\n" + tasks);

        String all = runner(projectDir, gradleVersion, "tasks", "--all").build().getOutput();
        assertTrue(
                tasksOfGroup(all, "Other tasks").contains(TASK_NAME),
                TASK_NAME + " is not among the tasks without a group:\n" + all);
        assertTrue(
                all.contains(TASK_NAME + " - " + DESCRIPTION),
                TASK_NAME + " does not carry the IDE description:\n" + all);
    }

    @ParameterizedTest(name = "Gradle {0}")
    @MethodSource("gradleVersions")
    @DisplayName("実行すると format=json と既定の output を渡して JSON ができ、2回目は UP-TO-DATE")
    void writesTheJsonAndIsUpToDateTheSecondTime(String gradleVersion, @TempDir Path projectDir)
            throws IOException {
        writeFixture(projectDir, "");

        assertEquals(TaskOutcome.SUCCESS, outcome(runner(projectDir, gradleVersion, TASK_NAME).build()));

        assertTrue(Files.exists(projectDir.resolve(JSON_PATH)), "the JSON was not written to " + JSON_PATH);
        List<String> args = Arrays.asList(read(projectDir.resolve("build/katachi/args.txt")).split("\n"));
        assertTrue(args.contains("--processor=internalTemplatesJson"), "wrong processor:\n" + args);
        assertTrue(args.contains("--arg=format=json"), "format=json was not passed:\n" + args);
        assertTrue(args.contains("--arg=output=" + JSON_PATH), "the default output was not passed:\n" + args);

        assertEquals(TaskOutcome.UP_TO_DATE, outcome(runner(projectDir, gradleVersion, TASK_NAME).build()));
    }

    @ParameterizedTest(name = "Gradle {0}")
    @MethodSource("gradleVersions")
    @DisplayName("利用者が同じキーを別のクラスで register すると、ふつうの katachi のタスクになる")
    void aReplacedKeyIsAnOrdinaryTask(String gradleVersion, @TempDir Path projectDir) throws IOException {
        writeFixture(
                projectDir,
                "\n"
                        + "katachi {\n"
                        + "    processors {\n"
                        + "        register(\"internalTemplatesJson\", \"fixture.OurOwnJson\")\n"
                        + "    }\n"
                        + "}\n");

        String output = runner(projectDir, gradleVersion, "tasks", "--group", "katachi").build().getOutput();

        assertTrue(
                output.contains(TASK_NAME + " - Runs the katachi processor fixture.OurOwnJson"),
                "the replacing registration did not become an ordinary katachi task:\n" + output);
    }

    /** A {@code src/test/java} entry point that writes what {@code --arg=output=} names. */
    private static final String WRITE_OUTPUT =
            "public final class WriteOutput {\n"
                    + "    public static void main(String[] args) throws Exception {\n"
                    + "        java.io.File recorded = new java.io.File(\"build/katachi/args.txt\");\n"
                    + "        recorded.getParentFile().mkdirs();\n"
                    + "        java.nio.file.Files.write(\n"
                    + "                recorded.toPath(), String.join(\"\\n\", args).getBytes(\"UTF-8\"));\n"
                    + "        for (String arg : args) {\n"
                    + "            if (arg.startsWith(\"--arg=output=\")) {\n"
                    + "                java.io.File out = new java.io.File(arg.substring(\"--arg=output=\".length()));\n"
                    + "                out.getParentFile().mkdirs();\n"
                    + "                java.nio.file.Files.write(out.toPath(), \"{}\\n\".getBytes(\"UTF-8\"));\n"
                    + "            }\n"
                    + "        }\n"
                    + "    }\n"
                    + "}\n";

    private static void writeFixture(Path projectDir, String extraBuildScript) throws IOException {
        write(projectDir.resolve("settings.gradle.kts"), "rootProject.name = \"katachi-fixture\"\n");
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
                        + "    mainClass.set(\"WriteOutput\")\n"
                        + "}\n"
                        + "\n"
                        + "katachi {\n"
                        + "    architecture = \"fixture.ProjectArchitectureKt\"\n"
                        + "}\n"
                        + extraBuildScript);
        write(projectDir.resolve("src/test/java/WriteOutput.java"), WRITE_OUTPUT);
    }

    private static void write(Path path, String content) throws IOException {
        Files.createDirectories(path.getParent());
        Files.write(path, content.getBytes(StandardCharsets.UTF_8));
    }

    private static String read(Path path) throws IOException {
        return new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
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

    private static TaskOutcome outcome(BuildResult result) {
        BuildTask task = result.task(":" + TASK_NAME);
        assertNotNull(task, TASK_NAME + " never entered the task graph:\n" + result.getOutput());
        return task.getOutcome();
    }

    /** The task names {@code ./gradlew tasks} lists under {@code header}. */
    private static List<String> tasksOfGroup(String output, String header) {
        List<String> lines = Arrays.asList(output.split("\\R"));
        int start = lines.indexOf(header);
        assertTrue(start >= 0, "no \"" + header + "\" section in:\n" + output);
        List<String> names = new ArrayList<>();
        for (String line : lines.subList(start + 2, lines.size())) {
            if (line.trim().isEmpty()) {
                break;
            }
            names.add(line.split(" ", 2)[0]);
        }
        return names;
    }
}
