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
import me.tbsten.katachi.check.validate
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
 * writes no line for a file, or for any file, raises nothing but is judged like an empty block:
 * deny by default (decided 2026-10-25), so whatever such a file holds is a violation. This
 * replaces decision 1 of design.md section 8 (2026-10-24), which left such files unchecked.
 */
class KtFileContentNothingWrittenSpec : FreeSpec({
    "覆うファイルに .kt が無ければ KatachiKonsistNoKotlinFilesException" {
        val violations = ktFileContentRun(
            "docs/README.md" to "# docs\n",
            paths = { "docs" / "*.md".file() },
        ) {
            ktFileContent { required(`class`, fileName) }
        }

        violations.filterIsInstance<UncheckedFileConstraint>().single().cause
            .shouldBeInstanceOf<KatachiKonsistNoKotlinFilesException>().role shouldBe "Screen"
    }

    "行を 1 つも書かないファイルも、空のブロックと同じにすべてを違反にする" {
        val violations = ktFileContentRun(
            "src/Home.kt" to "package com.example\n\nclass Home\n",
            "src/Other.kt" to "package com.example\n\nobject Other\n",
            paths = { "src" / "*".ktFile() },
        ) {
            ktFileContent {
                `package`("**")
                if (fileName == "Home") required(`class`, fileName)
            }
        }

        violations.filterIsInstance<UncheckedFileConstraint>().shouldBeEmpty()
        violations.unsatisfiedLines() shouldBe listOf("src/Other.kt: Other (line 3) | Not allowed: object Other")
    }

    "どのファイルでも行を書かないブロックは、例外にならず、すべてを違反にする" {
        val violations = ktFileContentRun(
            "src/Home.kt" to "package com.example\n\nclass Home\n",
            paths = { "src" / "*".ktFile() },
        ) {
            ktFileContent { if (fileName == "Nothing") required(`class`, fileName) }
        }

        violations.filterIsInstance<UncheckedFileConstraint>().shouldBeEmpty()
        violations.unsatisfiedLines() shouldBe listOf(
            "src/Home.kt: package com.example (line 1) | Not allowed: package com.example | Note: $NOTHING_WRITTEN",
            "src/Home.kt: Home (line 3) | Not allowed: class Home",
        )
    }

    "ワイルドカードのモジュールで、行を書かないモジュールがあっても例外にならず、どのモジュールも検査される" {
        // The module block runs once per module, so each module is its own subject: feature/other
        // writes no declaration line for any of its files, and its declarations are denied.
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
                            ktFileContent {
                                `package`("**")
                                if (fileName.endsWith("Screen")) required(`class`, fileName)
                            }
                        }
                    }
                }
            }.validate(RealFileSystem(root), FileConstraintCheck())
        }

        violations.filterIsInstance<UncheckedFileConstraint>().shouldBeEmpty()
        violations.unsatisfiedLines() shouldBe listOf(
            "feature/home/src/main/kotlin/HomeScreen.kt: Stray (line 5) | Not allowed: object Stray",
            "feature/other/src/main/kotlin/Other.kt: Other (line 3) | Not allowed: class Other",
        )
    }
})
