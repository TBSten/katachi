package me.tbsten.katachi.gradle;

import java.io.File;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.gradle.api.InvalidUserDataException;
import org.gradle.api.Task;
import org.gradle.api.provider.MapProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.specs.Spec;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.JavaExec;
import org.gradle.api.tasks.Optional;
import org.gradle.api.tasks.OutputDirectory;
import org.gradle.api.tasks.options.Option;
import org.gradle.work.DisableCachingByDefault;

/**
 * Runs one registered katachi processor: starts a JVM on the applying module's test runtime
 * classpath and hands katachi's entry point the processor this task was registered for.
 *
 * <p>{@link KatachiPlugin} registers one of these per processor key, named {@code katachi}
 * followed by the key with its first letter upper-cased: {@code docs} runs as
 * {@code katachiDocs}, {@code template} as {@code katachiTemplate}, and a
 * {@code register("layout", ...)} as {@code katachiLayout}. Which processor a task runs is fixed
 * when it is registered; the command line only adds {@code --arg}.
 *
 * <p>It is a {@link JavaExec} rather than a {@code Test} task on purpose. A {@code Test} task
 * starts a test engine, and a run that discovers no tests reports success without having done
 * anything; this one starts one class and nothing else. Nothing on this path reads a test
 * engine, so {@code build/test-results/} stays absent after a run -- which is the mechanical
 * way to tell the two apart.
 *
 * <p>It runs every time unless its processor declared, through
 * {@link KatachiProcessors#outputs}, that it reads no file of the project and where it writes.
 * Only then is {@link #getCacheableOutputDirectory()} present, and only then may Gradle call the
 * task up to date or restore it from the build cache: its inputs are the test runtime classpath
 * (the definition) and the arguments, which Gradle already tracks. A processor that walks the
 * project has every project file as an input, which is not declared anywhere, so it is never
 * skipped.
 *
 * <p>{@code ignoreExitValue} is left at its default of {@code false}, and nothing here may set
 * it to {@code true}. That default is what makes the entry point's failures visible: an
 * uncaught exception ends the JVM with a non-zero status, and {@link JavaExec} turns a non-zero
 * status into a failed task.
 *
 * <h2>Example 1: run one processor with an argument</h2>
 *
 * <pre>{@code
 * ./gradlew :architecture-test:katachiTemplate --arg roleName=UseCase --arg name=GetUser
 * }</pre>
 *
 * <h2>Example 2: run two processors in one build</h2>
 *
 * <pre>{@code
 * # Gradle attaches an option to the task right before it, so each task takes its own --arg.
 * ./gradlew :architecture-test:katachiLayout :architecture-test:katachiDocs --arg mode=check
 * }</pre>
 *
 * <h2>Example 3: point every processor task at a different entry point from a build script</h2>
 *
 * <pre>{@code
 * import me.tbsten.katachi.gradle.KatachiProcessorTask
 *
 * tasks.withType<KatachiProcessorTask>().configureEach {
 *     mainClass.set("com.example.MyRunnerKt")
 * }
 * }</pre>
 *
 * @see KatachiPlugin
 */
// Stated on this type rather than left to the one JavaExec carries. `java-gradle-plugin` runs
// `validatePlugins` with stricter validation, which asks every task type to say whether its
// output may be cached; saying it here keeps the answer independent of whether Gradle treats
// the supertype's annotation as inherited. It is also the default answer: what an arbitrary
// processor writes, and what it reads, is not known to the plugin. The `cacheIf` in the
// constructor switches caching back on for a processor that declared both.
@DisableCachingByDefault(because = "Runs an arbitrary processor whose outputs are not declared")
public abstract class KatachiProcessorTask extends JavaExec {

    private List<String> processorArgs = new ArrayList<>();

    /** Wires the one condition under which a run may be skipped or come from the build cache. */
    public KatachiProcessorTask() {
        getReadsProjectFiles().convention(true);
        // Needed as well as the null output: an @OutputDirectory property that is present on the
        // type but null still counts as "outputs declared", which alone would let Gradle call a
        // run with no output files up to date.
        getOutputs().upToDateWhen(new HasCacheableOutput());
        getOutputs().cacheIf(
                "the processor declared that it reads no project file, and where it writes",
                new HasCacheableOutput());
    }

    /** A processor argument as {@code key=value}. Repeatable; may itself contain {@code =}. */
    @Input
    public List<String> getProcessorArgs() {
        return processorArgs;
    }

    /**
     * Sets the {@code --arg} values of this run.
     *
     * <p>A plain {@code List<String>} with a setter rather than a {@code ListProperty}: Gradle
     * 8.0 does not recognise {@code @Option} on a {@code ListProperty} as taking an argument at
     * all, and answers {@code --arg} with "does not take an argument". A setter taking a
     * {@code List<String>} has been the repeatable form since long before 8.0.
     */
    @Option(option = "arg", description = "Processor argument as key=value. Repeatable.")
    public void setProcessorArgs(List<String> processorArgs) {
        this.processorArgs = processorArgs;
    }

    /** The registered key of the one processor this task runs. Set by {@link KatachiPlugin}. */
    @Input
    public abstract Property<String> getProcessorKey();

    /** Set by {@link KatachiPlugin} from {@code katachi { architecture = ... } }. */
    @Input
    @Optional
    public abstract Property<String> getArchitectureClassName();

    /** Set by {@link KatachiPlugin} to {@link KatachiEntryPointSource#QUALIFIED_NAME}. */
    @Input
    public abstract Property<String> getEntryPointClassName();

    /**
     * What this task's processor was given in {@code build.gradle.kts}.
     *
     * <p>Filled by {@link KatachiPlugin} from the {@code katachi { }} block: the typed
     * {@code docs { }} and {@code template { }} blocks, and the string form
     * {@code processors { args("key") { } }}. A {@code --arg} of the same name wins.
     */
    @Input
    public abstract MapProperty<String, String> getConfiguredArgs();

    /**
     * Whether the processor reads files of the project. Set by {@link KatachiPlugin} from
     * {@code outputs(...)}; {@code true} unless declared otherwise.
     */
    @Input
    public abstract Property<Boolean> getReadsProjectFiles();

    /**
     * The directory the processor writes when {@link #getOutputDirArg()} is not given, relative
     * to the working directory or absolute. Set by {@link KatachiPlugin}.
     *
     * <p>{@code @Internal}: where the output lands is already tracked through
     * {@link #getCacheableOutputDirectory()}, and a path here would only tie the cache key to
     * one machine.
     */
    @Internal
    public abstract Property<String> getDeclaredOutputDir();

    /** The argument that replaces {@link #getDeclaredOutputDir()}. Set by {@link KatachiPlugin}. */
    @Input
    @Optional
    public abstract Property<String> getOutputDirArg();

    /**
     * Argument values under which the processor writes nothing, such as {@code mode=check} for
     * {@code docs}. Set by {@link KatachiPlugin}.
     */
    @Input
    public abstract MapProperty<String, String> getWritesNothingWhen();

    /**
     * The directory this run writes, when Gradle may skip or cache the run; {@code null}
     * otherwise.
     *
     * <p>{@code null} -- no output at all, so the task runs every time -- when the processor may
     * read the project, declared no output directory, or is given an argument under which it
     * writes nothing. The last one matters for {@code docs --arg mode=check}: a check that
     * compares files on disk would otherwise be called up to date without comparing them.
     *
     * @return the output directory resolved against the working directory, or {@code null}.
     */
    @OutputDirectory
    @Optional
    public File getCacheableOutputDirectory() {
        if (getReadsProjectFiles().getOrElse(true)) {
            return null;
        }
        for (Map.Entry<String, String> entry : getWritesNothingWhen().get().entrySet()) {
            if (entry.getValue().equals(argValue(entry.getKey()))) {
                return null;
            }
        }
        String path = getOutputDirArg().isPresent() ? argValue(getOutputDirArg().get()) : null;
        if (path == null) {
            path = getDeclaredOutputDir().getOrNull();
        }
        if (path == null) {
            return null;
        }
        File file = new File(path);
        File resolved = file.isAbsolute() ? file : new File(getWorkingDir(), path);
        return resolved.toPath().normalize().toFile();
    }

    /**
     * The value this run gives the argument {@code name}: the command line's first, then the
     * build script's, the order {@link #exec()} sends them in. A malformed {@code --arg} is
     * skipped here; {@link #exec()} is the one that reports it.
     */
    private String argValue(String name) {
        String prefix = name + "=";
        for (String rawArg : getProcessorArgs()) {
            if (rawArg.startsWith(prefix)) {
                return rawArg.substring(prefix.length());
            }
        }
        return getConfiguredArgs().get().get(name);
    }

    /**
     * The {@code upToDateWhen} and {@code cacheIf} condition. A named class rather than a lambda, because the
     * configuration cache stores the spec and a plain Java lambda is not serializable.
     */
    static final class HasCacheableOutput implements Spec<Task> {
        @Override
        public boolean isSatisfiedBy(Task task) {
            return task instanceof KatachiProcessorTask
                    && ((KatachiProcessorTask) task).getCacheableOutputDirectory() != null;
        }
    }

    /**
     * Validates this run's configuration and builds the argv the entry point reads, then starts
     * the JVM.
     *
     * <p>Validation happens here, in the task, rather than inside the entry point's own {@code
     * main()} on {@code :katachi}. These are mistakes in the Gradle command line the user typed,
     * so failing before a JVM is even started reports the error as Gradle's own -- no stack
     * trace, no JVM startup cost, and the message is the first thing on the screen rather than
     * buried under {@code JavaExec}'s "process finished with non-zero exit value" noise.
     *
     * @throws InvalidUserDataException when {@code katachi { architecture = ... } } was never
     *     set, or when some {@code --arg} does not contain {@code =}, has an empty key before the
     *     first {@code =}, or repeats a key already given.
     */
    @Override
    public void exec() {
        if (!getArchitectureClassName().isPresent()) {
            throw new InvalidUserDataException(
                    "katachi { architecture = ... } is not set in this module's "
                            + "build.gradle.kts. Add it, e.g. "
                            + "`katachi { architecture = \"com.example.projectArchitecture\" }`, "
                            + "then run " + getName() + " again.");
        }

        List<String> args = new ArrayList<>();
        args.add("--entry-point=" + getEntryPointClassName().get());
        args.add("--processor=" + getProcessorKey().get());

        // The command line wins over what the build script configured, so `--arg` stays the way
        // to vary one run without editing the build. Collected first for that reason: a key seen
        // here is not overwritten below.
        Set<String> seenArgKeys = new LinkedHashSet<>();
        for (String rawArg : getProcessorArgs()) {
            int separatorIndex = rawArg.indexOf('=');
            if (separatorIndex <= 0) {
                throw new InvalidUserDataException(
                        "Invalid --arg \"" + rawArg + "\" for " + getName() + ". Expected --arg "
                                + "key=value, e.g. --arg roleName=GetUser.");
            }
            String key = rawArg.substring(0, separatorIndex);
            if (!seenArgKeys.add(key)) {
                throw new InvalidUserDataException(
                        "--arg key \"" + key + "\" (\"" + rawArg + "\") was given more than "
                                + "once to " + getName() + ". Each --arg key may be passed only "
                                + "once.");
            }
            args.add("--arg=" + rawArg);
        }

        for (Map.Entry<String, String> entry : getConfiguredArgs().get().entrySet()) {
            if (!seenArgKeys.contains(entry.getKey())) {
                args.add("--arg=" + entry.getKey() + "=" + entry.getValue());
            }
        }

        setArgs(args);
        super.exec();
    }
}
