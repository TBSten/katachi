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
