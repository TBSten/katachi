package me.tbsten.katachi.gradle;

import java.util.Collections;

import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.Optional;
import org.gradle.process.CommandLineArgumentProvider;

/**
 * Hands {@code -Dkatachi.maxViolations}, given to Gradle, on to the JVM of a {@code Test} task
 * and of a {@code KatachiProcessorTask}, where katachi reads it to decide how many blocks a
 * report spells out.
 *
 * <p>A {@code -D} on the Gradle command line only reaches the build's own JVM; a test, and a
 * processor, runs in a JVM of its own. The value is an {@link Input} of the task, so changing it
 * -- or dropping it -- makes the task run again instead of being reported {@code UP-TO-DATE}
 * with the previous budget. Unlike {@link KatachiBaselineArguments} nothing keeps the task from
 * being {@code UP-TO-DATE} when the same value is given twice: the property only changes what
 * the report looks like, not what is written anywhere, and a failing test is never up to date.
 *
 * <p>The value is passed as given. Whether it is {@code all} or a whole number is katachi's to
 * judge, and katachi warns on standard error about anything else.
 *
 * <h2>Example 1: every block of the architecture test's report</h2>
 *
 * <pre>{@code
 * ./gradlew :architecture-test:test -Dkatachi.maxViolations=all
 * // the test JVM starts with -Dkatachi.maxViolations=all
 * }</pre>
 *
 * <h2>Example 2: the same for a processor task</h2>
 *
 * <pre>{@code
 * ./gradlew :architecture-test:katachiLayout -Dkatachi.maxViolations=all
 * }</pre>
 */
public final class KatachiMaxViolationsArguments implements CommandLineArgumentProvider {

    /** The property katachi reads: {@code all}, or a whole number of 0 or more. */
    static final String MAX_VIOLATIONS_PROPERTY = "katachi.maxViolations";

    private final Provider<String> maxViolations;

    /**
     * @param maxViolations the build's own {@code katachi.maxViolations}, absent when not given.
     */
    KatachiMaxViolationsArguments(Provider<String> maxViolations) {
        this.maxViolations = maxViolations;
    }

    /**
     * The value of {@code katachi.maxViolations}, absent when it was not given.
     *
     * @return the value as given on the command line.
     */
    @Input
    @Optional
    public Provider<String> getMaxViolations() {
        return maxViolations;
    }

    /** {@inheritDoc} */
    @Override
    public Iterable<String> asArguments() {
        // Nothing is passed when nothing was given, so the JVM sees exactly what it would have
        // seen without the plugin.
        if (!maxViolations.isPresent()) {
            return Collections.emptyList();
        }
        return Collections.singletonList("-D" + MAX_VIOLATIONS_PROPERTY + "=" + maxViolations.get());
    }
}
