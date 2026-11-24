package me.tbsten.katachi.gradle;

import java.util.Collections;

import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.Optional;
import org.gradle.process.CommandLineArgumentProvider;

/**
 * Hands {@code -Dkatachi.projectRoot}, given to Gradle, on to the JVM of a {@code Test} task and
 * of a {@code KatachiProcessorTask}, where katachi reads it as the directory to check instead of
 * the project root it finds above the working directory.
 *
 * <p>A {@code -D} on the Gradle command line only reaches the build's own JVM; a test, and a
 * processor, runs in a JVM of its own. The value is an {@link Input} of the task, so changing it
 * -- or dropping it -- makes the task run again instead of being reported {@code UP-TO-DATE}
 * with the result for the previous tree.
 *
 * <p>The value is passed as given. Whether it names an existing directory is katachi's to judge.
 *
 * <h2>Example 1: the current definition against an old checkout</h2>
 *
 * <pre>{@code
 * ./gradlew :architecture-test:test -Dkatachi.projectRoot=/tmp/old
 * // the test JVM starts with -Dkatachi.projectRoot=/tmp/old
 * }</pre>
 *
 * <h2>Example 2: the same for a processor task</h2>
 *
 * <pre>{@code
 * ./gradlew :architecture-test:katachiLayout -Dkatachi.projectRoot=/tmp/old
 * }</pre>
 */
public final class KatachiProjectRootArguments implements CommandLineArgumentProvider {

    /** The property katachi reads: the directory to check. */
    static final String PROJECT_ROOT_PROPERTY = "katachi.projectRoot";

    private final Provider<String> projectRoot;

    /**
     * @param projectRoot the build's own {@code katachi.projectRoot}, absent when not given.
     */
    KatachiProjectRootArguments(Provider<String> projectRoot) {
        this.projectRoot = projectRoot;
    }

    /**
     * The value of {@code katachi.projectRoot}, absent when it was not given.
     *
     * @return the value as given on the command line.
     */
    @Input
    @Optional
    public Provider<String> getProjectRoot() {
        return projectRoot;
    }

    /** {@inheritDoc} */
    @Override
    public Iterable<String> asArguments() {
        if (!projectRoot.isPresent()) {
            return Collections.emptyList();
        }
        return Collections.singletonList("-D" + PROJECT_ROOT_PROPERTY + "=" + projectRoot.get());
    }
}
