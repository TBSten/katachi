package me.tbsten.katachi.gradle;

import java.io.File;
import java.util.Map;
import org.gradle.api.InvalidUserDataException;
import org.gradle.api.file.RegularFile;

/**
 * One format of the report {@code katachiDiff} writes: whether it is written, and where.
 *
 * <p>Both formats are written by default. Turning both off fails the run, since it would write
 * nothing.
 *
 * <pre>{@code
 * katachi {
 *     processors {
 *         diff {
 *             markdown {
 *                 outputFile = layout.projectDirectory.file("diff-report.md")
 *             }
 *             html {
 *                 enable = false
 *             }
 *         }
 *     }
 * }
 * }</pre>
 *
 * @see KatachiDiffOptions
 */
public class KatachiDiffOutputOptions {

    /** {@code markdown} or {@code html}: the block name, and the prefix of its {@code --arg}s. */
    private final String format;

    private Boolean enable;
    private String outputFile;

    KatachiDiffOutputOptions(String format) {
        this.format = format;
    }

    /** Whether this format is written. */
    public Boolean getEnable() {
        return enable;
    }

    /**
     * Sets whether this format is written. Left unset, it is.
     *
     * @throws InvalidUserDataException when given {@code null}.
     */
    public void setEnable(Boolean enable) {
        if (enable == null) {
            throw new InvalidUserDataException(
                    "katachi { processors { diff { " + format + " { enable = ... } } } } was given no "
                            + "value. Leave it out entirely to write the " + format + " report.");
        }
        this.enable = enable;
    }

    /** Where this format is written, as the command line will carry it. */
    public Object getOutputFile() {
        return outputFile;
    }

    /**
     * Sets where this format is written.
     *
     * <p>Takes a {@link RegularFile} such as {@code layout.projectDirectory.file("diff-report.md")} or
     * a {@link File}, carried as its absolute path, or a {@code String} relative to this module's
     * directory. Left unset, the processor's own default applies, which is
     * {@code build/katachi/diff/diff-report.md} or {@code .html}.
     *
     * @throws InvalidUserDataException when the value is not a file, or when a relative one climbs
     *     out of the module with {@code ".."}.
     */
    public void setOutputFile(Object outputFile) {
        if (outputFile instanceof RegularFile) {
            this.outputFile = ((RegularFile) outputFile).getAsFile().getAbsolutePath();
            return;
        }
        if (outputFile instanceof File) {
            this.outputFile = ((File) outputFile).getAbsolutePath();
            return;
        }
        if (outputFile instanceof CharSequence && outputFile.toString().length() > 0) {
            String path = outputFile.toString();
            for (String segment : path.split("[/\\\\]")) {
                if ("..".equals(segment)) {
                    throw new InvalidUserDataException(
                            "katachi { processors { diff { " + format + " { outputFile = \"" + path
                                    + "\" } } } } climbs out of this module with \"..\". Write a path "
                                    + "under the module, or hand it a RegularFile: outputFile = "
                                    + "rootProject.layout.projectDirectory.file(\"diff-report." + extension()
                                    + "\").");
                }
            }
            this.outputFile = path;
            return;
        }
        throw new InvalidUserDataException(
                "katachi { processors { diff { " + format + " { outputFile = ... } } } } was given "
                        + (outputFile == null ? "no value" : "a " + outputFile.getClass().getName())
                        + ". Write a RegularFile, a File or a String relative to this module, e.g. "
                        + "outputFile = layout.projectDirectory.file(\"diff-report." + extension()
                        + "\"), or leave it out entirely to keep the default.");
    }

    /** Adds what was set to {@code args}, as {@code <format>=} and {@code <format>OutputFile=}. */
    void putArgs(Map<String, String> args) {
        if (enable != null) {
            args.put(format, enable.toString());
        }
        if (outputFile != null) {
            args.put(format + "OutputFile", outputFile);
        }
    }

    private String extension() {
        return "markdown".equals(format) ? "md" : format;
    }
}
