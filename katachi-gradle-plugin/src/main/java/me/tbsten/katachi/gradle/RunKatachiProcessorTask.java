package me.tbsten.katachi.gradle;

import org.gradle.api.tasks.JavaExec;
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
 * <p>A type of its own rather than a plain {@link JavaExec} because the {@code --processor} and
 * {@code --arg} command line options land here later; declaring the type now means the task
 * does not have to change type when they do.
 *
 * <h2>Example 1: point the task at a different entry point from a build script</h2>
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
    // TODO(v0.2 step 2): @Option("processor") Property<String> and @Option("arg")
    //  ListProperty<String>, plus the CLI that decodes them.
}
