package me.tbsten.katachi.gradle;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.gradle.api.InvalidUserDataException;

/**
 * What {@code katachiTemplate} is given every run, written in {@code build.gradle.kts}.
 *
 * <p>Typed, for the reason {@link KatachiDocsOptions} is: these argument names are katachi's own
 * and part of its public surface, so a typo in {@code onExisting} belongs in the IDE rather than
 * at the end of a run. What a project writes itself keeps going through
 * {@code processors { args("key") { } }}.
 *
 * <p><strong>A template's own parameters are not written here.</strong> They differ from template
 * to template, so they travel as ordinary {@code --arg} entries, and the template processor
 * accepts them without any configuration in this block.
 *
 * <p>{@code template} is registered for every module this plugin is applied to, so there is
 * nothing to {@code register}.
 *
 * <pre>{@code
 * katachi {
 *     architecture = "com.example.projectArchitecture"
 *     processors {
 *         template {
 *             onExisting = KatachiOnExisting.SKIP
 *         }
 *     }
 * }
 * }</pre>
 *
 * @see KatachiOnExisting
 */
public class KatachiTemplateOptions {

    /** The registry key these arguments are for. Not configurable: it is katachi's own. */
    static final String KEY = "template";

    private static final java.util.regex.Pattern TEMPLATE_SPECIFIER_PATTERN =
            java.util.regex.Pattern.compile("[A-Za-z][A-Za-z0-9_-]*(\\.[A-Za-z][A-Za-z0-9_-]*)*");

    private List<String> template;
    private KatachiOnExisting onExisting;

    /** The templates run: each by {@code role.id} (or {@code role} alone for its one template). */
    public List<String> getTemplate() {
        return template;
    }

    /**
     * Sets the templates run, each by {@code role.id} -- or {@code role} alone while that role has
     * one template -- with a leading group written as {@code group.role.id}.
     *
     * <p>Usually left alone. Which template to generate is the one thing that changes from run to
     * run, so it is normally passed as {@code --arg template=data.Repository.repository}; writing
     * it here fixes the module to one set of templates. More than one entry generates them
     * together, exactly as {@code --arg template=a,b} does.
     *
     * @throws InvalidUserDataException when [template] is {@code null}, empty, or holds a specifier
     *     that is not {@code role}, {@code role.id} or {@code group.role.id}.
     */
    public void setTemplate(List<String> template) {
        if (template == null || template.isEmpty()) {
            throw new InvalidUserDataException(
                    "katachi { processors { template { template = ... } } } was given no value. "
                            + "Write at least one specifier, e.g. template = listOf(\"UseCase\"), "
                            + "or leave the block out entirely to pass --arg template= at the "
                            + "command line instead.");
        }
        for (String specifier : template) {
            if (specifier == null || !TEMPLATE_SPECIFIER_PATTERN.matcher(specifier).matches()) {
                throw new InvalidUserDataException(
                        "Invalid katachi { processors { template { template = ... } } } entry \""
                                + specifier + "\". Write a template specifier, e.g. \"UseCase\", "
                                + "\"UseCase.impl\", or \"domain.UseCase\" for a role in a group. "
                                + "A title is not a specifier here: identifiers hold only letters, "
                                + "digits, underscore and hyphen, separated by \".\".");
            }
        }
        this.template = template;
    }

    /** What a run does when a file a template produces is already on disk. */
    public KatachiOnExisting getOnExisting() {
        return onExisting;
    }

    /**
     * Sets what a run does when a file a template produces is already on disk.
     *
     * <p>Left unset, the processor's own default applies, which is {@link KatachiOnExisting#FAIL}.
     * All three answers decide about the whole set of files rather than about one of them, so
     * {@link KatachiOnExisting#SKIP} leaves the run alone entirely rather than filling the gaps.
     *
     * @throws InvalidUserDataException when given {@code null}.
     */
    public void setOnExisting(KatachiOnExisting onExisting) {
        if (onExisting == null) {
            throw new InvalidUserDataException(
                    "katachi { processors { template { onExisting = ... } } } was given no "
                            + "value. Leave it out entirely to keep the default, FAIL.");
        }
        this.onExisting = onExisting;
    }

    /** Whether this block was written at all. */
    boolean isConfigured() {
        return template != null || onExisting != null;
    }

    /** These options as the {@code --arg} values they become. Only what was set. */
    Map<String, String> toArgs() {
        Map<String, String> args = new LinkedHashMap<>();
        if (template != null) {
            args.put("template", String.join(",", template));
        }
        if (onExisting != null) {
            args.put("onExisting", onExisting.wireName());
        }
        return args;
    }
}
