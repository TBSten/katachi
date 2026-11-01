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

private const val NAMESPACE_ROOT = "com.example.sample"

private val NAMESPACE = Regex("""namespace\s*=\s*"([^"]+)"""")

private const val LIBRARY_PLUGIN = "androidLibrary"

fun DeclarationContainerScope.gradleGroup() = gradle {
    description = """
        Besides katachi's Gradle roles, this project adds one rule to the build scripts: the
        `namespace` of a library module is `com.example.sample` followed by its module path. It
        is written inside `buildScript { }` instead of a Role of this project's own, because
        `build.gradle.kts` is a file katachi's `gradle()` already places, and a second Role
        claiming it would make the file ambiguous. Only library modules take part: the app names
        the application plugin instead, and the root build script has no module path, so it is
        skipped by path before its text is read.
    """.trimIndent()
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
