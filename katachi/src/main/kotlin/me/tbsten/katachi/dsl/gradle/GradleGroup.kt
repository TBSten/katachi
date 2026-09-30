package me.tbsten.katachi.dsl.gradle

import me.tbsten.katachi.dsl.*
import me.tbsten.katachi.dsl.internal.markSynthetic

/**
 * Declares the files every Gradle build has, as a `"Gradle"` group with one role per kind of
 * file, kept out of the generated documentation.
 *
 * | role | files |
 * |---|---|
 * | `Gradle.GradleWrapper.LauncherScript` | `gradlew`, `gradlew.bat` |
 * | `Gradle.GradleWrapper.WrapperJar` | `gradle/wrapper/gradle-wrapper.jar` |
 * | `Gradle.GradleWrapper.WrapperProperties` | `gradle/wrapper/gradle-wrapper.properties` |
 * | `Gradle.SettingsScript` | `settings.gradle.kts` or `settings.gradle` |
 * | `Gradle.BuildScript` | `build.gradle.kts` or `build.gradle` of every module, the root project included |
 * | `Gradle.GradleProperties` | `gradle.properties` |
 * | `Gradle.VersionCatalog` | `*.versions.toml` directly inside `gradle` |
 * | `Gradle.DaemonJvmProperties` | `gradle/gradle-daemon-jvm.properties`, the daemon JVM toolchain of Gradle 8.8 and later |
 *
 * Only the four wrapper files are required, because `gradle wrapper` writes them as one set
 * and a `*.jar` line in `.gitignore` silently leaving the jar out is exactly what a missing
 * file report is for. Pass `requireWrapper = false` for a build without a wrapper, or with
 * only part of one. Everything else is allowed without being required: a catalog of any name
 * is a catalog, and whether a build writes its scripts in Kotlin or Groovy is not the
 * definition's business.
 *
 * Build scripts are declared for the modules katachi finds, the same modules a `":**"` module
 * key expands to, so the list of modules is never written twice. What else lives inside a
 * module is left to the project's own roles.
 *
 * Every group and role here sets `documented = false` for itself. The generated
 * documentation already drops a whole group that opted out, but the metadata is not
 * inherited, and a processor reading `role[Documented]` should get the same answer.
 *
 * `buildSrc` and an included `build-logic` build are not declared: what they hold is code,
 * and how it is laid out is the project's decision. Declare them in [block].
 *
 * ## Example 1: Declare the Gradle files, plus one this build has on top
 * ```kt
 * import me.tbsten.katachi.dsl.architecture
 * import me.tbsten.katachi.dsl.gradle.gradle
 *
 * val arch = architecture {
 *     gradle {
 *         "DependencyVerification" {
 *             documented = false
 *             layout { "gradle/verification-metadata.xml".file() }
 *         }
 *     }
 * }
 * ```
 *
 * @param requireWrapper whether a missing wrapper file is reported. `false` still allows the
 *   wrapper files; it only stops asking for them.
 * @param block evaluated inside the `"Gradle"` group, after katachi's own roles, to add roles
 *   of the project's own or to change the group's `title` and `summary`.
 * @see me.tbsten.katachi.dsl.gradle.module
 */
public fun DeclarationContainerScope.gradle(
    requireWrapper: Boolean = true,
    block: GroupScope.() -> Unit = {},
): Unit = "Gradle".group {
    title = "Gradle"
    summary = "The files that make this project a Gradle build"
    documented = false

    "GradleWrapper".group {
        title = "Gradle wrapper"
        summary = "What runs the build with the Gradle version the project pins"
        documented = false

        gradleRole("LauncherScript", "The scripts that start the wrapper, for Unix and Windows") {
            "gradlew".file().optionalUnless(requireWrapper)
            "gradlew.bat".file().optionalUnless(requireWrapper)
        }
        gradleRole("WrapperJar", "The wrapper itself, which downloads and runs Gradle") {
            "gradle/wrapper/gradle-wrapper.jar".file().optionalUnless(requireWrapper)
        }
        gradleRole("WrapperProperties", "Which Gradle distribution the wrapper runs") {
            "gradle/wrapper/gradle-wrapper.properties".file().optionalUnless(requireWrapper)
        }
    }

    gradleRole("SettingsScript", "Which modules make up the build") {
        "settings.gradle.kts".file().optional()
        "settings.gradle".file().optional()
    }
    gradleRole("BuildScript", "How one module is built, one per module") {
        ":**".module { "build.gradle".file().markSynthetic() }.optional()
    }
    gradleRole("GradleProperties", "Properties of the build and of the Gradle daemon") {
        "gradle.properties".file().optional()
    }
    gradleRole("VersionCatalog", "The versions of the build's dependencies and plugins") {
        "gradle" / "*.versions.toml".file().optional()
    }
    gradleRole("DaemonJvmProperties", "Which JVM the Gradle daemon runs on (Gradle 8.8 and later)") {
        "gradle/gradle-daemon-jvm.properties".file().optional()
    }

    block()
}

/** One role of [gradle]: out of the documentation, and with nothing but a layout. */
private fun GroupScope.gradleRole(name: String, summary: String, layout: LayoutScope.() -> Unit) {
    name {
        this.summary = summary
        documented = false
        layout(layout)
    }
}

private fun LayoutFile.optionalUnless(required: Boolean): LayoutFile = if (required) this else optional()
