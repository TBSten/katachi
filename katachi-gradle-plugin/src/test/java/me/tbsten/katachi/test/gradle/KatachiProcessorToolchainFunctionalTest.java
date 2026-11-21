package me.tbsten.katachi.test.gradle;

import static org.junit.jupiter.api.Assertions.assertEquals;
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

import org.gradle.testkit.runner.GradleRunner;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * The processor tasks start on the module's Java toolchain, on Gradle 8 as on Gradle 9.
 *
 * <p>The fixture's toolchain is a Java newer than the one running Gradle, and its tests are compiled for it, so a
 * processor JVM started on the JVM running Gradle could not load them. The fixture's entry point records the Java
 * version it runs on in {@code build/recorded-java.txt}.
 *
 * <p>The newer JDK is the one this module's build passes as {@code katachi.testkit.newerJavaHome}. It and the JVM
 * running this test are the only ones the fixture knows of, so what the machine has installed does not change what
 * the fixture finds.
 */
class KatachiProcessorToolchainFunctionalTest {

    private static final String PLUGIN_ID = "me.tbsten.katachi";

    static Stream<String> gradleVersions() {
        return KatachiPluginFunctionalTest.gradleVersions();
    }

    @ParameterizedTest(name = "Gradle {0}")
    @MethodSource("gradleVersions")
    @DisplayName("katachi<Key> と katachiProcessors は、Gradle を動かす JVM より新しいモジュールの toolchain で動く")
    void processorTasksRunOnTheModuleToolchain(String gradleVersion, @TempDir Path projectDir) throws IOException {
        int newerJava = newerJava();
        writeSettingsAndProperties(projectDir, false);
        writeModule(projectDir, newerJava, null);

        run(projectDir, gradleVersion, ":katachiDocs");
        assertEquals(String.valueOf(newerJava), takeRecordedJava(projectDir));

        run(projectDir, gradleVersion, ":katachiProcessors", "--processor", "docs");
        assertEquals(String.valueOf(newerJava), takeRecordedJava(projectDir));
    }

    @ParameterizedTest(name = "Gradle {0}")
    @MethodSource("gradleVersions")
    @DisplayName("build script が javaLauncher を決めていれば、plugin より先に書かれていてもそちらで動く")
    void aJavaLauncherFromTheBuildScriptWins(String gradleVersion, @TempDir Path projectDir) throws IOException {
        int newerJava = newerJava();
        int gradleJava = Runtime.version().feature();
        writeSettingsAndProperties(projectDir, true);
        // The root build script runs before the subproject applies the plugin, so this action is
        // registered before the plugin's own and runs first on every task.
        write(
                projectDir.resolve("build.gradle.kts"),
                "subprojects {\n"
                        + "    tasks.withType<JavaExec>().configureEach {\n"
                        + "        javaLauncher.set(project.extensions.getByType<JavaToolchainService>().launcherFor {\n"
                        + "            languageVersion.set(JavaLanguageVersion.of(" + gradleJava + "))\n"
                        + "        })\n"
                        + "    }\n"
                        + "}\n");
        Path module = projectDir.resolve("architecture-test");
        writeModule(module, newerJava, gradleJava);

        run(projectDir, gradleVersion, ":architecture-test:katachiDocs");
        assertEquals(String.valueOf(gradleJava), takeRecordedJava(module));

        run(projectDir, gradleVersion, ":architecture-test:katachiProcessors", "--processor", "docs");
        assertEquals(String.valueOf(gradleJava), takeRecordedJava(module));
    }

    /**
     * The Java version of the JDK this module's build passes in, checked to be newer than the
     * JVM running this test, which is also the one the fixture's Gradle runs on.
     */
    private static int newerJava() {
        String configured = System.getProperty("katachi.testkit.newerJava");
        assertNotNull(configured, "katachi.testkit.newerJava is not set; run this through the `test` task.");
        int newerJava = Integer.parseInt(configured);
        int gradleJava = Runtime.version().feature();
        assertTrue(
                newerJava > gradleJava,
                "the fixture needs a toolchain newer than the Java " + gradleJava + " running Gradle, but "
                        + "katachi.testkit.newerJava is " + newerJava);
        return newerJava;
    }

    /**
     * Writes the settings, with {@code architecture-test} as a subproject when
     * {@code withSubproject}, and a {@code gradle.properties} that makes the newer JDK and the
     * JVM running this test the only ones Gradle knows of. Gradle 8.0 does not count the JVM it
     * runs on once auto-detection is off, so that one is listed too.
     */
    private static void writeSettingsAndProperties(Path projectDir, boolean withSubproject) throws IOException {
        write(
                projectDir.resolve("settings.gradle.kts"),
                "rootProject.name = \"katachi-toolchain-fixture\"\n"
                        + (withSubproject ? "include(\"architecture-test\")\n" : ""));
        String newerJavaHome = System.getProperty("katachi.testkit.newerJavaHome");
        assertNotNull(newerJavaHome, "katachi.testkit.newerJavaHome is not set; run this through the `test` task.");
        String installations = newerJavaHome + "," + System.getProperty("java.home");
        write(
                projectDir.resolve("gradle.properties"),
                "org.gradle.java.installations.paths=" + installations.replace("\\", "\\\\") + "\n"
                        + "org.gradle.java.installations.auto-detect=false\n"
                        + "org.gradle.java.installations.auto-download=false\n");
    }

    /**
     * Writes a module whose toolchain is {@code toolchain} and whose processor tasks start the
     * fixture's recording entry point.
     *
     * @param testRelease the Java the tests are compiled for, or {@code null} for the toolchain's.
     */
    private static void writeModule(Path moduleDir, int toolchain, Integer testRelease) throws IOException {
        write(
                moduleDir.resolve("build.gradle.kts"),
                "plugins {\n"
                        + "    java\n"
                        + "    id(\"" + PLUGIN_ID + "\")\n"
                        + "}\n"
                        + "\n"
                        + "java {\n"
                        + "    toolchain {\n"
                        + "        languageVersion.set(JavaLanguageVersion.of(" + toolchain + "))\n"
                        + "    }\n"
                        + "}\n"
                        + "\n"
                        + (testRelease == null
                                ? ""
                                : "tasks.named<JavaCompile>(\"compileTestJava\") {\n"
                                        + "    options.release.set(" + testRelease + ")\n"
                                        + "}\n"
                                        + "\n")
                        + "tasks.withType<JavaExec>().configureEach {\n"
                        + "    mainClass.set(\"Recorder\")\n"
                        + "}\n"
                        + "\n"
                        + "katachi {\n"
                        + "    architecture = \"fixture.ProjectArchitectureKt\"\n"
                        + "}\n");
        write(
                moduleDir.resolve("src/test/java/Recorder.java"),
                "public final class Recorder {\n"
                        + "    public static void main(String[] args) throws Exception {\n"
                        + "        java.nio.file.Path recorded = java.nio.file.Paths.get(\"build/recorded-java.txt\");\n"
                        + "        java.nio.file.Files.createDirectories(recorded.getParent());\n"
                        + "        java.nio.file.Files.write(recorded,\n"
                        + "                String.valueOf(Runtime.version().feature()).getBytes(\"UTF-8\"));\n"
                        + "    }\n"
                        + "}\n");
    }

    /** The Java version the fixture's entry point ran on, removing the record for the next run. */
    private static String takeRecordedJava(Path moduleDir) throws IOException {
        Path recorded = moduleDir.resolve("build/recorded-java.txt");
        assertTrue(Files.exists(recorded), "the fixture's entry point never ran");
        String java = new String(Files.readAllBytes(recorded), StandardCharsets.UTF_8).trim();
        Files.delete(recorded);
        return java;
    }

    private static void run(Path projectDir, String gradleVersion, String... arguments) {
        List<String> all = new ArrayList<>(Arrays.asList(arguments));
        all.add("--stacktrace");
        all.add("--configuration-cache");
        GradleRunner.create()
                .withProjectDir(projectDir.toFile())
                .withGradleVersion(gradleVersion)
                .withPluginClasspath()
                .forwardOutput()
                .withArguments(all)
                .build();
    }

    private static void write(Path path, String content) throws IOException {
        Files.createDirectories(path.getParent());
        Files.write(path, content.getBytes(StandardCharsets.UTF_8));
    }
}
