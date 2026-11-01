@file:OptIn(ExperimentalKatachiApi::class)

package com.example.gradle

import java.io.File
import me.tbsten.katachi.ExperimentalKatachiApi
import me.tbsten.katachi.dsl.DeclarationContainerScope
import me.tbsten.katachi.dsl.FileConstraintAllowedList
import me.tbsten.katachi.dsl.FileConstraintFailure
import me.tbsten.katachi.dsl.FileConstraintFix
import me.tbsten.katachi.dsl.FileConstraintReason
import me.tbsten.katachi.dsl.gradle.gradle

private const val WARNINGS_AS_ERRORS = "allWarningsAsErrors.set(true)"

fun DeclarationContainerScope.gradleGroup() = gradle {
    description = """
        Besides katachi's Gradle roles, this project adds one rule to the build scripts: every
        `build.gradle.kts` turns compiler warnings into errors with `allWarningsAsErrors.set(true)`.
        It is written inside `buildScript { }` instead of a Role of this project's own, because
        `build.gradle.kts` is a file katachi's `gradle()` already places, and a second Role
        claiming it would make the file ambiguous. The rule reads the file as text because
        Konsist does not read `.kts`.
    """.trimIndent()
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
