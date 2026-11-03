package me.tbsten.katachi.dsl.gradle

import me.tbsten.katachi.dsl.*
import me.tbsten.katachi.dsl.gradle.internal.expandModules
import me.tbsten.katachi.dsl.internal.DeclaredByGradle
import me.tbsten.katachi.dsl.internal.GradleRoleKind
import me.tbsten.katachi.dsl.internal.metadataBuilder
import me.tbsten.katachi.dsl.kotlin.ktsFile

/**
 * Declares the files every Gradle build has, as a `"Gradle"` group with one role per kind of
 * file, kept out of the generated documentation.
 *
 * | role | hook in [block] | files |
 * |---|---|---|
 * | `Gradle.GradleWrapper.LauncherScript` | `launcherScript { }` | `gradlew`, `gradlew.bat` |
 * | `Gradle.GradleWrapper.WrapperJar` | `wrapperJar { }` | `gradle/wrapper/gradle-wrapper.jar` |
 * | `Gradle.GradleWrapper.WrapperProperties` | `wrapperProperties { }` | `gradle/wrapper/gradle-wrapper.properties` |
 * | `Gradle.SettingsScript` | `settingsScript { }` | `settings.gradle.kts` or `settings.gradle` |
 * | `Gradle.BuildScript` | `buildScript { }` | `build.gradle.kts` or `build.gradle` of every module, the root project included |
 * | `Gradle.GradleProperties` | `gradleProperties { }` | `gradle.properties` |
 * | `Gradle.VersionCatalog` | `versionCatalog { }` | `*.versions.toml` directly inside `gradle` |
 * | `Gradle.DaemonJvmProperties` | `daemonJvmProperties { }` | `gradle/gradle-daemon-jvm.properties`, the daemon JVM toolchain of Gradle 8.8 and later |
 *
 * Only the four wrapper files are required, because `gradle wrapper` writes them as one set
 * and a `*.jar` line in `.gitignore` silently leaving the jar out is exactly what a missing
 * file report is for. Pass `requireWrapper = false` for a build without a wrapper, or with
 * only part of one. Everything else is allowed without being required: a catalog of any name
 * is a catalog, and whether a build writes its scripts in Kotlin or Groovy is not the
 * definition's business.
 *
 * Build scripts are declared for every module katachi finds in the project, so the list of
 * modules is never written twice. What else lives inside a module is left to the project's own
 * roles.
 *
 * A hook of [GradleScope] opens one of these roles for a constraint or a metadata value of the
 * project's own — `buildScript { fileConstraint("...") { subject -> ... } }` — without taking
 * over where its files live. A `"BuildScript".role { }` written in [block] instead is a
 * duplicate of katachi's role and is refused with [KatachiDuplicateDeclarationException];
 * the constraint belongs in the hook.
 *
 * Every group and role here sets `documented = false`, so a processor reading `role[Documented]`
 * gets `false` for each of them.
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
 *         "DependencyVerification".role {
 *             documented = false
 *             layout { "gradle/verification-metadata.xml".file() }
 *         }
 *     }
 * }
 * ```
 *
 * ## Example 2: Constrain the build scripts katachi declares
 * ```kt
 * import me.tbsten.katachi.dsl.FileConstraintFailure
 * import me.tbsten.katachi.dsl.architecture
 * import me.tbsten.katachi.dsl.gradle.gradle
 *
 * val arch = architecture {
 *     gradle {
 *         buildScript {
 *             fileConstraint("fails the build on every compiler warning") { subject ->
 *                 subject.files
 *                     .filterNot { "allWarningsAsErrors" in java.io.File(subject.projectRoot, it).readText() }
 *                     .map { FileConstraintFailure(it) }
 *             }
 *         }
 *     }
 * }
 * ```
 *
 * @param requireWrapper whether a missing wrapper file is reported. `false` still allows the
 *   wrapper files; it only stops asking for them.
 * @param block written inside the `"Gradle"` group: hooks such as `buildScript { }` for katachi's
 *   roles, and `"X".role { }` / `"X".group { }` for the project's own, which come after katachi's
 *   roles.
 * @throws KatachiDuplicateDeclarationException when [block] declares a role named like one of katachi's.
 * @see me.tbsten.katachi.dsl.GradleScope
 */
public fun DeclarationContainerScope.gradle(
    requireWrapper: Boolean = true,
    block: GradleScope.() -> Unit = {},
): Unit = "Gradle".group {
    title = "Gradle"
    summary = "The files that make this project a Gradle build"
    documented = false

    val scope = GradleScopeImpl(this)
    scope.block()

    "GradleWrapper".group {
        title = "Gradle wrapper"
        summary = "What runs the build with the Gradle version the project pins"
        documented = false

        gradleRole(GradleRoleKind.LauncherScript, "The scripts that start the wrapper, for Unix and Windows", scope) {
            "gradlew".file().optionalUnless(requireWrapper)
            "gradlew.bat".file().optionalUnless(requireWrapper)
        }
        gradleRole(GradleRoleKind.WrapperJar, "The wrapper itself, which downloads and runs Gradle", scope) {
            "gradle/wrapper/gradle-wrapper.jar".file().optionalUnless(requireWrapper)
        }
        gradleRole(GradleRoleKind.WrapperProperties, "Which Gradle distribution the wrapper runs", scope) {
            "gradle/wrapper/gradle-wrapper.properties".file().optionalUnless(requireWrapper)
        }
    }

    gradleRole(GradleRoleKind.SettingsScript, "Which modules make up the build", scope) {
        "settings.gradle.kts".file().optional()
        "settings.gradle".file().optional()
    }
    gradleRole(GradleRoleKind.BuildScript, "How one module is built, one per module", scope) {
        expandModules(":**") {
            // Declared by this role on its own account rather than left to the module sugar:
            // the sugar's line is synthetic, which keeps it out of what a constraint covers
            // and out of the ambiguity check, and a build script is exactly what this role is about.
            "build.gradle".ktsFile()
            "build.gradle".file()
        }.optional()
    }
    gradleRole(GradleRoleKind.GradleProperties, "Properties of the build and of the Gradle daemon", scope) {
        "gradle.properties".file().optional()
    }
    gradleRole(GradleRoleKind.VersionCatalog, "The versions of the build's dependencies and plugins", scope) {
        "gradle" / "*.versions.toml".file().optional()
    }
    gradleRole(GradleRoleKind.DaemonJvmProperties, "Which JVM the Gradle daemon runs on (Gradle 8.8 and later)", scope) {
        "gradle/gradle-daemon-jvm.properties".file().optional()
    }

    scope.declareDeferred()
}

private fun GroupScope.gradleRole(
    kind: GradleRoleKind,
    summary: String,
    scope: GradleScopeImpl,
    layout: LayoutScope.() -> Unit,
) {
    kind.roleName.role {
        this.summary = summary
        documented = false
        // Before the hook: a reader can tell katachi's Gradle roles apart whatever the hook wrote.
        metadataBuilder()[DeclaredByGradle] = true
        layout(layout)
        val roleScope = GradleRoleScopeImpl(this)
        for (hook in scope.hooksOf(kind)) roleScope.hook()
    }
}

private fun LayoutFile.optionalUnless(required: Boolean): LayoutFile = if (required) this else optional()
