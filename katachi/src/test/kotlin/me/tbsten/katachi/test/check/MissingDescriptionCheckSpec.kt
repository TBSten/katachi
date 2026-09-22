package me.tbsten.katachi.test.check

import io.kotest.assertions.throwables.shouldNotThrowAny
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import me.tbsten.katachi.check.KatachiArchitectureAssertionError
import me.tbsten.katachi.check.assert
import me.tbsten.katachi.check.report
import me.tbsten.katachi.check.validate
import me.tbsten.katachi.dsl.architecture
import me.tbsten.katachi.dsl.flattenLayout
import me.tbsten.katachi.dsl.gradle.module
import me.tbsten.katachi.dsl.kotlin.ktFile
import me.tbsten.katachi.scan.MissingDescription
import me.tbsten.katachi.scan.missingDescriptionsOf

/**
 * [MissingDescription] wired through the real check — `ProjectModel.declaredEntries`,
 * `LayoutCheck`, `validate()` and `assert()` against a fake file system — and the wording of its
 * report block. `MissingDescriptionSpec` (in `test.scan`) covers the detector's own rule.
 */

/** A role living in [modules], each holding one wildcard file and no `description`. */
private fun roleAcross(vararg modules: String) = architecture {
    "group".group {
        "Role" {
            layout {
                for (modulePath in modules) modulePath.module { "*UseCase".ktFile() }
            }
        }
    }
}

/** The role's warnings, built from the declarations alone — no walk, so no tree to build. */
private fun warningsAcross(vararg modules: String): List<MissingDescription> =
    missingDescriptionsOf(roleAcross(*modules).flattenLayout()).filterIsInstance<MissingDescription>()

class MissingDescriptionCheckSpec : FreeSpec({
    "配置場所が2つあって description が無いと、警告は出るが assert() は落ちない" {
        val definition = architectureOf {
            "group".group {
                "Role" {
                    layout {
                        ":core:domain".module { "*UseCase".ktFile() }
                        ":feature:home".module { "*UseCase".ktFile() }
                    }
                }
            }
        }
        val tree = repositoryOf {
            "core" { "domain" { "build.gradle.kts"(); "GetUserUseCase.kt"() } }
            "feature" { "home" { "build.gradle.kts"(); "ShowHomeUseCase.kt"() } }
        }

        val printed = capturingStandardError {
            shouldNotThrowAny { definition.assert(tree) }
        }

        printed shouldContain "[MissingDescription] core/domain"
        printed shouldContain "[MissingDescription] feature/home"
        definition.validate(tree).filterIsInstance<MissingDescription>().map { it.path } shouldBe
            listOf("core/domain", "feature/home")
    }

    "description を書けば警告が消える" {
        val definition = architectureOf {
            "group".group {
                "Role" {
                    layout {
                        ":core:domain".module {
                            description = "複数 feature から使われるもの"
                            "*UseCase".ktFile()
                        }
                        ":feature:home".module {
                            description = "その feature 専用のもの"
                            "*UseCase".ktFile()
                        }
                    }
                }
            }
        }
        val tree = repositoryOf {
            "core" { "domain" { "build.gradle.kts"(); "GetUserUseCase.kt"() } }
            "feature" { "home" { "build.gradle.kts"(); "ShowHomeUseCase.kt"() } }
        }

        definition.validate(tree).shouldBeEmpty()
    }

    "\":feature:*\".module { } は、実在するモジュール数によらず1つの place として数えられる" {
        val definition = architectureOf {
            "group".group {
                "Role" {
                    layout {
                        ":core:ui".module { "*Screen".ktFile() }
                        ":feature:*".module { "*Screen".ktFile() }
                    }
                }
            }
        }
        val tree = repositoryOf {
            "build.gradle.kts"()
            "settings.gradle.kts"()
            "core" { "ui" { "build.gradle.kts"(); "BaseScreen.kt"() } }
            "feature" {
                "home" { "build.gradle.kts"(); "HomeScreen.kt"() }
                "settings" { "build.gradle.kts"(); "SettingsScreen.kt"() }
                "debugMenu" { "build.gradle.kts"(); "DebugMenuScreen.kt"() }
            }
        }

        definition.validate(tree).filterIsInstance<MissingDescription>().map { it.path } shouldBe
            listOf("core/ui", "feature/*")
    }

    "Error と Warning が両方あると、失敗メッセージの末尾に Warning セクションが来る" {
        val definition = architectureOf {
            "group".group {
                "Role" {
                    layout {
                        ":core:domain".module { "GetUser".ktFile() }
                        ":feature:home".module { "*UseCase".ktFile() }
                    }
                }
            }
        }
        // `core/domain/GetUser.kt` is declared without a wildcard and is not there: one error.
        val tree = repositoryOf {
            "core" { "domain" { "build.gradle.kts"() } }
            "feature" { "home" { "build.gradle.kts"(); "ShowHomeUseCase.kt"() } }
        }

        val failure = shouldThrow<KatachiArchitectureAssertionError> { definition.assert(tree) }

        failure.message!!.lineSequence().first() shouldBe
            "Katachi check failed: 1 violation (Missing: 1), 2 warnings"
        failure.message!!.substringAfter("[MissingFile]") shouldContain
            "Katachi check found 2 warnings. Warnings never fail the check."
    }

    "ブロックの文面" - {
        "配置場所が2つ — Other place は単数形" {
            val warnings = warningsAcross(":core:domain", ":feature:home")
            val site = warnings.first().declaredAt

            warnings.take(1).report() shouldBe
                """
                Katachi check found 1 warning. Warnings never fail the check.

                [MissingDescription] core/domain
                  Role group/Role may live in 2 places, and this one does not say when to use it.
                  Other place: feature/home
                  Declared at: $site
                  How to fix:
                    - Write `description = "..."` in this block, saying which files belong here rather than in the others
                    - Or merge this place into another if the two are really the same place
                """.trimIndent()
        }

        "配置場所が3つ — Other places は複数形で件数も増える" {
            val report = warningsAcross(":core:domain", ":feature:home", ":tool:cli").take(1).report()

            report shouldContain "Role group/Role may live in 3 places, and this one does not say when to use it."
            report shouldContain "Other places: feature/home, tool/cli"
        }

        "配置場所が5つ — Other places は3件で打ち切り、残りは件数だけ" {
            val report = warningsAcross(":a", ":b", ":c", ":d", ":e").take(1).report()

            report shouldContain "Role group/Role may live in 5 places, and this one does not say when to use it."
            report shouldContain "Other places: b, c, d, and 1 more"
        }
    }
})
