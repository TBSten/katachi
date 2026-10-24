@file:OptIn(InternalKatachiApi::class)
@file:Suppress("DEPRECATION")

package me.tbsten.katachi.test.konsist.declarations

import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import me.tbsten.katachi.InternalKatachiApi
import me.tbsten.katachi.check.FileConstraintCheck
import me.tbsten.katachi.check.UncheckedFileConstraint
import me.tbsten.katachi.check.internal.validate
import me.tbsten.katachi.dsl.architecture
import me.tbsten.katachi.dsl.files.internal.RealFileSystem
import me.tbsten.katachi.dsl.gradle.div
import me.tbsten.katachi.dsl.gradle.kotlin
import me.tbsten.katachi.dsl.gradle.mainSourceSet
import me.tbsten.katachi.dsl.gradle.module
import me.tbsten.katachi.dsl.kotlin.ktFile
import me.tbsten.katachi.dsl.wholeTree
import me.tbsten.katachi.konsist.KatachiKonsistNoKotlinFilesException
import me.tbsten.katachi.konsist.declarations.ktFileContent
import me.tbsten.katachi.test.konsist.fixtureProject

/**
 * A role whose covered files hold no `.kt` Konsist can read is refused (decided D6). A block that
 * writes no line for a file, or for any file, judges nothing and raises nothing: a conditional block
 * under a wildcard module must not fail the modules it has nothing to say about (decided 2026-10-24, decision 1 of design.md section 8).
 */
class KtFileContentNothingWrittenSpec : FreeSpec({
    "覆うファイルに .kt が無ければ KatachiKonsistNoKotlinFilesException" {
        val violations = ktFileContentRun(
            "docs/README.md" to "# docs\n",
            paths = { "docs" / "*.md".file() },
        ) {
            ktFileContent { allow(`class`, fileName) }
        }

        violations.filterIsInstance<UncheckedFileConstraint>().single().cause
            .shouldBeInstanceOf<KatachiKonsistNoKotlinFilesException>().role shouldBe "Screen"
    }

    "ファイルによっては何も書かなくても、どれかのファイルで書けば検査する" {
        val violations = ktFileContentRun(
            "src/Home.kt" to "package com.example\n\nclass Home\n",
            "src/Other.kt" to "package com.example\n\nobject Other\n",
            paths = { "src" / "*".ktFile() },
        ) {
            ktFileContent { if (fileName == "Home") allow(`class`, fileName) }
        }

        violations.shouldBeEmpty()
    }

    "どのファイルでも書かなくても例外にならず、違反も出さない" {
        val violations = ktFileContentRun(
            "src/Home.kt" to "package com.example\n\nclass Home\n",
            paths = { "src" / "*".ktFile() },
        ) {
            ktFileContent { if (fileName == "Nothing") allow(`class`, fileName) }
        }

        violations.shouldBeEmpty()
    }

    "ワイルドカードのモジュールで、該当ファイルが片方にしか無くても、ほかのモジュールは検査される" {
        // The module block runs once per module, so each module is its own subject: feature/other
        // writes no line for any of its files, and must not fail feature/home.
        val violations = fixtureProject(
            "feature/home/build.gradle.kts" to "",
            "feature/home/src/main/kotlin/HomeScreen.kt" to "package com.example\n\nclass HomeScreen\n\nobject Stray\n",
            "feature/other/build.gradle.kts" to "",
            "feature/other/src/main/kotlin/Other.kt" to "package com.example\n\nclass Other\n",
        ) { root ->
            architecture {
                files = wholeTree()
                "Screen".role {
                    layout {
                        "gradlew".file()
                        ":feature:*".module {
                            "build.gradle.kts".file()
                            mainSourceSet / kotlin / "*".ktFile()
                            ktFileContent { if (fileName.endsWith("Screen")) allow(`class`, fileName) }
                        }
                    }
                }
            }.validate(RealFileSystem(root), FileConstraintCheck())
        }

        violations.filterIsInstance<UncheckedFileConstraint>().shouldBeEmpty()
        violations.unsatisfiedLines() shouldBe listOf(
            "feature/home/src/main/kotlin/HomeScreen.kt: Stray (line 5) | Not allowed: object Stray",
        )
    }
})
