@file:OptIn(ExperimentalKatachiApi::class)

package com.example.sample.gradle

import java.io.File
import me.tbsten.katachi.ExperimentalKatachiApi
import me.tbsten.katachi.dsl.DeclarationContainerScope
import me.tbsten.katachi.dsl.FileConstraintAllowedList
import me.tbsten.katachi.dsl.FileConstraintFailure
import me.tbsten.katachi.dsl.FileConstraintFix
import me.tbsten.katachi.dsl.FileConstraintReason
import me.tbsten.katachi.dsl.gradle.gradle

/** The package every library module's `namespace` starts from: the app's own. */
private const val NAMESPACE_ROOT = "com.example.sample"

private val NAMESPACE = Regex("""namespace\s*=\s*"([^"]+)"""")

/**
 * What marks a module as a library: the plugin it applies. The root names it too, with
 * `apply false`, and is skipped by path before the text is read.
 */
private const val LIBRARY_PLUGIN = "androidLibrary"

/**
 * The `"Gradle"` group katachi ships (see [gradle]): the wrapper, `settings.gradle.kts`, every
 * module's `build.gradle.kts`, the root `gradle.properties` and the version catalog -- plus
 * one rule of this sample's own on the build scripts.
 *
 * `gradle()` needs no module list: its build-script role covers every module that
 * `settings.gradle.kts` already names. Its own `"Gradle"` group already sets
 * `documented = false` and a title, so it is called as is rather than wrapped in a group of
 * this sample's own.
 *
 * The rule: a library module's `namespace` is [NAMESPACE_ROOT] followed by the module's path
 * (`feature/home` -> `com.example.sample.feature.home`), so that a module can be found from
 * its package and the other way round. Only modules whose build script names the library
 * plugin take part: the app module names the application plugin instead, and the root build
 * script (which names the library plugin with `apply false`) has no module path, so it is
 * skipped before the text is read. It is written in `buildScript { }`, the hook `gradle { }`
 * opens for the `Gradle.BuildScript` role, rather than in a role of this sample's own -- the
 * build script is katachi's file to place, and a second role claiming it would be an
 * ambiguity. A `.kts` is not something Konsist reads, so the check is a plain
 * `fileConstraint` over the text.
 *
 * Because the group and every role inside it are declared inside katachi's own
 * `GradleGroup.kt`, not here, `Group.declaredAt` / `Role.declaredAt` for all of them resolve to
 * the `gradle {` line below — katachi walks the stack past its own frames to the first one
 * outside itself, which is this call. The constraint is different: it is written here, so its
 * own declaration site is the `fileConstraint(` line. `ProjectArchitectureSpec` carves this
 * subtree out of the "one declaration, one file" checks it runs on the rest of the definition,
 * because there genuinely is only one declaration site here: this file.
 */
fun DeclarationContainerScope.gradleGroup() = gradle {
    buildScript {
        fileConstraint("namespace of a library module follows the module path") { subject ->
            subject.files.mapNotNull { file ->
                // The root build script has no module path to follow: never a subject here.
                if ('/' !in file) return@mapNotNull null
                val text = File(subject.projectRoot, file).readText()
                if (LIBRARY_PLUGIN !in text) return@mapNotNull null
                val namespace = NAMESPACE.find(text)?.groupValues?.get(1) ?: return@mapNotNull null
                val expected = "$NAMESPACE_ROOT." + file.substringBeforeLast('/').replace('/', '.')
                if (namespace == expected) return@mapNotNull null
                FileConstraintFailure(
                    file = file,
                    line = text.lineSequence().indexOfFirst { NAMESPACE.containsMatchIn(it) } + 1,
                    reasons = listOf(FileConstraintReason("Namespace", namespace)),
                    allowed = FileConstraintAllowedList("Allowed here", listOf(expected)),
                    howToFix = listOf(FileConstraintFix("Set namespace = \"$expected\" in $file")),
                )
            }
        }
    }
}
