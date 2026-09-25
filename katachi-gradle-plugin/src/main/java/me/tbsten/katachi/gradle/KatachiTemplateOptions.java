package me.tbsten.katachi.gradle;

import java.util.LinkedHashMap;
import java.util.Map;
import org.gradle.api.InvalidUserDataException;

/**
 * What {@code --processor=template} is given every run, written in {@code build.gradle.kts}.
 *
 * <p><strong>Only {@code roleName} so far.</strong> Code generation from a template is not
 * implemented yet, and the rest of its arguments -- how a template's own parameters reach it, what
 * happens to a file that is already there -- are still open. They arrive here as they are settled,
 * which is why this block exists now rather than later: a project that writes
 * {@code template { roleName = "UseCase" }} today keeps it working, instead of migrating off a
 * string map once the typed block appears.
 *
 * <p>Registering {@code template} is still the project's job. Unlike {@code docs}, katachi does not
 * register it by default, because the processor it would point at does not exist.
 *
 * <pre>{@code
 * katachi {
 *     architecture = "com.example.projectArchitecture"
 *     template {
 *         roleName = "UseCase"
 *     }
 * }
 * }</pre>
 */
public class KatachiTemplateOptions {

    /** The registry key these arguments are for. Not configurable: it is katachi's own. */
    static final String KEY = "template";

    private static final java.util.regex.Pattern ROLE_NAME_PATTERN =
            java.util.regex.Pattern.compile("[A-Za-z][A-Za-z0-9_-]*(/[A-Za-z][A-Za-z0-9_-]*)*");

    private String roleName;

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

    /** These options as the {@code --arg} values they become. Only what was set. */
    Map<String, String> toArgs() {
        Map<String, String> args = new LinkedHashMap<>();
        if (roleName != null) {
            args.put("roleName", roleName);
        }
        return args;
    }
}
