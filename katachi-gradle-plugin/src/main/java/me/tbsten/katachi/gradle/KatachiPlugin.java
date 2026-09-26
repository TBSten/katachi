package me.tbsten.katachi.gradle;

import java.io.File;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import org.gradle.api.InvalidUserDataException;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.plugins.JavaPluginExtension;
import org.gradle.api.tasks.SourceSet;
import org.gradle.api.tasks.TaskProvider;

/**
 * Registers one task per katachi processor on the module that holds the katachi architecture
 * definition.
 *
 * <p>A katachi definition lives in a test source set, so it can only be read by a JVM started
 * with that source set's runtime classpath. This plugin builds that JVM and nothing else: it
 * holds no knowledge of any individual processor, and it never starts a test runner.
 *
 * <p>Every processor key gets a {@link KatachiProcessorTask} named {@code katachi} followed by
 * the key with its first letter upper-cased. {@code docs} and {@code template} are registered by
 * default, so {@code katachiDocs} and {@code katachiTemplate} are always there; a
 * {@code register("layout", ...)} adds {@code katachiLayout}. A key whose task name is already
 * taken by another task stops the build at that {@code register(...)} line, rather than
 * replacing or shadowing the other task.
 *
 * <p>Applying this plugin to a module without the {@code java} plugin registers the tasks but
 * leaves their classpath empty, so a run fails at startup rather than at configuration time.
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
 * ./gradlew :architecture-test:katachiDocs
 * ./gradlew :architecture-test:tasks --group katachi
 * }</pre>
 *
 * @see KatachiProcessorTask
 */
public class KatachiPlugin implements Plugin<Project> {

    /** The task group every task of this plugin is reported under. */
    private static final String TASK_GROUP = "katachi";

    /**
     * The entry point every processor task starts, as the JVM names it.
     *
     * <p>{@code me/tbsten/katachi/processor/internal/Main.kt} of {@code me.tbsten.katachi:katachi}
     * compiles to this class. Nothing verifies the name at build time -- it is a string on
     * this side of the module boundary -- so a sample run is what catches a move.
     */
    private static final String KATACHI_MAIN_CLASS = "me.tbsten.katachi.processor.internal.MainKt";

    /** The name of the code generation task this plugin registers. */
    private static final String GENERATE_KATACHI_ENTRY_POINT_TASK_NAME = "generateKatachiEntryPoint";

    /** {@inheritDoc} */
    @Override
    public void apply(Project project) {
        // Read once, at configuration time, and carried as a plain File so that the task
        // holds no reference to the Project.
        final File projectDirectory = project.getLayout().getProjectDirectory().getAsFile();

        final KatachiExtension extension = project.getExtensions().create("katachi", KatachiExtension.class);

        // Wrapped in `project.provider { ... }` because `katachi { }` is evaluated after this
        // `apply(Project)` returns: reading `extension.getArchitecture()` right here would
        // always see the field's initial `null`, before the user's own build script had a
        // chance to call `setArchitecture(...)`.
        final TaskProvider<GenerateKatachiEntryPointTask> generateKatachiEntryPoint = project.getTasks()
                .register(GENERATE_KATACHI_ENTRY_POINT_TASK_NAME, GenerateKatachiEntryPointTask.class, task -> {
                    task.setGroup(TASK_GROUP);
                    task.setDescription(
                            "Writes the entry point every katachi processor task reads its "
                                    + "architecture and registered processors from.");
                    task.getArchitectureClassName().set(project.provider(extension::getArchitecture));
                    task.getProcessors().set(
                            project.provider(() -> extension.getProcessors().getRegistrations()));
                    task.getOutputDirectory().set(project.getLayout().getBuildDirectory()
                            .dir("generated/sources/katachi/test/kotlin"));
                });

        // Called right away for the default keys, and then from inside every `register(...)` the
        // build script makes -- so each task exists from the line that asked for it, without an
        // `afterEvaluate`, and is itself registered lazily.
        final KatachiProcessors processors = extension.getProcessors();
        final Map<String, String> keyByTaskName = new HashMap<>();
        processors.whenKeyAdded(key -> {
            final String taskName = KatachiProcessors.taskNameOf(key);
            if (project.getTasks().getNames().contains(taskName)) {
                throw taskNameTaken(key, taskName, keyByTaskName.get(taskName));
            }
            keyByTaskName.put(taskName, key);
            project.getTasks().register(taskName, KatachiProcessorTask.class, task -> {
                task.setGroup(TASK_GROUP);
                // Read when the task is realized, after the build script has run, so a
                // `register("docs", ...)` replacing a default is what the description names.
                task.setDescription(
                        "Runs the katachi processor " + processors.getRegistrations().get(key)
                                + " (registered as \"" + key + "\") on the test runtime "
                                + "classpath of this module. Pass arguments with --arg key=value.");
                task.getProcessorKey().set(key);
                // A convention, not a set: a build script's `withType<KatachiProcessorTask>()
                // .configureEach { mainClass.set(...) }` runs before this action for a task
                // registered after it, and must still win.
                task.getMainClass().convention(KATACHI_MAIN_CLASS);
                // JavaExec already resolves its working directory to the project directory.
                // Saying it here is what keeps the processor's output path (build/katachi/...)
                // from depending on a default nobody declared.
                task.setWorkingDir(projectDirectory);
                task.getArchitectureClassName().set(project.provider(extension::getArchitecture));
                task.getEntryPointClassName().convention(KatachiEntryPointSource.QUALIFIED_NAME);
                task.getConfiguredArgs().set(project.provider(() -> {
                    Map<String, String> forKey = processors.getConfiguredArgs().get(key);
                    return forKey == null ? Collections.<String, String>emptyMap() : forKey;
                }));
            });
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

            // The generated file is Kotlin, added to the *Java* source directory set of the
            // `test` source set. That is not a mistake: the Kotlin Gradle plugin (confirmed in
            // 2.4.10's `KotlinJvmCompilationWireJavaSourcesSideEffect`, registered for every
            // `KotlinJvmCompilation` -- both `kotlin("jvm")` and a KMP `jvm { }` target) wires
            // `compilation.defaultSourceSet.kotlin.srcDirs(javaSourceSet.java.sourceDirectories)`,
            // and `sourceDirectories` is a *live* `FileCollection` -- a directory added to the
            // Java source set after that wiring ran is still picked up. Since this module is
            // Java only and never touches a Kotlin Gradle plugin type directly, this is the one
            // door available to hand Kotlin sources to `compileTestKotlin` at all.
            testSourceSet.getJava().srcDir(
                    generateKatachiEntryPoint.flatMap(GenerateKatachiEntryPointTask::getOutputDirectory));

            // `configureEach` rather than a loop over what exists now: a processor registered
            // after this point still gets its classpath. `runtimeClasspath` carries the tasks
            // that produce it, and JavaExec declares `classpath` as @Classpath, so compiling the
            // tests is ordered before a run without a `dependsOn` of its own.
            project.getTasks().withType(KatachiProcessorTask.class).configureEach(task ->
                    task.setClasspath(testSourceSet.getRuntimeClasspath()));
        });

        // A safety net alongside the `srcDir(...)` above. Whether `SourceDirectorySet`'s
        // `builtBy` reliably propagates to the Kotlin compile task has varied across Gradle
        // versions in the past; declaring the dependency explicitly here costs nothing when it
        // was already implied, and is the only thing standing between a stale generated file and
        // a green `compileTestKotlin` when it was not. Matched by name rather than by type
        // because this module never depends on the Kotlin Gradle plugin.
        project.getTasks().matching(t -> "compileTestKotlin".equals(t.getName()))
                .configureEach(t -> t.dependsOn(generateKatachiEntryPoint));
    }

    /**
     * The error for a processor key whose task name another task already holds.
     *
     * @param otherKey the processor key that registered the task first, or {@code null} when the
     *     task is not one of this plugin's.
     */
    private static InvalidUserDataException taskNameTaken(String key, String taskName, String otherKey) {
        String holder = otherKey == null
                ? "this project already has a task named \"" + taskName + "\""
                : "the katachi processor key \"" + otherKey + "\" already registered the task \""
                        + taskName + "\"";
        return new InvalidUserDataException(
                "katachi processor key \"" + key + "\" would register the task \"" + taskName
                        + "\", but " + holder + ". Each processor runs as katachi<Key>, and a task "
                        + "name cannot be shared, so nothing was replaced. Register the processor "
                        + "under a different key in katachi { processors { register(\"<key>\", ...) } }, "
                        + "e.g. \"" + key + "Check\", which registers \""
                        + KatachiProcessors.taskNameOf(key + "Check") + "\".");
    }
}
