package me.tbsten.katachi.gradle;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.List;
import java.util.Set;

import org.gradle.api.InvalidUserDataException;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.MapProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.JavaExec;
import org.gradle.api.tasks.Optional;
import org.gradle.api.tasks.options.Option;
import org.gradle.work.DisableCachingByDefault;

/**
 * Starts a JVM on the applying module's test runtime classpath and runs katachi's entry point.
 *
 * <p>It is a {@link JavaExec} rather than a {@code Test} task on purpose. A {@code Test} task
 * starts a test engine, and a run that discovers no tests reports success without having done
 * anything; this one starts one class and nothing else. Nothing on this path reads a test
 * engine, so {@code build/test-results/} stays absent after a run -- which is the mechanical
 * way to tell the two apart.
 *
 * <p>{@code ignoreExitValue} is left at its default of {@code false}, and nothing here may set
 * it to {@code true}. That default is what makes the entry point's failures visible: an
 * uncaught exception ends the JVM with a non-zero status, and {@link JavaExec} turns a non-zero
 * status into a failed task.
 *
 * <h2>Example 1: run one processor with an argument</h2>
 *
 * <pre>{@code
 * ./gradlew runKatachiProcessor --processor=docs --arg roleName=GetUser
 * }</pre>
 *
 * <h2>Example 2: run two processors at once</h2>
 *
 * <pre>{@code
 * ./gradlew runKatachiProcessor --processor=layout,konsist
 * }</pre>
 *
 * <h2>Example 3: point the task at a different entry point from a build script</h2>
 *
 * <pre>{@code
 * import me.tbsten.katachi.gradle.RunKatachiProcessorTask
 *
 * tasks.named<RunKatachiProcessorTask>("runKatachiProcessor") {
 *     mainClass.set("com.example.MyRunnerKt")
 * }
 * }</pre>
 *
 * @see KatachiPlugin
 */
// Stated on this type rather than left to the one JavaExec carries. `java-gradle-plugin` runs
// `validatePlugins` with stricter validation, which asks every task type to say whether its
// output may be cached; saying it here keeps the answer independent of whether Gradle treats
// the supertype's annotation as inherited. It is also the true answer: what a processor writes
// is decided at run time by `--processor` and `--arg`, so there is no declared output for a
// cache to key on.
@DisableCachingByDefault(because = "Runs an arbitrary processor whose outputs are not declared")
public abstract class RunKatachiProcessorTask extends JavaExec {

    private List<String> processorKeys = new ArrayList<>();
    private List<String> processorArgs = new ArrayList<>();

    /**
     * Registered processor key(s) to run, comma separated within one {@code --processor}, or
     * given several times. Both {@code --processor=a,b} and {@code --processor=a --processor=b}
     * are accepted, and either form may repeat a key -- the duplicate is silently dropped rather
     * than counted twice.
     *
     * <p>A plain {@code List<String>} with a setter rather than a {@link ListProperty}: Gradle
     * 8.0 does not recognise {@code @Option} on a {@code ListProperty} as taking an argument at
     * all, and answers {@code --processor} with "does not take an argument". A setter taking a
     * {@code List<String>} has been the repeatable form since long before 8.0. The compatibility
     * matrix is what caught this -- it passed on the wrapper's Gradle and failed only on 8.0.
     */
    @Input
    public List<String> getProcessorKeys() {
        return processorKeys;
    }

    @Option(option = "processor", description = "Registered processor key(s), comma-separated. Repeatable.")
    public void setProcessorKeys(List<String> processorKeys) {
        this.processorKeys = processorKeys;
    }

    /** A processor argument as {@code key=value}. Repeatable; may itself contain {@code =}. */
    @Input
    public List<String> getProcessorArgs() {
        return processorArgs;
    }

    @Option(option = "arg", description = "Processor argument as key=value. Repeatable.")
    public void setProcessorArgs(List<String> processorArgs) {
        this.processorArgs = processorArgs;
    }

    /** Set by {@link KatachiPlugin} from {@code katachi { architecture = ... } }. */
    @Input
    @Optional
    public abstract Property<String> getArchitectureClassName();

    /** Set by {@link KatachiPlugin} to {@link KatachiEntryPointSource#QUALIFIED_NAME}. */
    @Input
    public abstract Property<String> getEntryPointClassName();

    /**
     * What each processor key was given in {@code build.gradle.kts}, by key.
     *
     * <p>Filled by {@link KatachiPlugin} from the {@code katachi { }} block: the typed
     * {@code docs { }} and {@code template { }} blocks, and the string form
     * {@code processors { args("key") { } }}. Only the keys this run actually selects are sent,
     * so configuring a processor that is not asked for costs nothing.
     */
    @Input
    public abstract MapProperty<String, Map<String, String>> getConfiguredArgs();

    /**
     * Validates this run's configuration and builds the argv the entry point reads, then starts
     * the JVM.
     *
     * <p>Validation happens here, in the task, rather than inside the entry point's own {@code
     * main()} on {@code :katachi}. These are mistakes in the Gradle command line the user typed,
     * so failing before a JVM is even started reports the error as Gradle's own -- no stack
     * trace, no JVM startup cost, and the message is the first thing on the screen rather than
     * buried under {@code JavaExec}'s "process finished with non-zero exit value" noise. {@code
     * main()} on the other side performs the same checks independently, but that copy exists
     * only to guard a hand-written call to {@code main()} directly; it is not the path a user
     * following this task takes.
     *
     * @throws InvalidUserDataException when {@code katachi { architecture = ... } } was never
     *     set; when no {@code --processor} was given; or when some {@code --arg} does not
     *     contain {@code =}, has an empty key before the first {@code =}, or repeats a key
     *     already given.
     */
    @Override
    public void exec() {
        if (!getArchitectureClassName().isPresent()) {
            throw new InvalidUserDataException(
                    "katachi { architecture = ... } is not set in this module's "
                            + "build.gradle.kts. Add it, e.g. "
                            + "`katachi { architecture = \"com.example.projectArchitecture\" }`, "
                            + "then run this task again.");
        }

        List<String> processorKeys = splitAndDeduplicate(getProcessorKeys());
        if (processorKeys.isEmpty()) {
            throw new InvalidUserDataException(
                    "No --processor was given. Pass at least one registered processor key, e.g. "
                            + "`./gradlew runKatachiProcessor --processor=<key>`.");
        }

        List<String> args = new ArrayList<>();
        args.add("--entry-point=" + getEntryPointClassName().get());
        for (String key : processorKeys) {
            args.add("--processor=" + key);
        }

        // The command line wins over what the build script configured, so `--arg` stays the way
        // to vary one run without editing the build. Collected first for that reason: a key seen
        // here is not overwritten below.
        Set<String> seenArgKeys = new LinkedHashSet<>();
        for (String rawArg : getProcessorArgs()) {
            int separatorIndex = rawArg.indexOf('=');
            if (separatorIndex <= 0) {
                throw new InvalidUserDataException(
                        "Invalid --arg \"" + rawArg + "\". Expected --arg key=value, e.g. "
                                + "--arg roleName=GetUser.");
            }
            String key = rawArg.substring(0, separatorIndex);
            if (!seenArgKeys.add(key)) {
                throw new InvalidUserDataException(
                        "--arg key \"" + key + "\" (\"" + rawArg + "\") was given more than "
                                + "once. Each --arg key may be passed only once.");
            }
            args.add("--arg=" + rawArg);
        }

        Map<String, Map<String, String>> configured = getConfiguredArgs().get();
        Map<String, String> fromBuildScript = new LinkedHashMap<>();
        Map<String, String> claimedBy = new LinkedHashMap<>();
        for (String key : processorKeys) {
            Map<String, String> forKey = configured.get(key);
            if (forKey == null) {
                continue;
            }
            for (Map.Entry<String, String> entry : forKey.entrySet()) {
                String name = entry.getKey();
                if (seenArgKeys.contains(name)) {
                    continue;
                }
                String previousOwner = claimedBy.get(name);
                if (previousOwner != null && !fromBuildScript.get(name).equals(entry.getValue())) {
                    // Two selected processors both configured the same name, differently. The
                    // command line carries one value per name, so there is no answer that serves
                    // both; say which two disagree rather than picking one.
                    throw new InvalidUserDataException(
                            "\"" + previousOwner + "\" and \"" + key + "\" both configure the "
                                    + "argument \"" + name + "\", to \"" + fromBuildScript.get(name)
                                    + "\" and \"" + entry.getValue() + "\". A run carries one "
                                    + "value per argument name, so run them separately, or pass "
                                    + "--arg " + name + "=... to settle it for this run.");
                }
                fromBuildScript.put(name, entry.getValue());
                claimedBy.put(name, key);
            }
        }
        for (Map.Entry<String, String> entry : fromBuildScript.entrySet()) {
            args.add("--arg=" + entry.getKey() + "=" + entry.getValue());
        }

        setArgs(args);
        super.exec();
    }

    /** Comma-splits every element of [raw], drops empty pieces, and deduplicates by first seen. */
    private static List<String> splitAndDeduplicate(List<String> raw) {
        Set<String> result = new LinkedHashSet<>();
        for (String entry : raw) {
            for (String piece : entry.split(",")) {
                if (!piece.isEmpty()) {
                    result.add(piece);
                }
            }
        }
        return new ArrayList<>(result);
    }
}
