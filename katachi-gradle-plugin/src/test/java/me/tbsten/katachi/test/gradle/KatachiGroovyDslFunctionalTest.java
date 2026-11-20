package me.tbsten.katachi.test.gradle;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.gradle.testkit.runner.BuildResult;
import org.gradle.testkit.runner.GradleRunner;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * Drives a build whose {@code build.gradle} is Groovy, on every Gradle version katachi claims to
 * support, and reads back what each block inside {@code katachi { processors { } } } configured.
 *
 * <p>Groovy is where a block can miss its object without the Kotlin DSL noticing. A Kotlin block
 * takes an {@code Action}'s argument as its receiver whatever made the object; a Groovy closure
 * runs against the block only when Gradle made the object whose method it is given to, and
 * otherwise looks its assignments up on the enclosing {@code KatachiProcessors}, which fails as
 * an unknown property ({@code KatachiExtension} says more). So every block here is written the way
 * the Kotlin examples write it, without {@code it.}.
 */
class KatachiGroovyDslFunctionalTest {

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
    @DisplayName("build.gradle（Groovy）でも docs { } template { } diff { } register(...) { } args(...) { } outputs(...) { } はそれぞれのブロックに届く")
    void everyBlockReachesItsOwnObject(String gradleVersion, @TempDir Path projectDir) throws IOException {
        writeFixture(projectDir);

        BuildResult result = runner(projectDir, gradleVersion).build();

        assertEquals("outputDir=build/our-docs", single(result, "katachi.args.docs="));
        assertEquals("onExisting=skip,template=Domain.UseCase", single(result, "katachi.args.template="));
        assertEquals("before=HEAD~1,markdown=false", single(result, "katachi.args.diff="));
        assertEquals("prefix=domain,suffix=UseCase", single(result, "katachi.args.names="));
        assertEquals("format=json", single(result, "katachi.args.templates="));
        assertEquals("false", single(result, "katachi.names.readsProjectFiles="));
        assertEquals("build/katachi/names", single(result, "katachi.names.declaredOutputDir="));
    }

    private static void writeFixture(Path projectDir) throws IOException {
        write(projectDir.resolve("settings.gradle"), "rootProject.name = 'katachi-groovy-fixture'\n");
        write(
                projectDir.resolve("build.gradle"),
                "import me.tbsten.katachi.gradle.KatachiOnExisting\n"
                        + "\n"
                        + "plugins {\n"
                        + "    id 'java'\n"
                        + "    id 'me.tbsten.katachi'\n"
                        + "}\n"
                        + "\n"
                        + "katachi {\n"
                        + "    architecture = 'fixture.ProjectArchitectureKt'\n"
                        + "    processors {\n"
                        + "        docs {\n"
                        + "            outputDir = 'build/our-docs'\n"
                        + "        }\n"
                        + "        template {\n"
                        + "            onExisting = KatachiOnExisting.SKIP\n"
                        + "            template = ['Domain.UseCase']\n"
                        + "        }\n"
                        + "        diff {\n"
                        + "            before = 'HEAD~1'\n"
                        + "            markdown {\n"
                        + "                enable = false\n"
                        + "            }\n"
                        + "        }\n"
                        + "        register('names', 'com.example.Names') {\n"
                        + "            arg('prefix', 'domain')\n"
                        + "            // The way around it before the blocks were made by Gradle keeps working.\n"
                        + "            it.arg('suffix', 'UseCase')\n"
                        + "        }\n"
                        + "        args('templates') {\n"
                        + "            arg('format', 'json')\n"
                        + "        }\n"
                        + "        outputs('names') {\n"
                        + "            readsProjectFiles = false\n"
                        + "            outputDir = 'build/katachi/names'\n"
                        + "        }\n"
                        + "    }\n"
                        + "}\n"
                        + "\n"
                        + "tasks.register('reportKatachiDsl') {\n"
                        + "    // Read into plain strings at configuration time: the action then holds\n"
                        + "    // nothing the configuration cache could not store.\n"
                        + "    def lines = []\n"
                        + "    [docs: 'katachiDocs', template: 'katachiTemplate', diff: 'katachiDiff',\n"
                        + "            names: 'katachiNames', templates: 'katachiTemplates'].each { key, taskName ->\n"
                        + "        def configured = tasks.named(taskName).get().configuredArgs.get()\n"
                        + "        lines << 'katachi.args.' + key + '=' + configured.keySet().sort().collect { it + '=' + configured[it] }.join(',')\n"
                        + "    }\n"
                        + "    def names = tasks.named('katachiNames').get()\n"
                        + "    lines << 'katachi.names.readsProjectFiles=' + names.readsProjectFiles.get()\n"
                        + "    lines << 'katachi.names.declaredOutputDir=' + names.declaredOutputDir.get()\n"
                        + "    doLast {\n"
                        + "        println lines.join('\\n')\n"
                        + "    }\n"
                        + "}\n");
    }

    private static void write(Path path, String content) throws IOException {
        Files.createDirectories(path.getParent());
        Files.write(path, content.getBytes(StandardCharsets.UTF_8));
    }

    private static GradleRunner runner(Path projectDir, String gradleVersion) {
        return GradleRunner.create()
                .withProjectDir(projectDir.toFile())
                .withGradleVersion(gradleVersion)
                .withPluginClasspath()
                .forwardOutput()
                .withArguments("reportKatachiDsl", "--stacktrace", "--configuration-cache");
    }

    private static String single(BuildResult result, String prefix) {
        List<String> values = Arrays.stream(result.getOutput().split("\\R"))
                .map(String::trim)
                .filter(line -> line.startsWith(prefix))
                .map(line -> line.substring(prefix.length()))
                .collect(Collectors.toList());
        assertEquals(1, values.size(), "expected exactly one line starting with " + prefix + ", got " + values);
        return values.get(0);
    }
}
