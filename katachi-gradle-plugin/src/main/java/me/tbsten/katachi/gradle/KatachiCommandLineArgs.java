package me.tbsten.katachi.gradle;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.gradle.api.InvalidUserDataException;

/** The {@code --arg key=value} values of one task run, checked the same way by every katachi task. */
final class KatachiCommandLineArgs {

    private KatachiCommandLineArgs() {
    }

    /**
     * Splits each {@code --arg} on its first {@code =}, keeping the command line's order.
     *
     * @param taskName the task the values were given to, for the error message.
     * @throws InvalidUserDataException when a value does not contain {@code =}, has an empty key
     *     before the first {@code =}, or repeats a key already given.
     */
    static Map<String, String> parse(String taskName, List<String> rawArgs) {
        Map<String, String> parsed = new LinkedHashMap<>();
        for (String rawArg : rawArgs) {
            int separatorIndex = rawArg.indexOf('=');
            if (separatorIndex <= 0) {
                throw new InvalidUserDataException(
                        "Invalid --arg \"" + rawArg + "\" for " + taskName + ". Expected --arg "
                                + "key=value, e.g. --arg template=UseCase.");
            }
            String key = rawArg.substring(0, separatorIndex);
            if (parsed.containsKey(key)) {
                throw new InvalidUserDataException(
                        "--arg key \"" + key + "\" (\"" + rawArg + "\") was given more than "
                                + "once to " + taskName + ". Each --arg key may be passed only "
                                + "once.");
            }
            parsed.put(key, rawArg.substring(separatorIndex + 1));
        }
        return parsed;
    }

    /**
     * The error for {@code katachi { architecture = ... } } never having been set.
     *
     * @param taskName the task that needed it.
     */
    static InvalidUserDataException architectureNotSet(String taskName) {
        return new InvalidUserDataException(
                "katachi { architecture = ... } is not set in this module's "
                        + "build.gradle.kts. Add it, e.g. "
                        + "`katachi { architecture = \"com.example.projectArchitecture\" }`, "
                        + "then run " + taskName + " again.");
    }

    /**
     * The error for a module without the {@code java} plugin, whose {@code test} runtime
     * classpath is the one every katachi task runs on. A Kotlin Multiplatform or Android module
     * does not apply it, so the task would otherwise start a JVM with nothing on its classpath
     * and fail with "Could not find or load main class".
     *
     * @param taskName the task that needed it.
     */
    static InvalidUserDataException noTestClasspath(String taskName) {
        return new InvalidUserDataException(
                taskName + " has no classpath to run on: me.tbsten.katachi runs on the test "
                        + "runtime classpath of the java plugin, and this module does not apply it "
                        + "(a Kotlin Multiplatform or Android module does not). Apply "
                        + "me.tbsten.katachi to a kotlin(\"jvm\") module that holds the definition "
                        + "and its test, then run " + taskName + " there.");
    }
}
