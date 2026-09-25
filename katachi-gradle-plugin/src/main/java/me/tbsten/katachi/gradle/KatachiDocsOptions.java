package me.tbsten.katachi.gradle;

import java.io.File;
import java.util.LinkedHashMap;
import java.util.Map;
import org.gradle.api.InvalidUserDataException;
import org.gradle.api.file.Directory;

/**
 * What {@code --processor=docs} is given every run, written in {@code build.gradle.kts}.
 *
 * <p>Typed rather than a string map, because these arguments are katachi's own and their spelling
 * is part of its public surface: a typo in {@code outputDir} should be a red squiggle in the IDE,
 * not a message at the end of a Gradle run. Everything a project writes itself keeps going through
 * {@code processors { args("key") { } }}, which stays strings on both sides.
 *
 * <p>A {@code --arg} of the same name on the command line wins, so what is written here is the
 * project's default rather than a lock.
 *
 * <pre>{@code
 * katachi {
 *     architecture = "com.example.projectArchitecture"
 *     docs {
 *         outputDir = "docs/architecture"
 *         rootTitle = "myapp"
 *         rootDescription = "このリポジトリの構成。"
 *     }
 * }
 * }</pre>
 */
public class KatachiDocsOptions {

    /** The registry key these arguments are for. Not configurable: it is katachi's own. */
    static final String KEY = "docs";

    private String outputDir;
    private KatachiDocsMode mode;
    private String rootTitle;
    private String rootDescription;
    private String rootTitleConvention;

    /** Where the pages go, as the command line will carry it. */
    public Object getOutputDir() {
        return outputDir;
    }

    /**
     * Sets where the pages go.
     *
     * <p>Takes what Gradle takes for a path elsewhere: a {@code String} relative to this module's
     * directory, or a {@link Directory} / {@link File}, which are carried as their absolute path
     * so that {@code rootProject.layout.projectDirectory.dir("docs")} lands where it reads.
     *
     * <p>Left unset, the processor's own default applies, which is {@code build/katachi/docs}.
     *
     * <p><strong>The output directory becomes katachi's.</strong> A {@code *.md} this run did not
     * produce is deleted from it, so pointing this at a directory holding handwritten Markdown
     * loses that Markdown. Files that are not {@code *.md} are left alone. Pointing it outside
     * {@code build/} also puts the generated pages in front of {@code gitTracked()}, which
     * reports a file no role declares -- declare them in a role's {@code layout { }}, or keep the
     * output under {@code build/}.
     *
     * @throws InvalidUserDataException when the value is not a path, or when a relative one
     *     climbs out of the module with {@code ".."}.
     */
    public void setOutputDir(Object outputDir) {
        if (outputDir == null) {
            throw new InvalidUserDataException(
                    "katachi { docs { outputDir = ... } } was given no value. Leave it out "
                            + "entirely to keep the default, build/katachi/docs.");
        }
        if (outputDir instanceof Directory) {
            this.outputDir = ((Directory) outputDir).getAsFile().getAbsolutePath();
            return;
        }
        if (outputDir instanceof File) {
            this.outputDir = ((File) outputDir).getAbsolutePath();
            return;
        }
        if (outputDir instanceof CharSequence) {
            String path = outputDir.toString();
            if (path.isEmpty()) {
                throw new InvalidUserDataException(
                        "katachi { docs { outputDir = \"\" } } is empty. Leave it out entirely "
                                + "to keep the default, build/katachi/docs.");
            }
            for (String segment : path.split("[/\\\\]")) {
                if ("..".equals(segment)) {
                    throw new InvalidUserDataException(
                            "katachi { docs { outputDir = \"" + path + "\" } } climbs out of "
                                    + "this module with \"..\". Write a path under the module, "
                                    + "or hand it a Directory: "
                                    + "outputDir = rootProject.layout.projectDirectory.dir(\"docs\").");
                }
            }
            this.outputDir = path;
            return;
        }
        throw new InvalidUserDataException(
                "katachi { docs { outputDir = ... } } was given a "
                        + outputDir.getClass().getName()
                        + ". Write a String relative to this module, or a Directory / File, e.g. "
                        + "outputDir = \"build/katachi/docs\" or "
                        + "outputDir = rootProject.layout.projectDirectory.dir(\"docs\").");
    }

    /** Whether a run writes the pages or only compares them against what is on disk. */
    public KatachiDocsMode getMode() {
        return mode;
    }

    /**
     * Sets whether a run writes the pages or only compares them.
     *
     * <p>{@link KatachiDocsMode#CHECK} writes nothing and fails the task when the pages on disk
     * are not what this definition produces, which is what a CI step wants. Left unset, the
     * processor's own default applies, which is {@link KatachiDocsMode#WRITE}.
     */
    public void setMode(KatachiDocsMode mode) {
        if (mode == null) {
            throw new InvalidUserDataException(
                    "katachi { docs { mode = ... } } was given no value. Leave it out entirely "
                            + "to keep the default, WRITE.");
        }
        this.mode = mode;
    }

    /** What the generated root page calls itself. */
    public String getRootTitle() {
        return rootTitle;
    }

    /**
     * Sets what the generated root page calls itself: its heading becomes this followed by
     * {@code " ドキュメント"}.
     *
     * <p>Left unset, the name of the root project is sent instead -- the name the build already
     * spells this repository with, and a value only Gradle knows, which is why the plugin fills it
     * in rather than the processor defaulting to it. A definition that wrote a {@code title} on
     * {@code architecture { } } itself is overridden by both: what a run can change wins over what
     * a definition fixed.
     */
    public void setRootTitle(String rootTitle) {
        if (rootTitle == null || rootTitle.isEmpty()) {
            throw new InvalidUserDataException(
                    "katachi { docs { rootTitle = ... } } was given no value. Leave it out "
                            + "entirely to keep the default, the root project's name.");
        }
        this.rootTitle = rootTitle;
    }

    /** The one paragraph written under the root page's heading. */
    public String getRootDescription() {
        return rootDescription;
    }

    /**
     * Sets the one paragraph written under the root page's heading, as Markdown.
     *
     * <p>Left unset, the {@code description} the definition wrote on {@code architecture { } } is
     * used, and when there is none either, the heading is followed straight by the document map.
     */
    public void setRootDescription(String rootDescription) {
        if (rootDescription == null || rootDescription.isEmpty()) {
            throw new InvalidUserDataException(
                    "katachi { docs { rootDescription = ... } } was given no value. Leave it out "
                            + "entirely to write no paragraph under the heading.");
        }
        this.rootDescription = rootDescription;
    }

    /**
     * The name to send as {@code rootTitle} when this block did not set one.
     *
     * <p>Package private and written by {@link KatachiPlugin}: it is the root project's name,
     * which no other layer can see.
     */
    void setRootTitleConvention(String rootTitleConvention) {
        this.rootTitleConvention = rootTitleConvention;
    }

    /**
     * These options as the {@code --arg} values they become.
     *
     * <p>Only what was set. An option left alone is absent rather than sent as its default, so the
     * default lives in the processor and is not copied here where the two could drift apart.
     */
    Map<String, String> toArgs() {
        Map<String, String> args = new LinkedHashMap<>();
        if (outputDir != null) {
            args.put("outputDir", outputDir);
        }
        if (mode != null) {
            args.put("mode", mode.wireName());
        }
        if (rootTitle != null) {
            args.put("rootTitle", rootTitle);
        }
        if (rootDescription != null) {
            args.put("rootDescription", rootDescription);
        }
        return args;
    }

    /**
     * What this block sends although nobody wrote it: the values the processor cannot default to
     * by itself.
     *
     * <p>Kept apart from {@link #toArgs()} so that an untouched {@code docs { } } block still
     * counts as untouched -- writing {@code processors { args("docs") { } } } instead of this
     * block is allowed, and a convention is not what that check is about.
     */
    Map<String, String> toConventionArgs() {
        Map<String, String> args = new LinkedHashMap<>();
        if (rootTitle == null && rootTitleConvention != null && !rootTitleConvention.isEmpty()) {
            args.put("rootTitle", rootTitleConvention);
        }
        return args;
    }
}
