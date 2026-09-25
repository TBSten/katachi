package me.tbsten.katachi.gradle;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import org.gradle.api.Action;
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
 * <p>{@code docs} and {@code template} are registered for every module this plugin is applied
 * to, so {@code --processor=docs} works with an empty {@code katachi { } } block. They are the
 * only two: {@code layout} and {@code konsist} would have to be defaulted too if the rule were
 * "katachi's own processors", but {@code konsist} lives in {@code :katachi-konsist} and a module
 * that does not depend on it would get a registry entry that fails to resolve at run time.
 * Registering only what {@code :katachi} itself carries keeps every default entry resolvable.
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
        defaults.put("template", "me.tbsten.katachi.template.GenerateCodeFromTemplate");
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
    private final Map<String, KatachiProcessorArgs> configuredArgs = new LinkedHashMap<>();
    private final KatachiDocsOptions docs = new KatachiDocsOptions();
    private final KatachiTemplateOptions template = new KatachiTemplateOptions();

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
     * Registers a processor and gives it the arguments it always runs with.
     *
     * <p>The arguments are this module's defaults: a {@code --arg} of the same name on the
     * command line wins, so one run can still differ without the build script changing. They are
     * sent only when {@code key} is actually selected.
     *
     * <pre>{@code
     * katachi {
     *     processors {
     *         register("roleNames", "com.example.processors.RoleNames") {
     *             arg("prefix", "domain")
     *         }
     *     }
     * }
     * }</pre>
     *
     * @see #register(String, String)
     */
    public void register(String key, String className, Action<? super KatachiProcessorArgs> action) {
        register(key, className);
        configure(key, action);
    }

    /**
     * Gives an already registered processor the arguments it always runs with.
     *
     * <p>The one to reach for when the registration is katachi's own -- {@code docs} is
     * registered by default, so there is nothing to call {@link #register} for. Its typed block
     * {@link #docs(Action)} is the better door for that one; this stays for a processor whose
     * arguments katachi does not know.
     *
     * @throws InvalidUserDataException when the key is not one a processor may have, or when the
     *     same key is configured twice.
     */
    public void args(String key, Action<? super KatachiProcessorArgs> action) {
        if (key == null || key.isEmpty() || !KEY_PATTERN.matcher(key).matches()) {
            throw new InvalidUserDataException(
                    "Invalid katachi processor key \"" + key + "\" in args(...). "
                            + "Keys may hold only letters, digits, underscore and hyphen.");
        }
        configure(key, action);
    }

    private void configure(String key, Action<? super KatachiProcessorArgs> action) {
        if (configuredArgs.containsKey(key)) {
            throw new InvalidUserDataException(
                    "katachi processor key \"" + key + "\" is configured twice. "
                            + "Write one block per key, holding every argument that key needs.");
        }
        KatachiProcessorArgs args = new KatachiProcessorArgs(key);
        action.execute(args);
        configuredArgs.put(key, args);
    }

    /** What `--processor=docs` is given every run. See {@link #docs(Action)}. */
    public KatachiDocsOptions getDocs() {
        return docs;
    }

    /**
     * Configures the documentation processor katachi ships.
     *
     * <p>Typed, unlike {@link #args}, because these argument names are katachi's own and part of
     * its public surface: a typo in {@code outputDir} belongs in the IDE rather than at the end
     * of a run. What a project writes itself keeps going through the string form.
     *
     * <pre>{@code
     * katachi {
     *     processors {
     *         docs {
     *             outputDir = rootProject.layout.projectDirectory.dir("docs")
     *         }
     *     }
     * }
     * }</pre>
     */
    public void docs(Action<? super KatachiDocsOptions> action) {
        action.execute(docs);
    }

    /** What `--processor=template` is given every run. See {@link #template(Action)}. */
    public KatachiTemplateOptions getTemplate() {
        return template;
    }

    /**
     * Configures the template processor katachi ships.
     *
     * <p>Typed for the same reason {@link #docs(Action)} is. {@code template} is registered by
     * default, so there is nothing to {@link #register}; what this block is usually written for is
     * {@code acceptsUndeclaredArgs = true}, without which a template's own parameters cannot be
     * passed as {@code --arg} at all.
     *
     * <pre>{@code
     * katachi {
     *     processors {
     *         template {
     *             acceptsUndeclaredArgs = true
     *         }
     *     }
     * }
     * }</pre>
     */
    public void template(Action<? super KatachiTemplateOptions> action) {
        action.execute(template);
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

    /** Every configured argument, by key: the typed blocks and the string form, merged. */
    Map<String, Map<String, String>> getConfiguredArgs() {
        Map<String, Map<String, String>> copy = new LinkedHashMap<>();
        Map<String, String> docsArgs = docs.toArgs();
        putTyped(copy, KatachiDocsOptions.KEY, docsArgs, !docsArgs.isEmpty(), "docs { }");
        putTyped(
                copy,
                KatachiTemplateOptions.KEY,
                template.toArgs(),
                template.isConfigured(),
                "template { }");
        for (Map.Entry<String, KatachiProcessorArgs> entry : configuredArgs.entrySet()) {
            copy.put(entry.getKey(), entry.getValue().getValues());
        }
        return Collections.unmodifiableMap(copy);
    }

    /**
     * Records what a typed block configured, and refuses the same key being configured twice.
     *
     * <p>{@code configured} is asked separately from {@code typed.isEmpty()}: a block may carry a
     * word that produces no argument -- {@code template { acceptsUndeclaredArgs = true } } -- and
     * such a block still has to collide with {@code args("template") { } } rather than vanish.
     */
    private void putTyped(
            Map<String, Map<String, String>> collected,
            String key,
            Map<String, String> typed,
            boolean configured,
            String blockName) {
        if (!configured) {
            return;
        }
        if (configuredArgs.containsKey(key)) {
            throw new InvalidUserDataException(
                    "processors { " + blockName + " } and processors { args(\"" + key
                            + "\") { } } both configure \"" + key + "\". They mean the same "
                            + "thing, so one of the two is being ignored; keep the typed "
                            + blockName + " and remove the other.");
        }
        if (typed.isEmpty()) {
            return;
        }
        collected.put(key, typed);
    }

    /**
     * The registered keys this module let katachi ask for extra {@code --arg} names, sorted by the
     * order they were written.
     *
     * <p>Not derivable from {@link #getConfiguredArgs()}: a block that only raises its hand
     * produces no argument at all, so it would be invisible on that path.
     *
     * @throws InvalidUserDataException when a key raised its hand without being registered. The
     *     flag would reach the generated entry point and then be dropped by a registry that has no
     *     such key, which is a line in a build script doing nothing.
     */
    Set<String> getUndeclaredArgAcceptors() {
        Set<String> acceptors = new LinkedHashSet<>();
        if (template.getAcceptsUndeclaredArgs()) {
            acceptors.add(KatachiTemplateOptions.KEY);
        }
        for (Map.Entry<String, KatachiProcessorArgs> entry : configuredArgs.entrySet()) {
            if (entry.getValue().getAcceptsUndeclaredArgs()) {
                acceptors.add(entry.getKey());
            }
        }
        Map<String, String> registered = getRegistrations();
        for (String key : acceptors) {
            if (!registered.containsKey(key)) {
                throw new InvalidUserDataException(
                        "katachi processor key \"" + key + "\" sets acceptsUndeclaredArgs = true "
                                + "but is not registered, so nothing would ever be asked what it "
                                + "accepts. Register it first, e.g. register(\"" + key
                                + "\", \"com.example.processors.Example\"), or remove the flag.");
            }
        }
        return Collections.unmodifiableSet(acceptors);
    }
}
