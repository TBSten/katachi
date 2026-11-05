package me.tbsten.katachi.gradle;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.gradle.api.Action;
import org.gradle.api.InvalidUserDataException;

/**
 * What {@code katachiDiff} is given every run, written in {@code build.gradle.kts}.
 *
 * <p>Typed, for the reason {@link KatachiDocsOptions} is: these argument names are katachi's own
 * and part of its public surface, so a typo in {@code maxFiles} belongs in the IDE rather than at
 * the end of a run.
 *
 * <p>A {@code --arg} of the same name on the command line wins, so what is written here is the
 * project's default rather than a lock: a module can compare against {@code origin/main} by default
 * and still be run with {@code --arg pr=1234} for one pull request.
 *
 * <pre>{@code
 * katachi {
 *     architecture = "com.example.projectArchitecture"
 *     processors {
 *         diff {
 *             before = "origin/main"
 *             weights = listOf("Domain=2", "Presentation.ViewModel=0.5")
 *             maxFiles = 50
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
 * @see KatachiDiffOutputOptions
 */
public class KatachiDiffOptions {

    /** The registry key these arguments are for. Not configurable: it is katachi's own. */
    static final String KEY = "diff";

    private String before;
    private String after;
    private String pr;
    private List<String> weights;
    private final KatachiDiffOutputOptions markdown = new KatachiDiffOutputOptions("markdown");
    private final KatachiDiffOutputOptions html = new KatachiDiffOutputOptions("html");
    private Integer maxFiles;

    /** The ref the change starts from. */
    public String getBefore() {
        return before;
    }

    /**
     * Sets the ref the change starts from, such as {@code "origin/main"}. Ignored when a pull
     * request is given. Left unset, the processor's own default applies, which is {@code HEAD}.
     *
     * @throws InvalidUserDataException when given {@code null} or an empty string.
     */
    public void setBefore(String before) {
        this.before = requireText("before", before);
    }

    /** The ref the change ends at. */
    public String getAfter() {
        return after;
    }

    /**
     * Sets the ref the change ends at, such as {@code "HEAD"}. Left unset, the change ends at the
     * working tree, untracked files included.
     *
     * @throws InvalidUserDataException when given {@code null} or an empty string.
     */
    public void setAfter(String after) {
        this.after = requireText("after", after);
    }

    /** The pull request compared, as a number or URL. */
    public String getPr() {
        return pr;
    }

    /**
     * Sets the pull request compared, as a number or URL, resolved with {@code gh pr view}. Wins
     * over {@link #setBefore before} and {@link #setAfter after}. Usually given per run as
     * {@code --arg pr=1234} rather than written here.
     *
     * @throws InvalidUserDataException when given {@code null} or an empty string.
     */
    public void setPr(String pr) {
        this.pr = requireText("pr", pr);
    }

    /** The {@code <qualified group or role name>=<number>} entries that scale the heat. */
    public List<String> getWeights() {
        return weights;
    }

    /**
     * Sets the {@code <qualified group or role name>=<number>} entries that scale the heat of what
     * they name. The most specific entry applies: a role's own over its group's. A name the
     * definition does not have fails the run, listing the names it does have.
     *
     * @throws InvalidUserDataException when given {@code null}, or an entry without {@code =} or
     *     holding a comma, which is how the entries are separated on the command line.
     */
    public void setWeights(List<String> weights) {
        if (weights == null) {
            throw new InvalidUserDataException(
                    "katachi { processors { diff { weights = ... } } } was given no value. Leave it "
                            + "out entirely to weigh every role 1.");
        }
        for (String entry : weights) {
            if (entry == null || entry.indexOf('=') <= 0 || entry.contains(",")) {
                throw new InvalidUserDataException(
                        "Invalid katachi { processors { diff { weights = ... } } } entry \"" + entry
                                + "\". Write one \"<qualified group or role name>=<number>\" per "
                                + "entry, e.g. listOf(\"Domain=2\", \"Presentation.ViewModel=0.5\").");
            }
        }
        this.weights = new ArrayList<>(weights);
    }

    /** The Markdown report: whether it is written, and where. See {@link #markdown(Action)}. */
    public KatachiDiffOutputOptions getMarkdown() {
        return markdown;
    }

    /**
     * Configures the Markdown report.
     *
     * <p>The file listings go next to {@code outputFile}, named after it: {@code diff-report.md}
     * puts them in {@code diff-report.files-by-role.md} and {@code diff-report.files-in-no-role.md}.
     */
    public void markdown(Action<? super KatachiDiffOutputOptions> action) {
        action.execute(markdown);
    }

    /** The HTML report: whether it is written, and where. See {@link #html(Action)}. */
    public KatachiDiffOutputOptions getHtml() {
        return html;
    }

    /** Configures the HTML report. */
    public void html(Action<? super KatachiDiffOutputOptions> action) {
        action.execute(html);
    }

    /** How many changed files the per-file listings show at most. */
    public Integer getMaxFiles() {
        return maxFiles;
    }

    /**
     * Sets how many changed files the per-file listings show at most; 0 or less shows them all.
     * Only the listings are cut: the group and role rows still count every file. Left unset, the
     * processor's own default applies, which is 100.
     *
     * @throws InvalidUserDataException when given {@code null}.
     */
    public void setMaxFiles(Integer maxFiles) {
        if (maxFiles == null) {
            throw new InvalidUserDataException(
                    "katachi { processors { diff { maxFiles = ... } } } was given no value. Leave it "
                            + "out entirely to keep the default, 100, or write 0 to list every file.");
        }
        this.maxFiles = maxFiles;
    }

    /**
     * These options as the {@code --arg} values they become.
     *
     * <p>Only what was set. An option left alone is absent rather than sent as its default, so the
     * default lives in the processor and is not copied here where the two could drift apart.
     */
    Map<String, String> toArgs() {
        Map<String, String> args = new LinkedHashMap<>();
        if (before != null) {
            args.put("before", before);
        }
        if (after != null) {
            args.put("after", after);
        }
        if (pr != null) {
            args.put("pr", pr);
        }
        if (weights != null) {
            args.put("weights", String.join(",", weights));
        }
        markdown.putArgs(args);
        html.putArgs(args);
        if (maxFiles != null) {
            args.put("maxFiles", maxFiles.toString());
        }
        return args;
    }

    private static String requireText(String name, String value) {
        if (value == null || value.isEmpty()) {
            throw new InvalidUserDataException(
                    "katachi { processors { diff { " + name + " = ... } } } was given no value. "
                            + "Leave it out entirely to keep the processor's default.");
        }
        return value;
    }
}
