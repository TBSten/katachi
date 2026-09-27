package me.tbsten.katachi.gradle;

import java.util.ArrayList;
import java.util.List;

import org.gradle.api.Task;
import org.gradle.api.provider.Provider;
import org.gradle.api.specs.Spec;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.Optional;
import org.gradle.process.CommandLineArgumentProvider;

/**
 * Hands {@code -Dkatachi.baseline.update} and {@code -Dkatachi.baseline.prune}, given to Gradle,
 * on to the JVM of a {@code Test} task, where katachi's {@code assert()} reads them.
 *
 * <p>A {@code -D} on the Gradle command line only reaches the build's own JVM; a test runs in a
 * JVM of its own. Each value is an {@link Input} of the task, so changing it -- or dropping it --
 * makes the test run again instead of being reported {@code UP-TO-DATE} without the baseline
 * having been touched. That is also why these are system properties rather than environment
 * variables: an environment variable is not an input of the task. While either is given, the
 * plugin also keeps the task from being {@code UP-TO-DATE} or taken from the build cache
 * ({@link #requested()}), so giving the same value twice in a row runs the test twice.
 *
 * <h2>Example 1: what the plugin does for every Test task</h2>
 *
 * <pre>{@code
 * ./gradlew :architecture-test:test -Dkatachi.baseline.update=true
 * // the test JVM starts with -Dkatachi.baseline.update=true
 * }</pre>
 */
public final class KatachiBaselineArguments implements CommandLineArgumentProvider {

    /** The property that rebuilds the baseline from the run. */
    static final String UPDATE_PROPERTY = "katachi.baseline.update";

    /** The property that only removes what was fixed from the baseline. */
    static final String PRUNE_PROPERTY = "katachi.baseline.prune";

    private final Provider<String> update;
    private final Provider<String> prune;

    /**
     * @param update the build's own {@code katachi.baseline.update}, absent when not given.
     * @param prune the build's own {@code katachi.baseline.prune}, absent when not given.
     */
    KatachiBaselineArguments(Provider<String> update, Provider<String> prune) {
        this.update = update;
        this.prune = prune;
    }

    /**
     * The value of {@code katachi.baseline.update}, absent when it was not given.
     *
     * @return the value as given on the command line.
     */
    @Input
    @Optional
    public Provider<String> getUpdate() {
        return update;
    }

    /**
     * The value of {@code katachi.baseline.prune}, absent when it was not given.
     *
     * @return the value as given on the command line.
     */
    @Input
    @Optional
    public Provider<String> getPrune() {
        return prune;
    }

    /**
     * Whether either property was given, whatever its value, as a condition on a task.
     *
     * @return a spec that holds while {@code katachi.baseline.update} or
     *     {@code katachi.baseline.prune} is given.
     */
    Spec<Task> requested() {
        return new Requested(update, prune, true);
    }

    /**
     * The opposite of {@link #requested()}, for {@code outputs.upToDateWhen}.
     *
     * @return a spec that holds while neither property is given.
     */
    Spec<Task> notRequested() {
        return new Requested(update, prune, false);
    }

    /**
     * {@link #requested()} as a class of its own rather than a lambda: the configuration cache
     * stores it with the task, and a Java lambda cannot be stored unless it is serializable.
     */
    private static final class Requested implements Spec<Task> {
        private final Provider<String> update;
        private final Provider<String> prune;
        private final boolean whenGiven;

        Requested(Provider<String> update, Provider<String> prune, boolean whenGiven) {
            this.update = update;
            this.prune = prune;
            this.whenGiven = whenGiven;
        }

        @Override
        public boolean isSatisfiedBy(Task task) {
            boolean given = update.isPresent() || prune.isPresent();
            return given == whenGiven;
        }
    }

    /** {@inheritDoc} */
    @Override
    public Iterable<String> asArguments() {
        List<String> arguments = new ArrayList<>();
        // Nothing is passed when nothing was given, so a test JVM sees exactly what it would
        // have seen without the plugin.
        if (update.isPresent()) {
            arguments.add("-D" + UPDATE_PROPERTY + "=" + update.get());
        }
        if (prune.isPresent()) {
            arguments.add("-D" + PRUNE_PROPERTY + "=" + prune.get());
        }
        return arguments;
    }
}
