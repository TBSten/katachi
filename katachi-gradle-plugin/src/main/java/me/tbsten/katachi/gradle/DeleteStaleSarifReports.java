package me.tbsten.katachi.gradle;

import java.io.File;
import java.util.regex.Pattern;

import org.gradle.api.Action;
import org.gradle.api.Task;
import org.gradle.api.provider.Provider;

/**
 * Deletes the SARIF report an earlier run left behind, as the first action of a {@code Test} task
 * while {@code katachi { report { sarif = true } }} is on.
 *
 * <p>The test JVM writes the file only when {@code assert()} runs. In a run where it does not --
 * a {@code --tests} filter that leaves the architecture test out, a failure before it, a test
 * that throws before {@code assert()} -- the previous file would otherwise stay where a CI step
 * such as {@code upload-sarif} finds it and uploads an older run's violations as this one's.
 * The numbered siblings ({@code katachi-2.sarif}) a second {@code assert()} of the same JVM
 * writes go with it. With the report off nothing is touched.
 *
 * <p>Holds two providers and nothing else, so the configuration cache can store it.
 *
 * <h2>Example 1: what the plugin adds to every Test task</h2>
 *
 * <pre>{@code
 * test.doFirst(new DeleteStaleSarifReports(sarif, sarifFile));
 * }</pre>
 */
final class DeleteStaleSarifReports implements Action<Task> {

    /** {@code katachi-2.sarif}, {@code katachi-3.sarif}, ... next to {@code katachi.sarif}. */
    private static final Pattern NUMBERED = Pattern.compile("katachi-\\d+\\.sarif");

    private final Provider<Boolean> sarif;
    private final Provider<File> sarifFile;

    /**
     * @param sarif whether the report is on, read when the task runs.
     * @param sarifFile the file the test JVM writes.
     */
    DeleteStaleSarifReports(Provider<Boolean> sarif, Provider<File> sarifFile) {
        this.sarif = sarif;
        this.sarifFile = sarifFile;
    }

    /** {@inheritDoc} */
    @Override
    public void execute(Task task) {
        if (!sarif.get()) {
            return;
        }
        File file = sarifFile.get();
        file.delete();
        File[] siblings = file.getParentFile() == null ? null : file.getParentFile().listFiles();
        if (siblings == null) {
            return;
        }
        for (File sibling : siblings) {
            if (NUMBERED.matcher(sibling.getName()).matches()) {
                sibling.delete();
            }
        }
    }
}
