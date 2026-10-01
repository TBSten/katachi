@file:OptIn(InternalKatachiApi::class, ExperimentalKatachiApi::class)

package me.tbsten.katachi.test.konsist

import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe
import me.tbsten.katachi.ExperimentalKatachiApi
import me.tbsten.katachi.InternalKatachiApi
import me.tbsten.katachi.check.FileConstraintCheck
import me.tbsten.katachi.check.UncheckedFileConstraint
import me.tbsten.katachi.check.UnsatisfiedFileConstraint
import me.tbsten.katachi.check.Violation
import me.tbsten.katachi.check.internal.validate
import me.tbsten.katachi.dsl.architecture
import me.tbsten.katachi.dsl.files.internal.RealFileSystem
import me.tbsten.katachi.dsl.gradle.div
import me.tbsten.katachi.dsl.gradle.kotlin
import me.tbsten.katachi.dsl.gradle.mainSourceSet
import me.tbsten.katachi.dsl.gradle.module
import me.tbsten.katachi.dsl.kotlin.ktFile
import me.tbsten.katachi.dsl.wholeTree
import me.tbsten.katachi.konsist.konsist

/**
 * The `build.gradle.kts` that a `.module { }` block declares on its own (a synthetic entry) must
 * never reach a `konsist { }` constraint.
 *
 * It used to be covered by accident: a sample role that wrote `mustBePublic()` inside
 * `.module { }` failed as soon as Konsist saw a `.kts` file. Samples stop using `.module { }`,
 * so the guarantee is pinned here, where the synthetic entry is still produced.
 */
class KonsistSyntheticBuildScriptSpec : FreeSpec({
    "synthetic な build.gradle.kts は konsist の対象に入らない" - {
        "Kotlin のファイルと並んでいても、違反の対象は Kotlin のファイルだけ" {
            val violations = moduleKonsistRun(
                "build.gradle.kts" to "plugins { }\n",
                "src/main/kotlin/PublicThing.kt" to PUBLIC_THING_KT,
            )

            // Only PublicThing.kt is judged. A build script that leaked in would either be
            // reported itself or turn the run into an UncheckedFileConstraint.
            violations.filterIsInstance<UncheckedFileConstraint>().shouldBeEmpty()
            violations.filterIsInstance<UnsatisfiedFileConstraint>().single().path shouldBe
                "src/main/kotlin/PublicThing.kt"
        }

        "Kotlin のファイルが無ければ制約の対象が空になり、build script が「Kotlin のファイルが読めない」にならない" {
            val violations = moduleKonsistRun("build.gradle.kts" to "plugins { }\n")

            // A build script that leaked into the covered files would make Konsist the reader of
            // a .kts, which is KatachiKonsistNoKotlinFilesException (an UncheckedFileConstraint).
            violations.shouldBeEmpty()
        }
    }
})

/** One role with `":".module { }` and a `konsist { }` that wants every class to be internal. */
private fun moduleKonsistRun(vararg sources: Pair<String, String>): List<Violation> =
    fixtureProject(*sources) { root ->
        val projectArchitecture = architecture {
            files = wholeTree()
            "domain".group {
                "UseCase" {
                    layout {
                        "gradlew".file()
                        ":".module {
                            mainSourceSet / kotlin / "*".ktFile()
                            "規約".konsist { classes().must { it.hasInternalModifier } }
                        }
                    }
                }
            }
        }
        projectArchitecture.validate(RealFileSystem(root), FileConstraintCheck())
    }
