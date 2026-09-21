// =============================================================================
// This file exists for ONE purpose: aggregating the sample builds under sample/.
//
// The katachi library itself is `:katachi`. Do not add plugins, dependencies or
// sources to this root project. `./gradlew check` has to keep working on a
// machine that has neither an Android SDK nor a Kotlin/Native toolchain, and the
// only thing that guarantees that is an empty root project.
// =============================================================================
//
// Why each sample is driven through its own wrapper instead of being part of
// this build:
//
//   * `includeBuild("sample/<name>")` in settings.gradle.kts would make every
//     root invocation configure every sample, so `./gradlew :katachi:check`
//     would start requiring the Android SDK and the Kotlin/Native distribution.
//   * It would also stop the samples from exercising the code path a real user
//     takes: a separate build that resolves `me.tbsten.katachi:katachi` as an
//     external module and gets it substituted by `includeBuild("../..")`.
//   * A `GradleBuild` task runs the nested build inside the same build tree, and
//     Gradle then rejects the sample's own `includeBuild("../..")` with
//     "Cannot include build 'katachi' in build ':jvm'. This is not supported yet."
//
// An `Exec` task per sample has none of those problems and stays compatible with
// the configuration cache.

/**
 * One standalone Gradle build under `sample/`.
 *
 * @property name directory name under `sample/`, also the task name suffix.
 * @property defaultTasks tasks invoked through that sample's own wrapper.
 * @property needsAndroidSdk whether the build fails without an Android SDK location.
 */
data class SampleBuild(
    val name: String,
    val defaultTasks: List<String>,
    val needsAndroidSdk: Boolean,
)

val sampleBuilds = listOf(
    // `check` without a project path runs it in every project of the build, so
    // `:architecture-test:test` -- the katachi verification -- is included.
    SampleBuild("jvm", listOf("check"), needsAndroidSdk = false),
    // `check` here includes Android Lint over nine modules. Measured on this
    // sample: 14 s warm, 21 s with `clean --no-build-cache`, so there is no
    // reason to narrow it down to the unit tests. Revisit if the sample grows.
    SampleBuild("android", listOf("check"), needsAndroidSdk = true),
    // Deliberately NOT `check` / `build` / `assemble`, and there is no `jvmTest`
    // in this sample. Its modules declare iosArm64 / iosSimulatorArm64, so the
    // lifecycle tasks drag `compileKotlinIosArm64` and the Kotlin/Native
    // distribution download into the task graph, neither of which works on a
    // Linux runner. Neither task below ever reaches an Apple task.
    //
    // Two tasks, because they verify two different things and neither implies
    // the other:
    //   * `:architecture-test:test` is the katachi verification. It moved out of
    //     `:app:android` into a module of its own, so running only the Android
    //     unit tests would let every katachi assertion silently stop running.
    //   * `:app:android:testDebugUnitTest` is what proves the sample still
    //     compiles as a Kotlin Multiplatform project. `:architecture-test` is a
    //     plain JVM module that references none of `:ui` / `:data` / `:feature:*`.
    SampleBuild(
        "kmp",
        listOf(":architecture-test:test", ":app:android:testDebugUnitTest"),
        needsAndroidSdk = true,
    ),
)

/**
 * Tasks to run inside `sample/[sample]`.
 *
 * Override for one sample with `-Pkatachi.sample.<name>.task=...`, or for all of
 * them with `-Pkatachi.sample.task=...`. Several tasks are separated by spaces.
 */
fun sampleTasksOf(sample: SampleBuild): List<String> =
    providers.gradleProperty("katachi.sample.${sample.name}.task")
        .orElse(providers.gradleProperty("katachi.sample.task"))
        .orNull
        ?.split(" ")
        ?.filter { it.isNotBlank() }
        ?.takeIf { it.isNotEmpty() }
        ?: sample.defaultTasks

private val gradlewCommand: List<String> =
    if (providers.systemProperty("os.name").get().lowercase().startsWith("windows")) {
        listOf("cmd", "/c", "gradlew.bat")
    } else {
        listOf("./gradlew")
    }

/**
 * Android SDK location taken from the environment, which is how CI provides it
 * (`ANDROID_HOME` is preset on the GitHub-hosted runners). `null` when neither
 * variable is set.
 */
private val androidSdkFromEnvironment: String? =
    providers.environmentVariable("ANDROID_HOME")
        .orElse(providers.environmentVariable("ANDROID_SDK_ROOT"))
        .orNull
        ?.takeIf { it.isNotBlank() }

/** Default install locations of the Android SDK, used only as a last resort. */
private val wellKnownAndroidSdkDirs: List<File> =
    providers.systemProperty("user.home").get().let { home ->
        listOf(
            File(home, "Library/Android/sdk"), // macOS / Android Studio
            File(home, "Android/Sdk"), // Linux / Android Studio
        )
    }

val checkSamples = tasks.register("checkSamples") {
    group = LifecycleBasePlugin.VERIFICATION_GROUP
    description = "Runs every standalone sample build under sample/ through its own wrapper. " +
        "sample/android and sample/kmp need an Android SDK: set ANDROID_HOME (or ANDROID_SDK_ROOT), " +
        "or write sdk.dir into sample/<name>/local.properties."
}

/**
 * Every `checkSample*` task registered so far.
 *
 * Each new task is ordered after *all* of them, not just the one before it:
 * `mustRunAfter` does not chain transitively, so a pairwise chain falls apart as
 * soon as a sample is missing from the task graph (`./gradlew checkSampleJvm
 * checkSampleKmp`, or `./gradlew checkSamples -x checkSampleAndroid`).
 */
val registeredSamples = mutableListOf<TaskProvider<Exec>>()

sampleBuilds.forEach { sample ->
    val suffix = sample.name.replaceFirstChar { it.uppercaseChar() }
    val sampleTasks = sampleTasksOf(sample)
    val sampleDir = layout.projectDirectory.dir("sample/${sample.name}").asFile
    // Captured eagerly: by the time the configuration block below runs,
    // `registeredSamples` would already contain this very task.
    val predecessors = registeredSamples.toList()

    val task = tasks.register<Exec>("checkSample$suffix") {
        group = LifecycleBasePlugin.VERIFICATION_GROUP
        description = buildString {
            append(
                "Runs `${sampleTasks.joinToString(" ")}` in the standalone sample build " +
                    "sample/${sample.name}.",
            )
            if (sample.needsAndroidSdk) {
                append(" Needs an Android SDK: set ANDROID_HOME (or ANDROID_SDK_ROOT),")
                append(" or write sdk.dir into sample/${sample.name}/local.properties.")
            }
        }
        workingDir = sampleDir
        commandLine(gradlewCommand + sampleTasks + listOf("--console=plain"))

        // The nested build inherits this process's environment, so ANDROID_HOME
        // set by the developer or by CI already reaches it. This only covers the
        // remaining case: a developer machine with a standard Android Studio SDK
        // but no environment variable and no local.properties. The existence
        // check is not a tracked configuration-cache input, so after creating
        // local.properties run once with `--no-configuration-cache`.
        if (sample.needsAndroidSdk &&
            androidSdkFromEnvironment == null &&
            !File(sampleDir, "local.properties").exists()
        ) {
            wellKnownAndroidSdkDirs.firstOrNull { it.isDirectory }
                ?.let { environment("ANDROID_HOME", it.absolutePath) }
        }

        // All three samples include the same katachi build and therefore share
        // katachi's build/ directory. Running two of them concurrently corrupts
        // it, so they are kept strictly sequential -- whichever subset of the
        // sample tasks ends up in the task graph.
        mustRunAfter(predecessors)

        // The nested build compiles `:katachi` through `includeBuild("../..")`
        // and writes to that same katachi/build/. The outer build's own katachi
        // tasks are therefore just as much a conflict as another sample, and
        // nothing stops `./gradlew check checkSamples` from running both at
        // once (the two builds use different project caches, so Gradle's own
        // locking does not apply). These paths are resolved lazily and are
        // simply ignored when the task is not in the graph, so running a
        // `checkSample*` task on its own is unaffected.
        mustRunAfter(":katachi:check", ":katachi:build", ":katachi:jar", ":katachi:test")
    }
    registeredSamples += task
    checkSamples.configure { dependsOn(task) }
}
