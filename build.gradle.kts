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

data class SampleBuild(
    val name: String,
    val defaultTasks: List<String>,
    val needsAndroidSdk: Boolean,
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
    ),
    SampleBuild(
        "android",
        listOf("check", "runKatachiProcessor", "--processor=layout,docs", "--arg", "mode=check"),
        needsAndroidSdk = true,
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

val registeredSamples = mutableListOf<TaskProvider<Exec>>()

sampleBuilds.forEach { sample ->
    val suffix = sample.name.split("-").joinToString("") { part ->
        part.replaceFirstChar { it.uppercaseChar() }
    }
    val sampleTasks = sampleTasksOf(sample)
    val sampleDir = layout.projectDirectory.dir("sample/${sample.name}").asFile
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

        if (sample.needsAndroidSdk &&
            androidSdkFromEnvironment == null &&
            !File(sampleDir, "local.properties").exists()
        ) {
            wellKnownAndroidSdkDirs.firstOrNull { it.isDirectory }
                ?.let { environment("ANDROID_HOME", it.absolutePath) }
        }

        mustRunAfter(predecessors)

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
    registeredSamples += task
    checkSamples.configure { dependsOn(task) }
}
