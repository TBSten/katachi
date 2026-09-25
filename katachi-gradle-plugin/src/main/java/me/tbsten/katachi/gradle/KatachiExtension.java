package me.tbsten.katachi.gradle;

import java.util.regex.Pattern;

import org.gradle.api.Action;
import org.gradle.api.InvalidUserDataException;

/**
 * The {@code katachi { } } block a user writes in their {@code build.gradle.kts}.
 *
 * <h2>Why {@code architecture} is a plain {@code String} getter/setter, not a
 * {@code Property<String>}</h2>
 *
 * <p>The design this plugin follows asks for exactly this to be writable:
 *
 * <pre>{@code
 * katachi {
 *     architecture = "com.example.projectArchitecture"
 * }
 * }</pre>
 *
 * <p>Writing {@code architecture = "..."} against a {@code Property<String>} with Kotlin's
 * {@code =} is <em>lazy property assignment</em>, a Kotlin DSL feature Gradle only turned on
 * starting with Gradle 8.2. katachi's own floor is Gradle 8.0, whose <em>embedded</em> Kotlin
 * compiler -- 1.8.10, the one that actually compiles a user's {@code build.gradle.kts}, not the
 * one that compiles this module -- does not accept that syntax against a {@code Property} at
 * all; the script fails to compile. A plain {@code String} field's setter, by contrast, is an
 * ordinary Kotlin property assignment and has always worked, on every Gradle version this plugin
 * claims to support. Asking users to write {@code architecture.set("...")} instead was
 * considered and rejected: it is not the shape the design settled on, and it would make every
 * example in the docs Gradle-version-conditional for no reason a user could see.
 *
 * <p>Laziness is not lost by this choice -- it moves one level down. {@link KatachiPlugin} wraps
 * this getter in {@code project.provider(extension::getArchitecture)} before handing it to
 * {@link GenerateKatachiEntryPointTask}, so the value is still read at execution time, after the
 * whole {@code build.gradle.kts} -- {@code katachi { } } included -- has been evaluated.
 *
 * <h2>Example 1: the shape a user writes</h2>
 *
 * <pre>{@code
 * katachi {
 *     architecture = "com.example.projectArchitecture"
 *     processors {
 *         register("layout", "me.tbsten.katachi.check.LayoutCheck")
 *     }
 * }
 * }</pre>
 *
 * @see KatachiProcessors
 */
public class KatachiExtension {

    /**
     * Same shape as {@link KatachiProcessors}'s class name check, plus a requirement that the
     * name hold at least one {@code .} -- a top level declaration in Kotlin's default (unnamed)
     * root package cannot be {@code import}ed by the generated code, so there would be nothing
     * to write into {@code GeneratedKatachiEntryPoint.kt}.
     */
    private static final Pattern ARCHITECTURE_CLASS_NAME_PATTERN =
            Pattern.compile("[A-Za-z_][A-Za-z0-9_]*(\\.[A-Za-z_][A-Za-z0-9_]*)+");

    private String architecture;
    private final KatachiProcessors processors = new KatachiProcessors();

    /**
     * The fully qualified name of the top level {@code val} the generated entry point imports as
     * this module's {@code Architecture}, or {@code null} when not yet set.
     */
    public String getArchitecture() {
        return architecture;
    }

    /**
     * Sets the definition to run processors against.
     *
     * @param architecture the fully qualified name of a top level {@code val Architecture} in
     *     this module's test source set, e.g. {@code "com.example.projectArchitecture"}. It has
     *     to be {@code internal} or {@code public}, never {@code private}: the generated code
     *     sits in the same module and compilation, so an {@code internal} declaration is visible
     *     to it, but a {@code private} one is not visible outside its own file.
     * @throws InvalidUserDataException when {@code architecture} contains {@code $} (the JVM's
     *     internal spelling of a nested class, not Kotlin's), is otherwise not a dotted sequence
     *     of Kotlin identifiers, or names a top level declaration with no package at all.
     */
    public void setArchitecture(String architecture) {
        if (architecture == null || !ARCHITECTURE_CLASS_NAME_PATTERN.matcher(architecture).matches()) {
            if (architecture != null && architecture.contains("$")) {
                throw new InvalidUserDataException(
                        "Invalid katachi { architecture = ... } value \"" + architecture + "\". "
                                + "\"$\" is the JVM's own way of writing a nested class "
                                + "internally; write it the way Kotlin source does instead.");
            }
            if (architecture != null && !architecture.contains(".")) {
                throw new InvalidUserDataException(
                        "Invalid katachi { architecture = ... } value \"" + architecture + "\". "
                                + "A top level declaration in the default (unnamed) root package "
                                + "cannot be imported by the generated code; declare "
                                + "`projectArchitecture` inside a package, e.g. "
                                + "\"com.example.projectArchitecture\".");
            }
            throw new InvalidUserDataException(
                    "Invalid katachi { architecture = ... } value \"" + architecture + "\". "
                            + "Expected the fully qualified name of a top level `val Architecture`, "
                            + "e.g. \"com.example.projectArchitecture\".");
        }
        this.architecture = architecture;
    }

    /** The processors this module registered. See {@link #processors(Action)}. */
    public KatachiProcessors getProcessors() {
        return processors;
    }

    /**
     * Configures this module's {@link KatachiProcessors}.
     *
     * @param action a block that calls {@link KatachiProcessors#register}.
     */
    public void processors(Action<? super KatachiProcessors> action) {
        action.execute(processors);
    }

}
