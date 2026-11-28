@file:OptIn(ExperimentalKatachiApi::class)

package com.example.kmp.gradle

import com.example.kmp.processors.owner
import me.tbsten.katachi.ExperimentalKatachiApi
import me.tbsten.katachi.dsl.DeclarationContainerScope
import me.tbsten.katachi.dsl.FileConstraintFailure
import me.tbsten.katachi.dsl.gradle.gradle

private const val NAMESPACE_ROOT = "com.example.kmp"

private val NAMESPACE = Regex("""namespace\s*=\s*"([^"]+)"""")

private const val LIBRARY_BLOCK = "androidLibrary {"

fun DeclarationContainerScope.gradleGroup() = gradle {
    description = """
        Besides katachi's Gradle roles, this project adds one rule to the build scripts: the
        `namespace` of a library module is `com.example.kmp` followed by its module path. It is
        written inside `buildScript { }` instead of a Role of this project's own, because
        `build.gradle.kts` is a file katachi's `gradle()` already places, and a second Role
        claiming it would make the file ambiguous. Only build scripts that contain an
        `androidLibrary {` block are checked, and the root build script has no module path, so it
        is skipped by path before its text is read.
    """.trimIndent()
    owner = "platform"

    buildScript {
        owner = "platform"

        fileConstraint("namespace of a library module follows the module path") { subject ->
            buildList {
                for (file in subject.files) {
                    // The root build script has no module path to follow: never a subject here.
                    if ('/' !in file) continue
                    val text = subject.textOf(file)
                    if (LIBRARY_BLOCK !in text) continue
                    val namespace = NAMESPACE.find(text)?.groupValues?.get(1) ?: continue
                    val expected = "$NAMESPACE_ROOT." + file.substringBeforeLast('/').replace('/', '.')
                    if (namespace == expected) continue
                    add(
                        FileConstraintFailure(
                            file = file,
                            line = subject.lineOf(file, NAMESPACE),
                            reasons = listOf("Namespace" to namespace),
                            allowed = "Allowed here" to listOf(expected),
                            howToFix = listOf("Set namespace = \"$expected\" in $file"),
                        ),
                    )
                }
            }
        }
    }
}
