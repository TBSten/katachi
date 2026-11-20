@file:OptIn(InternalKatachiApi::class)

package me.tbsten.katachi.test.konture

import io.kotest.assertions.withClue
import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactly
import me.tbsten.katachi.InternalKatachiApi
import me.tbsten.katachi.check.UncheckedFileConstraint
import me.tbsten.katachi.check.UnsatisfiedFileConstraint
import me.tbsten.katachi.konture.KontureConstraintScope

/**
 * KATACHI-204: `classes { should() notDependOnPackages "java.io.." }` stays green on a class that
 * imports `java.io.File`.
 *
 * This is Konture's own behaviour, not a loss in katachi's graph. Konture 1.0.1's
 * `ClassesShouldDependencyAssertions.notDependOnClassesInAnyPackage` (which `notDependOnPackages`
 * on classes delegates to) drops every dependency whose package is `java`, `javax` or `kotlin`
 * — or starts with one of them — before the pattern is ever compared
 * (`standardExclusions`). The file and function rules of the same name have no such list, so
 * the same import is red through them. These specs pin the three so that a Konture upgrade
 * that changes any of them is noticed, and the last one pins where the function rule and
 * `notReferenceClass` stop: a JDK class named under two wildcard imports.
 */
class KontureJdkPackageSpec : FreeSpec({
    "classes の notDependOnPackages は JDK のパッケージを数えない (Konture の仕様)" {
        fixtureProject("src/UsesFile.kt" to USES_JAVA_IO_FILE_KT) { root ->
            val violations = kontureRunIn(root, declarations = listOf("UsesFile.kt")) {
                classes { should() notDependOnPackages "java.io.." }
            }

            violations.filterIsInstance<UncheckedFileConstraint>().shouldBeEmpty()
            violations.filterIsInstance<UnsatisfiedFileConstraint>().shouldBeEmpty()
        }
    }

    "同じ import でも JDK 以外のパッケージなら classes の notDependOnPackages は赤" {
        // The control: the same rule shape, the same kind of import, a package outside the
        // exclusion list. This is what makes the green above a choice of Konture's and not a
        // parse that saw no import at all.
        fixtureProject("src/HomeViewModel.kt" to COMPOSABLE_VIEW_MODEL_KT) { root ->
            val violations = kontureRunIn(root, declarations = listOf("HomeViewModel.kt")) {
                classes { should() notDependOnPackages "androidx.compose.." }
            }

            violations.filterIsInstance<UnsatisfiedFileConstraint>().map { "${it.path}:${it.declaration}" } shouldContainExactly
                listOf("src/HomeViewModel.kt:HomeViewModel")
        }
    }

    "files の notDependOnPackages は JDK のパッケージも数える" {
        fixtureProject("src/UsesFile.kt" to USES_JAVA_IO_FILE_KT) { root ->
            val violations = kontureRunIn(root, declarations = listOf("UsesFile.kt")) {
                files { should() notDependOnPackages listOf("java.io..") }
            }

            violations.filterIsInstance<UncheckedFileConstraint>().shouldBeEmpty()
            violations.filterIsInstance<UnsatisfiedFileConstraint>().map { it.path } shouldContainExactly
                listOf("src/UsesFile.kt")
        }
    }

    "functions の notDependOnPackages は JDK のパッケージも数える" {
        fixtureProject("src/UsesFile.kt" to USES_JAVA_IO_FILE_KT) { root ->
            val violations = kontureRunIn(root, declarations = listOf("UsesFile.kt")) {
                functions { should() notDependOnPackages listOf("java.io..") }
            }

            violations.filterIsInstance<UncheckedFileConstraint>().shouldBeEmpty()
            violations.filterIsInstance<UnsatisfiedFileConstraint>().map { "${it.path}:${it.declaration}" } shouldContainExactly
                listOf("src/UsesFile.kt:UsesFile.open")
        }
    }

    "classes でも notReferenceClass なら JDK のクラスを拾う" {
        // The class-level way around the exclusion: name the class, not the package.
        fixtureProject("src/UsesFile.kt" to USES_JAVA_IO_FILE_KT) { root ->
            val violations = kontureRunIn(root, declarations = listOf("UsesFile.kt")) {
                classes { should().notReferenceClass("java.io.File") }
            }

            violations.filterIsInstance<UncheckedFileConstraint>().shouldBeEmpty()
            violations.filterIsInstance<UnsatisfiedFileConstraint>().map { "${it.path}:${it.declaration}" }.distinct() shouldContainExactly
                listOf("src/UsesFile.kt:UsesFile")
        }
    }

    "2 つのワイルドカード import の下の JDK のクラスは、functions も notReferenceClass も拾えず、files だけが import で拾う" {
        // No file the check walks declares `File`, so the symbol table cannot say which of the two
        // packages holds it and the name stays unresolved. Only the import says where it came from.
        val rules: List<Pair<String, KontureConstraintScope.() -> Unit>> = listOf(
            "files" to { files { should() notDependOnPackages listOf("java.io..") } },
            "functions" to { functions { should() notDependOnPackages listOf("java.io..") } },
            "notReferenceClass" to { classes { should().notReferenceClass("java.io.File") } },
        )
        fixtureProject("src/UsesFile.kt" to USES_JAVA_IO_WILDCARDS_KT) { root ->
            val red = rules.map { (kind, rule) ->
                val violations = kontureRunIn(root, declarations = listOf("UsesFile.kt"), block = rule)
                withClue(kind) { violations.filterIsInstance<UncheckedFileConstraint>().shouldBeEmpty() }
                kind to violations.filterIsInstance<UnsatisfiedFileConstraint>().map { it.path }.distinct()
            }

            red shouldContainExactly listOf(
                "files" to listOf("src/UsesFile.kt"),
                "functions" to emptyList<String>(),
                "notReferenceClass" to emptyList<String>(),
            )
        }
    }
})

/** [USES_JAVA_IO_FILE_KT] with `java.io` and `java.util` imported by wildcard instead of `File` by name. */
private val USES_JAVA_IO_WILDCARDS_KT: String = """
    package com.example

    import java.io.*
    import java.util.*

    class UsesFile {
        fun open(path: String): File = File(path)
    }
""".trimIndent() + "\n"

/** A class that imports `java.io.File` and names it in a signature: the shape KATACHI-204 reports. Class on line 5. */
internal val USES_JAVA_IO_FILE_KT: String = """
    package com.example

    import java.io.File

    class UsesFile {
        fun open(path: String): File = File(path)
    }
""".trimIndent() + "\n"
