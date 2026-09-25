package me.tbsten.katachi.gradle;

import java.util.LinkedHashMap;
import java.util.Map;
import org.gradle.api.InvalidUserDataException;

/**
 * What {@code --processor=template} is given every run, written in {@code build.gradle.kts}.
 *
 * <p>Typed, for the reason {@link KatachiDocsOptions} is: these argument names are katachi's own
 * and part of its public surface, so a typo in {@code onExisting} belongs in the IDE rather than
 * at the end of a run. What a project writes itself keeps going through
 * {@code processors { args("key") { } }}.
 *
 * <p><strong>A template's own parameters are not written here.</strong> They differ from role to
 * role, so they travel as ordinary {@code --arg} entries, and the template processor accepts them
 * without any configuration in this block.
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

    private static final java.util.regex.Pattern ROLE_NAME_PATTERN =
            java.util.regex.Pattern.compile("[A-Za-z][A-Za-z0-9_-]*(/[A-Za-z][A-Za-z0-9_-]*)*");

    private String roleName;
    private KatachiOnExisting onExisting;

    /** The role whose template is used. */
    public String getRoleName() {
        return roleName;
    }

    /**
     * Sets the role whose template is used.
     *
     * <p>The role's identifier, not its {@code title}: {@code "UseCase"} for a role declared beside
     * the one asking, and {@code "domain/UseCase"} for one in another group.
     *
     * <p>Usually left alone. Which role to generate is the one thing that changes from run to run,
     * so it is normally passed as {@code --arg roleName=UseCase}; writing it here fixes the module
     * to one role.
     *
     * @throws InvalidUserDataException when the name is not one a role can have.
     */
    public void setRoleName(String roleName) {
        if (roleName == null || !ROLE_NAME_PATTERN.matcher(roleName).matches()) {
            throw new InvalidUserDataException(
                    "Invalid katachi { template { roleName = \"" + roleName + "\" } }. Write a "
                            + "role's identifier, e.g. \"UseCase\", or \"domain/UseCase\" for a "
                            + "role in another group. A title is not a name here: identifiers "
                            + "hold only letters, digits, underscore and hyphen, separated by "
                            + "\"/\".");
        }
        this.roleName = roleName;
    }

    /** What a run does when a file the template produces is already on disk. */
    public KatachiOnExisting getOnExisting() {
        return onExisting;
    }

    /**
     * Sets what a run does when a file the template produces is already on disk.
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
                    "katachi { template { onExisting = ... } } was given no value. Leave it out "
                            + "entirely to keep the default, FAIL.");
        }
        this.onExisting = onExisting;
    }

    /** Whether this block was written at all. */
    boolean isConfigured() {
        return roleName != null || onExisting != null;
    }

    /** These options as the {@code --arg} values they become. Only what was set. */
    Map<String, String> toArgs() {
        Map<String, String> args = new LinkedHashMap<>();
        if (roleName != null) {
            args.put("roleName", roleName);
        }
        if (onExisting != null) {
            args.put("onExisting", onExisting.wireName());
        }
        return args;
    }
}
