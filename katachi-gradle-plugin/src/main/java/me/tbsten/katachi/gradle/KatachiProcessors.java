package me.tbsten.katachi.gradle;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Pattern;

import org.gradle.api.InvalidUserDataException;

/**
 * The receiver of {@code katachi { processors { ... } } }: every processor this module
 * registers, keyed by the name it is selected with on the command line.
 *
 * <p>Validation happens right here, inside {@link #register}, rather than being deferred to the
 * code generator. This is the only point in the whole path that still knows which line of the
 * user's {@code build.gradle.kts} a bad key or class name came from -- a mistake caught later
 * only points at the generated file's own compile error, with the user's own build script
 * nowhere in the stack trace. This module does not depend on {@code :katachi}, so every error
 * here is a plain Gradle exception rather than a Katachi one.
 *
 * <h2>Which processors are there without being registered</h2>
 *
 * <p>{@code docs} is registered for every module this plugin is applied to, so
 * {@code --processor=docs} works with an empty {@code katachi { } } block. It is the only one:
 * {@code layout} and {@code konsist} would have to be defaulted too if the rule were "katachi's
 * own processors", but {@code konsist} lives in {@code :katachi-konsist} and a module that does
 * not depend on it would get a registry entry that fails to resolve at run time. Registering
 * only what {@code :katachi} itself carries keeps every default entry resolvable.
 *
 * <p>A {@link #register} of the same key wins. Without that, swapping in a documentation
 * processor of one's own would mean either accepting katachi's under its natural name or
 * inventing a second one, and the default would have taken a name away from the user rather
 * than saved them a line.
 *
 * <h2>Example 1: register two processors</h2>
 *
 * <pre>{@code
 * katachi {
 *     architecture = "com.example.projectArchitecture"
 *     processors {
 *         register("layout", "me.tbsten.katachi.check.LayoutCheck")
 *         register("konsist", "me.tbsten.katachi.check.KonsistCheck")
 *     }
 * }
 * }</pre>
 *
 * <h2>Example 2: replace the built-in documentation processor</h2>
 *
 * <pre>{@code
 * katachi {
 *     architecture = "com.example.projectArchitecture"
 *     processors {
 *         register("docs", "com.example.processors.OurOwnDocs");
 *     }
 * }
 * }</pre>
 *
 * @see KatachiExtension
 */
public class KatachiProcessors {

    /**
     * The registrations every module gets without asking, key to fully qualified class name.
     *
     * <p>Only processors that live in {@code me.tbsten.katachi:katachi} itself may be listed
     * here. The generated entry point writes each of these into a {@code ::class.java} literal
     * that has to compile against the module's test compile classpath, so a class from an
     * artifact the module did not depend on would break {@code compileTestKotlin} for every user
     * of this plugin rather than only for the one who asked for it.
     */
    private static final Map<String, String> DEFAULT_REGISTRATIONS = defaultRegistrations();

    private static Map<String, String> defaultRegistrations() {
        Map<String, String> defaults = new LinkedHashMap<>();
        defaults.put("docs", "me.tbsten.katachi.docs.GenerateDocumentation");
        return defaults;
    }

    /**
     * Keys may hold letters, digits, underscore and hyphen only.
     *
     * <p>A comma, space or {@code =} would collide with how {@code --processor=a,b} is split on
     * the command line, or with {@code --arg=key=value}'s own syntax.
     */
    private static final Pattern KEY_PATTERN = Pattern.compile("[A-Za-z0-9_-]+");

    /**
     * A Kotlin-shaped fully qualified name: one or more {@code .}-separated identifiers.
     *
     * <p>Deliberately does not allow {@code $}, which is how the JVM spells a nested class
     * internally ({@code Outer$Inner}) but which is never how Kotlin source names one
     * ({@code Outer.Inner}). {@code entrypoint-discovery.md} dropped a fully qualified name
     * scheme for the same reason once already; this validation exists so the same trap is not
     * reopened here.
     */
    private static final Pattern CLASS_NAME_PATTERN =
            Pattern.compile("[A-Za-z_][A-Za-z0-9_]*(\\.[A-Za-z_][A-Za-z0-9_]*)+");

    /** Registrations in the order {@link #register} was called, key to class name. */
    private final Map<String, String> registrations = new LinkedHashMap<>();

    /**
     * Registers a processor to run under {@code --processor=<key>}.
     *
     * @param key the name this processor is selected with on the command line. Letters, digits,
     *     underscore and hyphen only -- comma, whitespace and {@code =} are refused because they
     *     collide with how the command line itself is split.
     * @param className the processor's fully qualified Kotlin class or object name, e.g.
     *     {@code "me.tbsten.katachi.check.LayoutCheck"}. Refused if it contains {@code $}: that
     *     is the JVM's own spelling of a nested class and cannot be written into the generated
     *     Kotlin source, which needs {@code Outer.Inner} instead. Registering a key this plugin
     *     registers by default ({@code docs}) replaces it rather than failing.
     * @throws InvalidUserDataException when {@code key} is blank or holds a character other than
     *     a letter, digit, underscore or hyphen; when {@code key} was already registered, naming
     *     the class name it already points at; or when {@code className} is not a dotted
     *     sequence of Kotlin identifiers.
     */
    public void register(String key, String className) {
        if (key == null || key.isEmpty() || !KEY_PATTERN.matcher(key).matches()) {
            throw new InvalidUserDataException(
                    "Invalid katachi processor key \"" + key + "\". "
                            + "Keys may hold only letters, digits, underscore and hyphen "
                            + "(comma, whitespace and \"=\" are refused because "
                            + "--processor=a,b splits on comma and --arg=key=value splits on "
                            + "\"=\").");
        }
        if (registrations.containsKey(key)) {
            throw new InvalidUserDataException(
                    "katachi processor key \"" + key + "\" is already registered, pointing at "
                            + registrations.get(key) + ". "
                            + "Each key may be registered only once; pick a different key or "
                            + "remove the earlier registration.");
        }
        if (className == null || !CLASS_NAME_PATTERN.matcher(className).matches()) {
            if (className != null && className.contains("$")) {
                throw new InvalidUserDataException(
                        "Invalid katachi processor class name \"" + className + "\" for key \""
                                + key + "\". "
                                + "\"$\" is the JVM's own way of writing a nested class "
                                + "internally; write it the way Kotlin source does instead, e.g. "
                                + "\"com.example.Outer.Inner\".");
            }
            throw new InvalidUserDataException(
                    "Invalid katachi processor class name \"" + className + "\" for key \""
                            + key + "\". "
                            + "Expected a fully qualified Kotlin class or object name, e.g. "
                            + "\"me.tbsten.katachi.check.LayoutCheck\".");
        }
        registrations.put(key, className);
    }

    /**
     * Every processor this module can run, key to fully qualified class name: what
     * {@link #register} was called with, on top of what this plugin registers by default.
     *
     * <p>The user's own registration of a default key replaces it. Order is not meaningful --
     * {@link KatachiEntryPointSource#render} sorts by key so that reordering a build script
     * cannot change the generated text.
     */
    public Map<String, String> getRegistrations() {
        Map<String, String> merged = new LinkedHashMap<>(DEFAULT_REGISTRATIONS);
        merged.putAll(registrations);
        return merged;
    }
}
