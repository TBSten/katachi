package me.tbsten.katachi.gradle;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Pattern;
import org.gradle.api.InvalidUserDataException;

/**
 * The arguments one registered processor is given every run, written in {@code build.gradle.kts}.
 *
 * <p>{@code --arg} on the command line answers "what should this one run do"; this answers "what
 * does this project always want". A project that generates its documentation into
 * {@code docs/architecture} rather than {@code build/} says so once here instead of repeating it
 * in every invocation and every CI step.
 *
 * <p>Nothing here knows which processors exist. The key is a string and the arguments are strings,
 * exactly as they arrive over the command line, which is what keeps {@code runKatachiProcessor} a
 * task with no processor-specific logic in it. A name that is not one of the processor's arguments
 * is refused where every other unknown argument is, by the processor's own decoding, and names the
 * arguments it does know.
 *
 * <pre>{@code
 * katachi {
 *     architecture = "com.example.projectArchitecture"
 *     processors {
 *         args("docs") {
 *             arg("outputDir", "docs/architecture")
 *         }
 *     }
 * }
 * }</pre>
 */
public class KatachiProcessorArgs {

    /**
     * What an argument may be named.
     *
     * <p>The name of a Kotlin property on the processor's {@code Args} type, so the shape is
     * Kotlin's. {@code =} in particular has to be refused: {@code --arg=key=value} splits on the
     * first {@code =}, so a name holding one could never be written on the command line and the
     * two ways of passing an argument would stop agreeing.
     */
    private static final Pattern NAME_PATTERN = Pattern.compile("[A-Za-z_][A-Za-z0-9_]*");

    private final String key;
    private final Map<String, String> values = new LinkedHashMap<>();

    KatachiProcessorArgs(String key) {
        this.key = key;
    }

    /**
     * Gives the processor {@code name = value} on every run.
     *
     * <p>A {@code --arg} of the same name on the command line wins, so a configured value is a
     * default rather than a lock.
     *
     * @param name the argument's name, as the processor's {@code Args} type spells the property.
     * @param value the value, as a string. It is decoded by the processor, so {@code "3"} reaches
     *     an {@code Int} argument as {@code 3} and {@code "a,b"} reaches a {@code List<String>} as
     *     two elements.
     * @throws InvalidUserDataException when the name is not a Kotlin property name, or when the
     *     same name is given twice.
     */
    public void arg(String name, String value) {
        if (name == null || !NAME_PATTERN.matcher(name).matches()) {
            throw new InvalidUserDataException(
                    "Invalid katachi processor argument name \"" + name + "\" for key \"" + key
                            + "\". An argument is named after a property of the processor's Args "
                            + "type, so it may hold only letters, digits and underscore, and may "
                            + "not start with a digit (\"=\" in particular is refused because "
                            + "--arg=key=value splits on the first \"=\").");
        }
        if (values.containsKey(name)) {
            throw new InvalidUserDataException(
                    "katachi processor argument \"" + name + "\" is set twice for key \"" + key
                            + "\", first to \"" + values.get(name) + "\" and then to \"" + value
                            + "\". Each argument may be set only once; remove one of the two.");
        }
        if (value == null) {
            throw new InvalidUserDataException(
                    "katachi processor argument \"" + name + "\" for key \"" + key
                            + "\" was given no value. Arguments travel to the processor as "
                            + "strings; write \"\" for an empty one.");
        }
        values.put(name, value);
    }

    /** What was configured, in the order it was written. */
    Map<String, String> getValues() {
        return Collections.unmodifiableMap(new LinkedHashMap<>(values));
    }
}
