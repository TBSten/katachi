@file:OptIn(ExperimentalKatachiApi::class)

package com.example.groups

import java.io.File
import me.tbsten.katachi.ExperimentalKatachiApi
import me.tbsten.katachi.dsl.DeclarationContainerScope
import me.tbsten.katachi.dsl.FileConstraintAllowedList
import me.tbsten.katachi.dsl.FileConstraintFailure
import me.tbsten.katachi.dsl.FileConstraintFix
import me.tbsten.katachi.dsl.FileConstraintReason
import me.tbsten.katachi.dsl.gradle.gradle

/** The line every build script of this sample turns warnings into errors with. */
private const val WARNINGS_AS_ERRORS = "allWarningsAsErrors.set(true)"

/**
 * The `"Gradle"` group katachi ships (see [gradle]): the wrapper, `settings.gradle.kts`, every
 * module's `build.gradle.kts`, the root `gradle.properties` and the version catalog -- plus
 * one rule of this sample's own on the build scripts.
 *
 * `gradle()` needs no module list: its build-script role covers the same
 * modules `settings.gradle.kts` already names. Its own `"Gradle"` group already sets
 * `documented = false` and a title, so it is called as is rather than wrapped in a group of
 * this sample's own.
 *
 * The rule: every `build.gradle.kts` turns compiler warnings into errors, so none accumulate
 * unnoticed. It is written in `buildScript { }`, the hook `gradle { }` opens for the
 * `Gradle.BuildScript` role, rather than in a role of this sample's own -- the build script
 * is katachi's file to place, and a second role claiming it would be an ambiguity. A `.kts`
 * is not something Konsist reads, so the check is a plain `fileConstraint` over the text.
 *
 * Because the group and every role inside it are declared inside katachi's own `GradleGroup.kt`,
 * not here, `Group.declaredAt` / `Role.declaredAt` for all of them resolve to the `gradle {`
 * line below -- katachi walks the stack past its own frames to the first one outside itself,
 * which is this call, not the `"Gradle".group { }` katachi wrote. The constraint is different:
 * it is written here, so its own declaration site is the `fileConstraint(` line.
 * `ProjectArchitectureSpec` carves that whole subtree out of the "one declaration, one file"
 * checks it runs on the rest of the definition, because there genuinely is only one
 * declaration site here: this file.
 */
fun DeclarationContainerScope.gradleGroup() = gradle {
    buildScript {
        fileConstraint("fails the build on every compiler warning") { subject ->
            subject.files.mapNotNull { file ->
                val text = File(subject.projectRoot, file).readText()
                if (WARNINGS_AS_ERRORS in text) return@mapNotNull null
                FileConstraintFailure(
                    file = file,
                    reasons = listOf(FileConstraintReason("Missing", WARNINGS_AS_ERRORS)),
                    allowed = FileConstraintAllowedList("Expected inside kotlin { compilerOptions { } }", listOf(WARNINGS_AS_ERRORS)),
                    howToFix = listOf(FileConstraintFix("Add $WARNINGS_AS_ERRORS to the compilerOptions { } block of $file")),
                )
            }
        }
    }
}
