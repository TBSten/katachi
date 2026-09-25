plugins {
    id("org.jetbrains.dokka")
}

dokka {
    moduleName.set(rootProject.name)

    dokkaPublications.named("html") {
        outputDirectory.set(layout.projectDirectory.dir("docs/public/api-docs"))
    }
}

dependencies {
    dokka(project(":katachi"))
    dokka(project(":katachi-konsist"))
}

tasks.register("generateApiDocs") {
    group = "documentation"
    description = "Aggregates the :katachi and :katachi-konsist Dokka HTML into " +
            "docs/public/api-docs/ for the docs site's API reference page."
    dependsOn("dokkaGenerateHtml")
}

/**
 * A `template { }` run against a sample, checked the way a user would meet it: generate, then
 * the sample's own `assert()` test and a compile of the generated sources, then delete what was
 * generated.
 *
 * [generatedFiles] are relative to the sample and are the *only* paths the clean-up deletes.
 * They restate what the template and the layout decide together, so the check task refuses to
 * run when one of them is missing -- a list out of step with the template fails loudly instead
 * of leaving files behind.
 */
data class SampleTemplate(
    val args: List<String>,
    val generatedFiles: List<String>,
    val verifyTasks: List<String>,
)

data class SampleBuild(
    val name: String,
    val defaultTasks: List<String>,
    val needsAndroidSdk: Boolean,
    val template: SampleTemplate? = null,
)

val sampleBuilds = listOf(
    SampleBuild(
        "jvm",
        listOf(
            "check",
            "runKatachiProcessor",
            "--processor=layout,roleNames,docs",
            "--arg",
            "prefix=domain",
            "--arg",
            "mode=check",
        ),
        needsAndroidSdk = false,
        template = SampleTemplate(
            args = listOf("roleName=Service", "name=KatachiSmoke"),
            generatedFiles = listOf("src/main/kotlin/com/example/service/KatachiSmokeService.kt"),
            verifyTasks = listOf(
                ":architecture-test:test",
                "--tests",
                "com.example.ProjectArchitectureTest",
                "--rerun",
                ":compileKotlin",
            ),
        ),
    ),
    SampleBuild(
        "android",
        listOf("check", "runKatachiProcessor", "--processor=layout,docs", "--arg", "mode=check"),
        needsAndroidSdk = true,
        template = SampleTemplate(
            args = listOf("roleName=Component", "name=KatachiSmoke"),
            generatedFiles = listOf("ui/src/main/kotlin/com/example/sample/ui/component/AppKatachiSmoke.kt"),
            verifyTasks = listOf(
                ":architecture-test:test",
                "--tests",
                "com.example.sample.ProjectArchitectureTest",
                "--rerun",
                ":ui:compileDebugKotlin",
            ),
        ),
    ),
    SampleBuild(
        "kmp",
        listOf(
            ":architecture-test:test",
            ":app:android:testDebugUnitTest",
            ":architecture-test:runKatachiProcessor",
            "--processor=layout,docs",
            "--arg",
            "mode=check",
        ),
        needsAndroidSdk = true,
        template = SampleTemplate(
            args = listOf("roleName=Repository", "name=KatachiSmoke"),
            generatedFiles = listOf(
                "data/src/commonMain/kotlin/com/example/kmp/data/user/KatachiSmokeRepository.kt",
                "data/src/commonMain/kotlin/com/example/kmp/data/user/KatachiSmokeRepositoryImpl.kt",
            ),
            verifyTasks = listOf(
                ":architecture-test:test",
                "--tests",
                "com.example.kmp.ProjectLayoutSpec",
                "--rerun",
                ":data:compileAndroidMain",
            ),
        ),
    ),
    SampleBuild(
        "custom-processor",
        listOf(
            "check",
            "runKatachiProcessor",
            "--processor=roleFileCount,roleTable,roleDocCoverage,docs",
            "--arg",
            "groups=core,testing",
            "--arg",
            "sortBy=Declaration",
            "--arg",
            "mode=check",
        ),
        needsAndroidSdk = false,
    ),
)

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

private val androidSdkFromEnvironment: String? =
    providers.environmentVariable("ANDROID_HOME")
        .orElse(providers.environmentVariable("ANDROID_SDK_ROOT"))
        .orNull
        ?.takeIf { it.isNotBlank() }

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
 * Every task that runs a sample's wrapper, in the order they must run in. A sample build and
 * the root build share katachi's own `build/` through the composite build, so no two of them
 * may run at once -- and the configuration cache lets tasks of one project run in parallel.
 */
val registeredSamples = mutableListOf<TaskProvider<out Task>>()

/** Keeps [this] away from the root build's own katachi tasks, for the reason above. */
fun Task.runsAfterRootBuilds() {
    mustRunAfter(":katachi:check", ":katachi:build", ":katachi:jar", ":katachi:test")
    mustRunAfter(
        ":katachi-konsist:check",
        ":katachi-konsist:build",
        ":katachi-konsist:jar",
        ":katachi-konsist:test",
    )

    mustRunAfter(
        ":katachi-gradle-plugin:check",
        ":katachi-gradle-plugin:build",
        ":katachi-gradle-plugin:jar",
        ":katachi-gradle-plugin:test",
    )

    mustRunAfter(":architecture-test:test")
}

/** Runs [tasks] through the wrapper of the sample in [sampleDir]. */
fun Exec.runSampleWrapper(sample: SampleBuild, sampleDir: File, tasks: List<String>) {
    workingDir = sampleDir
    commandLine(gradlewCommand + tasks + listOf("--console=plain"))

    if (sample.needsAndroidSdk &&
        androidSdkFromEnvironment == null &&
        !File(sampleDir, "local.properties").exists()
    ) {
        wellKnownAndroidSdkDirs.firstOrNull { it.isDirectory }
            ?.let { environment("ANDROID_HOME", it.absolutePath) }
    }
    runsAfterRootBuilds()
}

sampleBuilds.forEach { sample ->
    val suffix = sample.name.split("-").joinToString("") { part ->
        part.replaceFirstChar { it.uppercaseChar() }
    }
    val sampleTasks = sampleTasksOf(sample)
    val sampleDir = layout.projectDirectory.dir("sample/${sample.name}").asFile
    val predecessors = registeredSamples.toList()
    val sdkNote = if (sample.needsAndroidSdk) {
        " Needs an Android SDK: set ANDROID_HOME (or ANDROID_SDK_ROOT)," +
            " or write sdk.dir into sample/${sample.name}/local.properties."
    } else {
        ""
    }
    val template = sample.template
    // Read inside doFirst { } instead of `sample`, so the configuration cache has only a
    // String to store.
    val sampleName = sample.name

    // A sample without a template keeps checkSample<Name> as the one Exec it always was.
    // With one, that name becomes the lifecycle task over the build and the template run.
    val buildTaskName = if (template == null) "checkSample$suffix" else "checkSample${suffix}Build"
    val buildTask = tasks.register<Exec>(buildTaskName) {
        group = LifecycleBasePlugin.VERIFICATION_GROUP
        description = "Runs `${sampleTasks.joinToString(" ")}` in the standalone sample build " +
            "sample/${sample.name}.$sdkNote"
        runSampleWrapper(sample, sampleDir, sampleTasks)
        mustRunAfter(predecessors)
    }
    registeredSamples += buildTask

    if (template == null) {
        checkSamples.configure { dependsOn(buildTask) }
        return@forEach
    }

    val generatedFiles = template.generatedFiles.map { File(sampleDir, it) }
    // Written just before generating and read by the clean-up: without it, a generation that
    // refused to start because the files were already there would have them deleted anyway.
    val marker = layout.buildDirectory.file("sample-template/${sample.name}.generated").get().asFile
    val generateArgs = listOf(":architecture-test:runKatachiProcessor", "--processor=template") +
        template.args.flatMap { listOf("--arg", it) }

    val deleteTask = tasks.register<Delete>("deleteSample${suffix}Template") {
        group = LifecycleBasePlugin.VERIFICATION_GROUP
        description = "Deletes the files generateSample${suffix}Template wrote into " +
            "sample/${sample.name}, and nothing else."
        onlyIf("generateSample${suffix}Template started generating") { marker.isFile }
        delete(generatedFiles + marker)
    }

    val generateTask = tasks.register<Exec>("generateSample${suffix}Template") {
        group = LifecycleBasePlugin.VERIFICATION_GROUP
        description = "Runs `${generateArgs.joinToString(" ")}` in sample/${sample.name}. " +
            "deleteSample${suffix}Template always follows it.$sdkNote"
        runSampleWrapper(sample, sampleDir, generateArgs)
        mustRunAfter(buildTask)
        finalizedBy(deleteTask)
        doFirst {
            // A marker left by a run that was killed outright says nothing about this run.
            marker.delete()
            val leftovers = generatedFiles.filter { it.exists() }
            if (leftovers.isNotEmpty()) {
                throw GradleException(
                    "The template run would write over files that are already there, and the " +
                        "clean-up after it would then delete them:\n" +
                        leftovers.joinToString("\n") { "  ${it.path}" } +
                        "\nIf they are left over from an earlier interrupted run, delete them " +
                        "by hand and run again.",
                )
            }
            marker.parentFile.mkdirs()
            marker.writeText(generatedFiles.joinToString("\n", postfix = "\n") { it.path })
        }
    }

    val checkTemplateTask = tasks.register<Exec>("checkSample${suffix}Template") {
        group = LifecycleBasePlugin.VERIFICATION_GROUP
        description = "Runs `${template.verifyTasks.joinToString(" ")}` in sample/${sample.name} " +
            "while the files generateSample${suffix}Template wrote are still there.$sdkNote"
        runSampleWrapper(sample, sampleDir, template.verifyTasks)
        dependsOn(generateTask)
        doFirst {
            val missing = generatedFiles.filterNot { it.isFile }
            if (missing.isNotEmpty()) {
                throw GradleException(
                    "The template run did not write these files, so the list in the root " +
                        "build.gradle.kts no longer matches the template of sample/$sampleName:\n" +
                        missing.joinToString("\n") { "  ${it.path}" } +
                        "\nFix the list, or the clean-up will leave generated files behind.",
                )
            }
        }
    }
    deleteTask.configure { mustRunAfter(checkTemplateTask) }
    registeredSamples += listOf(generateTask, checkTemplateTask, deleteTask)

    val task = tasks.register("checkSample$suffix") {
        group = LifecycleBasePlugin.VERIFICATION_GROUP
        description = "Runs checkSample${suffix}Build, then generates the files of a template " +
            "in sample/${sample.name}, checks the sample with them in place and deletes them."
        dependsOn(buildTask, checkTemplateTask)
    }
    checkSamples.configure { dependsOn(task) }
}
