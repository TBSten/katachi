package me.tbsten.katachi.gradle;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
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
 * Runs several registered katachi processors in one JVM, on one walk of the project.
 *
 * <p>{@link KatachiPlugin} registers one of these as {@code katachiProcessors}, next to the
 * {@code katachi<Key>} task every processor has. Running {@code katachiLayout} and
 * {@code katachiDocs} starts two JVMs, loads the definition twice and walks the project twice;
 * this task starts one JVM and walks once. The processors run one after another in the order
 * they were given, and one's failure does not stop the others: the task fails after all of them
 * have reported.
 *
 * <p>Each processor gets the arguments its own {@code katachi<Key>} task would get from the
 * build script -- {@code docs { outputDir = ... }} reaches {@code docs} and no other processor.
 * A {@code --arg} is given to every selected processor and, as for a single processor, wins over
 * a build-script value of the same name.
 *
 * <p>Every processor sees the project as the one walk found it. A file one processor writes in
 * this run is not seen by another processor of the same run; when one processor has to read what
 * another writes, run their own {@code katachi<Key>} tasks one after the other instead.
 *
 * <p>It runs every time: it declares no output, so Gradle never calls it up to date or takes it
 * from the build cache.
 *
 * <h2>Example 1: run three processors in one JVM</h2>
 *
 * <pre>{@code
 * ./gradlew :architecture-test:katachiProcessors --processor layout --processor fileConstraint --processor docs
 * }</pre>
 *
 * <h2>Example 2: choose the processors in the build script</h2>
 *
 * <pre>{@code
 * import me.tbsten.katachi.gradle.KatachiProcessorsTask
 *
 * tasks.named<KatachiProcessorsTask>("katachiProcessors") {
 *     processorKeys = listOf("layout", "fileConstraint", "docs")
 * }
 * }</pre>
 *
 * @see KatachiProcessorTask
 * @see KatachiPlugin
 */
@DisableCachingByDefault(because = "Runs arbitrary processors whose outputs are not declared")
public abstract class KatachiProcessorsTask extends JavaExec {

    private List<String> processorKeys = new ArrayList<>();
    private List<String> processorArgs = new ArrayList<>();

    /** Creates the task. Configured by {@link KatachiPlugin}. */
    public KatachiProcessorsTask() {
    }

    /**
     * The keys of the processors to run, in order. Each value may itself be a comma-separated
     * list of keys; a key given twice runs once.
     *
     * @return the keys as given, before splitting.
     */
    @Input
    public List<String> getProcessorKeys() {
        return processorKeys;
    }

    /**
     * Sets the processors this run starts, replacing what the build script set.
     *
     * <p>A plain {@code List<String>} with a setter for the reason given on
     * {@link KatachiProcessorTask#setProcessorArgs(List)}.
     *
     * @param processorKeys registered processor keys, e.g. {@code layout} and {@code docs}.
     */
    @Option(option = "processor", description = "Registered processor key to run. Repeatable; may be comma-separated.")
    public void setProcessorKeys(List<String> processorKeys) {
        this.processorKeys = processorKeys;
    }

    /**
     * A processor argument as {@code key=value}, given to every selected processor. Repeatable;
     * may itself contain {@code =}.
     *
     * @return the arguments as given.
     */
    @Input
    public List<String> getProcessorArgs() {
        return processorArgs;
    }

    /**
     * Sets the {@code --arg} values of this run.
     *
     * @param processorArgs {@code key=value} values.
     */
    @Option(option = "arg", description = "Processor argument as key=value, given to every selected processor. Repeatable.")
    public void setProcessorArgs(List<String> processorArgs) {
        this.processorArgs = processorArgs;
    }

    /**
     * Set by {@link KatachiPlugin} from {@code katachi { architecture = ... } }.
     *
     * @return the architecture's class name.
     */
    @Input
    @Optional
    public abstract Property<String> getArchitectureClassName();

    /**
     * Set by {@link KatachiPlugin} to {@link KatachiEntryPointSource#QUALIFIED_NAME}.
     *
     * @return the generated entry point's class name.
     */
    @Input
    public abstract Property<String> getEntryPointClassName();

    /**
     * Every registered processor key. Set by {@link KatachiPlugin}.
     *
     * @return the keys a run may select.
     */
    @Input
    public abstract ListProperty<String> getRegisteredKeys();

    /**
     * What each processor was given in {@code build.gradle.kts}, by processor key. Set by
     * {@link KatachiPlugin}; the same values each {@code katachi<Key>} task sends.
     *
     * @return the configured arguments by processor key.
     */
    @Input
    public abstract MapProperty<String, Map<String, String>> getConfiguredArgs();

    /**
     * Validates this run's configuration and builds the argv the entry point reads, then starts
     * the JVM.
     *
     * <p>A build-script argument travels as {@code --arg-for=<key>:<name>=<value>}, which the
     * entry point hands to that one processor, rather than as {@code --arg}, which it hands to
     * all of them.
     *
     * @throws InvalidUserDataException when {@code katachi { architecture = ... } } was never
     *     set, when no processor was selected, when a selected key is not registered, or when
     *     some {@code --arg} is not {@code key=value} or repeats a key.
     */
    @Override
    public void exec() {
        if (!getArchitectureClassName().isPresent()) {
            throw KatachiCommandLineArgs.architectureNotSet(getName());
        }
        Set<String> keys = selectedKeys();
        Map<String, String> commandLineArgs = KatachiCommandLineArgs.parse(getName(), getProcessorArgs());

        List<String> args = new ArrayList<>();
        args.add("--entry-point=" + getEntryPointClassName().get());
        for (String key : keys) {
            args.add("--processor=" + key);
        }
        for (Map.Entry<String, String> entry : commandLineArgs.entrySet()) {
            args.add("--arg=" + entry.getKey() + "=" + entry.getValue());
        }
        Map<String, Map<String, String>> configured = getConfiguredArgs().get();
        for (String key : keys) {
            Map<String, String> forKey = configured.get(key);
            if (forKey == null) {
                continue;
            }
            for (Map.Entry<String, String> entry : forKey.entrySet()) {
                // Left out rather than sent and overridden, so the command line reads the way
                // the run behaves: an --arg of the same name wins for every processor.
                if (!commandLineArgs.containsKey(entry.getKey())) {
                    args.add("--arg-for=" + key + ":" + entry.getKey() + "=" + entry.getValue());
                }
            }
        }

        setArgs(args);
        super.exec();
    }

    /** The selected keys, split on commas, in order and without repeats, each one registered. */
    private Set<String> selectedKeys() {
        Set<String> keys = new LinkedHashSet<>();
        for (String given : getProcessorKeys()) {
            for (String key : given.split(",")) {
                String trimmed = key.trim();
                if (!trimmed.isEmpty()) {
                    keys.add(trimmed);
                }
            }
        }
        List<String> registered = getRegisteredKeys().get();
        if (keys.isEmpty()) {
            throw new InvalidUserDataException(
                    getName() + " was given no processor to run. Choosing one for you would make "
                            + "what runs depend on the order of the build script. Name them with "
                            + "--processor, e.g. `" + getName() + " --processor layout --processor docs`. "
                            + "Registered processors: " + String.join(", ", registered) + ".");
        }
        for (String key : keys) {
            if (!registered.contains(key)) {
                throw new InvalidUserDataException(
                        "No katachi processor is registered as \"" + key + "\", so " + getName()
                                + " cannot run it. Registered processors: "
                                + String.join(", ", registered) + ". Register it in "
                                + "katachi { processors { register(\"" + key + "\", ...) } }, or "
                                + "fix the spelling of --processor.");
            }
        }
        return keys;
    }
}
