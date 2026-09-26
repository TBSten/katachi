package me.tbsten.katachi.gradle;

import java.io.File;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import org.gradle.api.InvalidUserDataException;
import org.gradle.api.file.Directory;

/**
 * What one processor reads and writes, declared so that Gradle may skip its task when nothing it
 * depends on changed, or restore its output from the build cache.
 *
 * <p>A processor task runs every time unless it is declared here with
 * {@code readsProjectFiles = false} and an {@code outputDir}. Both are promises only the
 * processor's author can make, and the plugin cannot check either:
 *
 * <ul>
 *   <li>{@code readsProjectFiles = false} promises that the processor reads the definition and
 *       its arguments and nothing else -- no {@code filesOf}, no file of the project. Gradle then
 *       knows every input (the test runtime classpath and the arguments) and may call the task
 *       up to date. A processor that walks the project must never say this: its input is every
 *       file of the project, which Gradle is not told about, so an up-to-date answer would be a
 *       result for files nobody looked at.
 *   <li>{@code outputDir} is the one directory the processor writes. Gradle watches it, and
 *       restores it from the build cache. A file written anywhere else is neither watched nor
 *       restored.
 * </ul>
 *
 * <p>{@code docs} is declared this way already when it is katachi's own documentation processor:
 * it writes to its {@code outputDir} argument, and under {@code mode=check} it writes nothing and
 * runs every time. Registering a processor of one's own under {@code docs} drops that
 * declaration, since katachi cannot know what the replacement reads.
 *
 * <p>The output directory becomes Gradle's to restore, which means Gradle may replace its whole
 * contents from the build cache. Keep it a directory only this processor writes to; one shared
 * with other files is detected by Gradle as overlapping outputs, and is then not cached.
 *
 * <h2>Example 1: let Gradle skip a processor that only reads declarations</h2>
 *
 * <pre>{@code
 * katachi {
 *     processors {
 *         register("roleNames", "com.example.processors.RoleNames")
 *         outputs("roleNames") {
 *             readsProjectFiles = false
 *             outputDir = "build/katachi/roleNames"
 *         }
 *     }
 * }
 * }</pre>
 *
 * @see KatachiProcessors#outputs(String, org.gradle.api.Action)
 * @see KatachiProcessorTask
 */
public class KatachiProcessorOutputs {

    private final String key;
    private Boolean readsProjectFiles;
    private String outputDir;
    private String outputDirArg;
    private final Map<String, String> writesNothingWhen = new LinkedHashMap<>();

    KatachiProcessorOutputs(String key) {
        this.key = key;
    }

    /** Whether the processor reads files of the project, or {@code null} when not declared. */
    public Boolean getReadsProjectFiles() {
        return readsProjectFiles;
    }

    /**
     * Declares whether the processor reads files of the project.
     *
     * <p>Left undeclared, a processor is assumed to read them, and its task runs every time.
     *
     * @throws InvalidUserDataException when given {@code null}.
     */
    public void setReadsProjectFiles(Boolean readsProjectFiles) {
        if (readsProjectFiles == null) {
            throw new InvalidUserDataException(
                    "katachi { processors { outputs(\"" + key + "\") { readsProjectFiles = ... } } } "
                            + "was given no value. Write true or false, or leave it out: an "
                            + "undeclared processor is assumed to read the project.");
        }
        this.readsProjectFiles = readsProjectFiles;
    }

    /** The directory the processor writes, as the task will resolve it, or {@code null}. */
    public Object getOutputDir() {
        return outputDir;
    }

    /**
     * Declares the one directory the processor writes.
     *
     * <p>Takes a {@code String} relative to this module's directory -- the directory the
     * processor is started in -- or a {@link Directory} / {@link File}, which are carried as
     * their absolute path.
     *
     * @throws InvalidUserDataException when the value is not a path, is empty, or is relative and
     *     climbs out of the module with {@code ".."}.
     */
    public void setOutputDir(Object outputDir) {
        String where = "katachi { processors { outputs(\"" + key + "\") { outputDir = ... } } }";
        if (outputDir instanceof Directory) {
            this.outputDir = ((Directory) outputDir).getAsFile().getAbsolutePath();
            return;
        }
        if (outputDir instanceof File) {
            this.outputDir = ((File) outputDir).getAbsolutePath();
            return;
        }
        if (outputDir instanceof CharSequence && outputDir.toString().length() > 0) {
            String path = outputDir.toString();
            for (String segment : path.split("[/\\\\]")) {
                if ("..".equals(segment)) {
                    throw new InvalidUserDataException(
                            where + " was given \"" + path + "\", which climbs out of this module "
                                    + "with \"..\". Write a path under the module, or hand it a "
                                    + "Directory.");
                }
            }
            this.outputDir = path;
            return;
        }
        throw new InvalidUserDataException(
                where + " was given " + (outputDir == null ? "no value" : "a " + outputDir.getClass().getName())
                        + ". Write a non-empty String relative to this module, or a Directory / "
                        + "File, e.g. outputDir = \"build/katachi/" + key + "\".");
    }

    /** The argument that moves the output directory, or {@code null}. */
    public String getOutputDirArg() {
        return outputDirArg;
    }

    /**
     * Names the processor argument that, when given, replaces {@link #getOutputDir()}.
     *
     * <p>For a processor that takes its output directory as an argument, as {@code docs} does
     * with {@code outputDir}: a {@code --arg outputDir=...} then moves what Gradle watches along
     * with what the processor writes.
     */
    public void setOutputDirArg(String outputDirArg) {
        this.outputDirArg = outputDirArg;
    }

    /** Argument values under which the processor writes nothing. */
    Map<String, String> getWritesNothingWhen() {
        return Collections.unmodifiableMap(new LinkedHashMap<>(writesNothingWhen));
    }

    /** Records an argument value under which the processor writes nothing at all. */
    void writesNothingWhen(String argName, String value) {
        writesNothingWhen.put(argName, value);
    }

    /** {@code declared} laid over this object: every value {@code declared} set wins. */
    KatachiProcessorOutputs overriddenBy(KatachiProcessorOutputs declared) {
        KatachiProcessorOutputs merged = new KatachiProcessorOutputs(key);
        merged.readsProjectFiles =
                declared.readsProjectFiles != null ? declared.readsProjectFiles : readsProjectFiles;
        merged.outputDir = declared.outputDir != null ? declared.outputDir : outputDir;
        merged.outputDirArg = declared.outputDirArg != null ? declared.outputDirArg : outputDirArg;
        merged.writesNothingWhen.putAll(writesNothingWhen);
        merged.writesNothingWhen.putAll(declared.writesNothingWhen);
        return merged;
    }
}
