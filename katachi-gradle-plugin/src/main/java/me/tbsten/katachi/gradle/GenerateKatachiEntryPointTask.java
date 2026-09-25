package me.tbsten.katachi.gradle;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import org.gradle.api.DefaultTask;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.provider.MapProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.Optional;
import org.gradle.api.tasks.OutputDirectory;
import org.gradle.api.tasks.TaskAction;
import org.gradle.work.DisableCachingByDefault;

/**
 * Writes {@code GeneratedKatachiEntryPoint.kt}, the sole bridge between a module's
 * {@code katachi { } } block and the running processor: see
 * {@code me.tbsten.katachi.processor.internal.KatachiEntryPoint} on the {@code :katachi} side.
 *
 * <p><strong>Does not fail when {@link #getArchitectureClassName()} is absent.</strong> This
 * task sits upstream of {@code compileTestKotlin} (see {@link KatachiPlugin}), so failing here
 * would fail {@code check} the moment the plugin is applied, before a user has had any reason to
 * set {@code architecture} at all -- forgetting it is not that expensive a mistake. Instead this
 * task quietly writes nothing, and {@link RunKatachiProcessorTask} is the one that fails, with a
 * message pointing at {@code katachi { architecture = ... } }, and only when a user actually
 * asks to run a processor.
 */
@DisableCachingByDefault(
        because =
                "Writing a few lines of Kotlin from two declared inputs is cheaper than a"
                        + " build cache round trip. `java-gradle-plugin` runs `validatePlugins`"
                        + " with stricter validation, which demands that a task say which of the"
                        + " two it is rather than leave it unstated.")
public abstract class GenerateKatachiEntryPointTask extends DefaultTask {

    /**
     * The fully qualified name of the top level {@code val Architecture} to generate an
     * {@code import} for, or absent when {@code katachi { architecture = ... } } was never set.
     */
    @Input
    @Optional
    public abstract Property<String> getArchitectureClassName();

    /** Every registered processor, key to fully qualified class name. */
    @Input
    public abstract MapProperty<String, String> getProcessors();

    /** Where {@code GeneratedKatachiEntryPoint.kt} is written. */
    @OutputDirectory
    public abstract DirectoryProperty getOutputDirectory();

    @TaskAction
    void generate() throws IOException {
        File outputDirectory = getOutputDirectory().get().getAsFile();
        // Cleared on every run: a stale entry point from a since-removed `architecture = ...`
        // must not linger and be picked up by a later, unrelated run.
        deleteRecursively(outputDirectory);

        if (!getArchitectureClassName().isPresent()) {
            return;
        }

        String source = KatachiEntryPointSource.render(
                getArchitectureClassName().get(),
                getProcessors().get());

        File outputFile = new File(outputDirectory, KatachiEntryPointSource.RELATIVE_PATH);
        Files.createDirectories(outputFile.getParentFile().toPath());
        // UTF-8 explicitly, the same reason `compileJava`'s `options.encoding` is set: this
        // file's content (an architecture's fully qualified name) may not stay ASCII, and a
        // platform default charset would make the generated source depend on the machine that
        // ran this task.
        Files.write(outputFile.toPath(), source.getBytes(StandardCharsets.UTF_8));
    }

    private static void deleteRecursively(File file) throws IOException {
        if (!file.exists()) {
            return;
        }
        File[] children = file.listFiles();
        if (children != null) {
            for (File child : children) {
                deleteRecursively(child);
            }
        }
        Files.delete(file.toPath());
    }
}
