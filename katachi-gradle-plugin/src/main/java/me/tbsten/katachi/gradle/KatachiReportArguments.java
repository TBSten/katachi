package me.tbsten.katachi.gradle;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import org.gradle.api.file.FileCollection;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.Internal;
import org.gradle.process.CommandLineArgumentProvider;

/**
 * Hands {@code -Dkatachi.report.sarif} and {@code -Dkatachi.report.sourceRoots} to the JVM of a
 * {@code Test} task, where katachi's {@code assert()} reads them, while
 * {@code katachi { report { sarif = true } }} is on.
 *
 * <p>{@code sarif} is an {@link Input}, so turning the report on or off runs the test again. The
 * file path and the source roots are {@link Internal}: the path follows from the task name and
 * the build directory, and the test sources are already inputs of the task. With {@code sarif}
 * off nothing is passed, so a test JVM sees exactly what it would have seen without the plugin.
 *
 * <h2>Example 1: what the plugin does for every Test task</h2>
 *
 * <pre>{@code
 * // katachi { report { sarif = true } }
 * // the test JVM starts with
 * //   -Dkatachi.report.sarif=/path/to/build/reports/katachi/katachi.sarif
 * //   -Dkatachi.report.sourceRoots=/path/to/src/test/kotlin:/path/to/src/test/java
 * }</pre>
 */
public final class KatachiReportArguments implements CommandLineArgumentProvider {

    /** The property that holds the path of the SARIF file to write. */
    static final String SARIF_PROPERTY = "katachi.report.sarif";

    /** The property that holds the directories declaration sites are looked up in. */
    static final String SOURCE_ROOTS_PROPERTY = "katachi.report.sourceRoots";

    private final Provider<Boolean> sarif;
    private final Provider<File> sarifFile;
    private final FileCollection sourceRoots;

    /**
     * @param sarif whether the report is on, read when the task runs.
     * @param sarifFile the file the test JVM writes.
     * @param sourceRoots the source directories of the tests; those that do not exist are left out.
     */
    KatachiReportArguments(Provider<Boolean> sarif, Provider<File> sarifFile, FileCollection sourceRoots) {
        this.sarif = sarif;
        this.sarifFile = sarifFile;
        this.sourceRoots = sourceRoots;
    }

    /**
     * Whether the report is on.
     *
     * @return the value of {@code report { sarif }}.
     */
    @Input
    public Provider<Boolean> getSarif() {
        return sarif;
    }

    /**
     * The file the test JVM writes, whether or not the report is on.
     *
     * @return the absolute file.
     */
    @Internal
    public Provider<File> getSarifFile() {
        return sarifFile;
    }

    /**
     * The test source directories handed to the test JVM.
     *
     * @return the directories, existing or not.
     */
    @Internal
    public FileCollection getSourceRoots() {
        return sourceRoots;
    }

    /** {@inheritDoc} */
    @Override
    public Iterable<String> asArguments() {
        List<String> arguments = new ArrayList<>();
        if (!sarif.get()) {
            return arguments;
        }
        arguments.add("-D" + SARIF_PROPERTY + "=" + sarifFile.get().getAbsolutePath());
        StringBuilder roots = new StringBuilder();
        for (File root : sourceRoots.getFiles()) {
            if (!root.isDirectory()) {
                continue;
            }
            if (roots.length() > 0) {
                roots.append(File.pathSeparatorChar);
            }
            roots.append(root.getAbsolutePath());
        }
        // Left out when empty, so the test JVM falls back to looking under src/ itself.
        if (roots.length() > 0) {
            arguments.add("-D" + SOURCE_ROOTS_PROPERTY + "=" + roots);
        }
        return arguments;
    }
}
