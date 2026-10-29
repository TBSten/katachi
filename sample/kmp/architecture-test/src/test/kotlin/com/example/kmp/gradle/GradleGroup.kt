@file:OptIn(ExperimentalKatachiApi::class)

package com.example.kmp.gradle

import com.example.kmp.processors.owner
import java.io.File
import me.tbsten.katachi.ExperimentalKatachiApi
import me.tbsten.katachi.dsl.DeclarationContainerScope
import me.tbsten.katachi.dsl.FileConstraintAllowedList
import me.tbsten.katachi.dsl.FileConstraintFailure
import me.tbsten.katachi.dsl.FileConstraintFix
import me.tbsten.katachi.dsl.FileConstraintReason
import me.tbsten.katachi.dsl.gradle.gradle

/** The package every library module's `namespace` starts from: the app's own. */
private const val NAMESPACE_ROOT = "com.example.kmp"

private val NAMESPACE = Regex("""namespace\s*=\s*"([^"]+)"""")

/**
 * What marks a module as a library: the `androidLibrary { }` block of the Kotlin Multiplatform
 * library plugin. The root has none, but is skipped by path all the same.
 */
private const val LIBRARY_BLOCK = "androidLibrary {"

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
 * `owner = "platform"` is written twice on purpose, to show both places it can go. On the
 * group, because [com.example.kmp.processors.PlatformOwnedFilesProcessor] walks a tagged
 * group's roles, which reaches every role `gradle()` declares in one line -- the shape
 * [me.tbsten.katachi.dsl.MetadataScope]'s own KDoc shows first. And on one role, through
 * `buildScript { }`: the hook `gradle { }` opens for the `Gradle.BuildScript` role is a
 * [me.tbsten.katachi.dsl.MetadataScope] too, so a word a processor brings in lands on that one
 * role. The processor treats a role tagged itself and a role under a tagged group alike, so
 * the build scripts are listed once, not twice.
 *
 * The rule in the same hook: a library module's `namespace` is [NAMESPACE_ROOT] followed by
 * the module's path (`feature/home` -> `com.example.kmp.feature.home`), so that a module can
 * be found from its package and the other way round. Only modules whose build script holds the
 * [LIBRARY_BLOCK] take part: `app/android` configures `android { applicationId }` instead, and
 * the root build script has no module path, so it is skipped before the text is read. It is
 * written in the hook rather than in a role of this sample's own because the build script is
 * katachi's file to place, and a second role claiming it would be an ambiguity. A `.kts` is
 * not something Konsist reads, so the check is a plain `fileConstraint` over the text.
 *
 * Because the group and every role inside it are declared inside katachi's own
 * `GradleGroup.kt`, not here, `Group.declaredAt` / `Role.declaredAt` for all of them resolve to
 * the `gradle {` line below -- katachi walks the stack past its own frames to the first one
 * outside itself, which is this call. The constraint is different: it is written here, so its
 * own declaration site is the `fileConstraint(` line. `ProjectArchitectureSpec` carves this
 * subtree out of the "one declaration, one file" checks it runs on the rest of the definition,
 * because there genuinely is only one declaration site here: this file.
 */
fun DeclarationContainerScope.gradleGroup() = gradle {
    owner = "platform"

    buildScript {
        owner = "platform"

        fileConstraint("namespace of a library module follows the module path") { subject ->
            subject.files.mapNotNull { file ->
                // The root build script has no module path to follow: never a subject here.
                if ('/' !in file) return@mapNotNull null
                val text = File(subject.projectRoot, file).readText()
                if (LIBRARY_BLOCK !in text) return@mapNotNull null
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
