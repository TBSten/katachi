package me.tbsten.katachi.gradle;

import java.io.File;

import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.plugins.JavaPluginExtension;
import org.gradle.api.tasks.SourceSet;
import org.gradle.api.tasks.TaskProvider;

/**
 * Registers {@code runKatachiProcessor} on the module that holds the katachi architecture
 * definition.
 *
 * <p>A katachi definition lives in a test source set, so it can only be read by a JVM started
 * with that source set's runtime classpath. This plugin builds that JVM and nothing else: it
 * holds no knowledge of any individual processor, and it never starts a test runner. Which
 * processors run, and with which arguments, is the task's business rather than the plugin's.
 *
 * <p>Applying this plugin to a module without the {@code java} plugin registers the task but
 * leaves its classpath empty, so the run fails at startup rather than at configuration time.
 * Turning that into a configuration-time error is deliberately left until the plugin knows
 * what it is supposed to run.
 *
 * <h2>Example 1: apply it to the module that holds the definition</h2>
 *
 * <pre>{@code
 * // architecture-test/build.gradle.kts
 * plugins {
 *     kotlin("jvm")
 *     id("me.tbsten.katachi")
 * }
 * }</pre>
 *
 * <h2>Example 2: run it</h2>
 *
 * <pre>{@code
 * ./gradlew :architecture-test:runKatachiProcessor
 * }</pre>
 *
 * @see RunKatachiProcessorTask
 */
public class KatachiPlugin implements Plugin<Project> {

    /** The name of the one task this plugin registers. */
    static final String RUN_KATACHI_PROCESSOR_TASK_NAME = "runKatachiProcessor";

    /** The task group every task of this plugin is reported under. */
    static final String TASK_GROUP = "katachi";

    /**
     * The entry point the task starts, as the JVM names it.
     *
     * <p>{@code me/tbsten/katachi/processor/Main.kt} of {@code me.tbsten.katachi:katachi}
     * compiles to this class. Nothing verifies the name at build time -- it is a string on
     * this side of the module boundary -- so a sample run is what catches a move.
     */
    static final String KATACHI_MAIN_CLASS = "me.tbsten.katachi.processor.MainKt";

    /** {@inheritDoc} */
    @Override
    public void apply(Project project) {
        // Read once, at configuration time, and carried as a plain File so that the task
        // holds no reference to the Project.
        final File projectDirectory = project.getLayout().getProjectDirectory().getAsFile();

        final TaskProvider<RunKatachiProcessorTask> runKatachiProcessor = project.getTasks()
                .register(RUN_KATACHI_PROCESSOR_TASK_NAME, RunKatachiProcessorTask.class, task -> {
                    task.setGroup(TASK_GROUP);
                    task.setDescription(
                            "Runs a katachi processor on the test runtime classpath of the "
                                    + "module this plugin is applied to.");
                    task.getMainClass().set(KATACHI_MAIN_CLASS);
                    // JavaExec already resolves its working directory to the project
                    // directory. Saying it here is what keeps the processor's output path
                    // (build/katachi/...) from depending on a default nobody declared.
                    task.setWorkingDir(projectDirectory);
                });

        // `sourceSets` belongs to the java plugin, which `kotlin("jvm")` applies. Reading it
        // from inside `withPlugin` is what makes the order of the user's plugins { } block
        // irrelevant.
        project.getPluginManager().withPlugin("java", appliedPlugin -> {
            // `JavaPluginExtension.getSourceSets()` exists since Gradle 7.1 and is the
            // successor of `JavaPluginConvention`, which Gradle 9 removed along with
            // `Project.getConvention()`. This is the one spelling that compiles and runs on
            // both ends of the supported range.
            final JavaPluginExtension java = project.getExtensions().getByType(JavaPluginExtension.class);
            final SourceSet testSourceSet = java.getSourceSets().getByName(SourceSet.TEST_SOURCE_SET_NAME);

            runKatachiProcessor.configure(task ->
                    // `runtimeClasspath` carries the tasks that produce it, and JavaExec
                    // declares `classpath` as @Classpath, so compiling the tests is ordered
                    // before this run without a `dependsOn` of its own.
                    task.setClasspath(testSourceSet.getRuntimeClasspath()));
        });
    }
}
