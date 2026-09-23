package me.tbsten.katachi.test.check

import io.kotest.assertions.throwables.shouldNotThrowAny
import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import me.tbsten.katachi.check.assert
import me.tbsten.katachi.check.report
import me.tbsten.katachi.check.validate
import me.tbsten.katachi.dsl.gradle.module
import me.tbsten.katachi.dsl.kotlin.ktFile
import me.tbsten.katachi.scan.AmbiguousLayout

/**
 * [AmbiguousLayout] wired through the real check: `ProjectModel.declaredEntries`,
 * `LayoutCheck`, `validate()` and `assert()` together, against a fake file system.
 * `AmbiguousLayoutSpec` (in `test.scan`) covers the detector's own rule in isolation.
 */
class AmbiguousLayoutCheckSpec : FreeSpec({
    "同じ glob を2つの役割が書くと Warning が1件になり assert() は落ちない" {
        val definition = architectureOf {
            "group".group {
                "RoleA" { layout { ".gitignore".file() } }
                "RoleB" { layout { ".gitignore".file() } }
            }
        }
        val tree = repositoryOf { ".gitignore"() }

        val printed = capturingStandardError {
            shouldNotThrowAny { definition.assert(tree) }
        }

        printed shouldContain "[AmbiguousLayout] .gitignore"
        definition.validate(tree).filterIsInstance<AmbiguousLayout>().map { it.path } shouldBe
            listOf(".gitignore")
    }

    "\":feature:*\".module { } を2役割が書くと、実在するモジュール数によらず警告は1件" {
        val definition = architectureOf {
            "group".group {
                "RoleA" { layout { ":feature:*".module { "Screen".ktFile() } } }
                "RoleB" { layout { ":feature:*".module { "Screen".ktFile() } } }
            }
        }
        val tree = repositoryOf {
            "build.gradle.kts"()
            "settings.gradle.kts"()
            "feature" {
                "home" { "build.gradle.kts"(); "Screen.kt"() }
                "settings" { "build.gradle.kts"(); "Screen.kt"() }
                "debugMenu" { "build.gradle.kts"(); "Screen.kt"() }
            }
        }

        definition.validate(tree).filterIsInstance<AmbiguousLayout>().map { it.path } shouldBe
            listOf("feature/*/Screen.kt")
    }

    "2つの役割が同じモジュールを .module { } で書いても警告は出ない" {
        val definition = architectureOf {
            "group".group {
                "RoleA" { layout { ":app".module { "Foo".ktFile() } } }
                "RoleB" { layout { ":app".module { "Bar".ktFile() } } }
            }
        }
        val tree = repositoryOf {
            "app" { "build.gradle.kts"(); "Foo.kt"(); "Bar.kt"() }
        }

        definition.validate(tree).filterIsInstance<AmbiguousLayout>().shouldBeEmpty()
    }

    "ブロックの文面" - {
        "2つの役割 — Declared by の各行と How to fix の単数形" {
            val definition = architectureOf {
                "group".group {
                    "RoleA" { layout { "gradle" / "libs.versions.toml".file() } }
                    "RoleB" { layout { "gradle" / "libs.versions.toml".file() } }
                }
            }
            val violations = definition.validate(repositoryOf { "gradle" { "libs.versions.toml"() } })
            val claims = violations.filterIsInstance<AmbiguousLayout>().single().claims
            val (siteA, siteB) = claims.map { it.declaredAt }

            violations.report() shouldBe
                """
                Katachi check found 1 warning. Warnings never fail the check.

                [AmbiguousLayout] gradle/libs.versions.toml
                  2 roles declare this path, so a file here belongs to all of them.

                  Declared by:
                    group/RoleA $siteA
                    group/RoleB $siteB

                  How to fix:
                    - Keep the path in the role its files belong to, and remove it from the other
                    - Narrow one of them if they are about different files
                """.trimIndent()
        }

        "3つ以上の役割 — 件数と How to fix が複数形になる" {
            val definition = architectureOf {
                "group".group {
                    "RoleA" { layout { "docs" / "README.md".file() } }
                    "RoleB" { layout { "docs" / "README.md".file() } }
                    "RoleC" { layout { "docs" / "README.md".file() } }
                }
            }

            val report = definition.validate(repositoryOf { "docs" { "README.md"() } }).report()

            report shouldContain "3 roles declare this path, so a file here belongs to all of them."
            report shouldContain "- Keep the path in the role its files belong to, and remove it from the others"
            report shouldContain "- Narrow the ones that are about different files"
        }
    }
})
