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
        val warnings = definition.validate(tree).filterIsInstance<AmbiguousLayout>()
        warnings.map { it.path } shouldBe listOf(".gitignore")
        // The walk finds the same two roles on the real `.gitignore`, and that group is
        // dropped rather than printed as a second block about the same two lines.
        warnings.single().overlappingFiles.shouldBeEmpty()
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

        // `build.gradle.kts` really is allowed by both roles — that is what makes it a file
        // and not a violation — but neither author claimed it, so it is not an overlap
        // either, textually or on the file the walk actually reached.
        definition.validate(tree).filterIsInstance<AmbiguousLayout>().shouldBeEmpty()
    }

    "実ファイル単位" - {
        "違う pattern が同じ実ファイルを掴むと警告が出る" {
            val definition = architectureOf {
                "ui".group {
                    "Component" { layout { "home" { "*".ktFile() } } }
                    "ViewModel" { layout { "home" { "*ViewModel".ktFile() } } }
                }
            }
            val tree = repositoryOf { "home" { "HomeScreen.kt"(); "HomeViewModel.kt"() } }

            val warnings = definition.validate(tree).filterIsInstance<AmbiguousLayout>()

            warnings.map { it.path } shouldBe listOf("home/HomeViewModel.kt")
            warnings.single().claims.map { it.role.qualifiedName } shouldBe
                listOf("ui/Component", "ui/ViewModel")
        }

        "重なったファイルが何件あっても警告は1件で、overlappingFiles が全部を持つ" {
            val definition = architectureOf {
                "ui".group {
                    "Component" { layout { "*" { "*".ktFile() } } }
                    "ViewModel" { layout { "*" { "*ViewModel".ktFile() } } }
                }
            }
            val tree = repositoryOf {
                "home" { "HomeScreen.kt"(); "HomeViewModel.kt"() }
                "search" { "SearchViewModel.kt"() }
                "topic" { "TopicViewModel.kt"() }
            }

            val warnings = definition.validate(tree).filterIsInstance<AmbiguousLayout>()

            warnings.map { it.path } shouldBe listOf("home/HomeViewModel.kt")
            warnings.single().overlappingFiles shouldBe listOf(
                "home/HomeViewModel.kt",
                "search/SearchViewModel.kt",
                "topic/TopicViewModel.kt",
            )
        }

        "文字列一致で既に報告された役割の組は、別のファイルで重なっても2件目にならない" {
            val definition = architectureOf {
                "group".group {
                    "RoleA" { layout { "docs" / "README.md".file(); "src" { "*".ktFile() } } }
                    "RoleB" { layout { "docs" / "README.md".file(); "src" { "*Spec".ktFile() } } }
                }
            }
            val tree = repositoryOf {
                "docs" { "README.md"() }
                "src" { "Foo.kt"(); "FooSpec.kt"() }
            }

            val warnings = definition.validate(tree).filterIsInstance<AmbiguousLayout>()

            warnings.map { it.path } shouldBe listOf("docs/README.md")
        }

        "ignore() したディレクトリは歩かれないので、ファイル単位の重なりも出ない" {
            val definition = architectureOf {
                "group".group {
                    "RoleA" { layout { "secrets".ignore() } }
                    "RoleB" { layout { "secrets" { "*.txt".file() } } }
                }
            }
            val tree = repositoryOf { "secrets" { "token.txt"() } }

            definition.validate(tree).filterIsInstance<AmbiguousLayout>().shouldBeEmpty()
        }

        "anyFile() も claim なので file() と重なる" {
            val definition = architectureOf {
                "build".group {
                    "Open" { layout { "generated" { anyFile() } } }
                    "Kotlin" { layout { "generated" { "*".ktFile() } } }
                }
            }
            val tree = repositoryOf { "generated" { "Api.kt"() } }

            definition.validate(tree).filterIsInstance<AmbiguousLayout>().map { it.path } shouldBe
                listOf("generated/Api.kt")
        }
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

        "実ファイル単位 — 制約への注意書きと、ファイル向けの How to fix" {
            val definition = architectureOf {
                // Two role names of the same length, so the `Declared by:` column needs no
                // padding and this spec can pin the block as one string.
                "ui".group {
                    "Component" { layout { "home" { "*".ktFile() } } }
                    "ViewModel" { layout { "home" { "*ViewModel".ktFile() } } }
                }
            }
            val violations = definition.validate(
                repositoryOf { "home" { "HomeScreen.kt"(); "HomeViewModel.kt"() } },
            )
            val claims = violations.filterIsInstance<AmbiguousLayout>().single().claims
            val (siteA, siteB) = claims.map { it.declaredAt }

            violations.report() shouldBe
                """
                Katachi check found 1 warning. Warnings never fail the check.

                [AmbiguousLayout] home/HomeViewModel.kt
                  2 roles claim this file, so it belongs to all of them.
                  Every constraint those roles declare is checked against it.

                  Declared by:
                    ui/Component $siteA
                    ui/ViewModel $siteB

                  How to fix:
                    - Narrow one of the two declarations so that each of these files is claimed by one role
                    - Or keep the overlap, and make every constraint of both roles hold for these files
                """.trimIndent()
        }

        "実ファイル単位 — 残りのファイルは3件まで並べて、あとは数える" {
            val definition = architectureOf {
                "ui".group {
                    "Component" { layout { "*" { "*".ktFile() } } }
                    "ViewModel" { layout { "*" { "*ViewModel".ktFile() } } }
                }
            }
            val tree = repositoryOf {
                "a" { "AViewModel.kt"() }
                "b" { "BViewModel.kt"() }
                "c" { "CViewModel.kt"() }
                "d" { "DViewModel.kt"() }
                "e" { "EViewModel.kt"() }
            }

            val report = definition.validate(tree).report()

            report shouldContain "[AmbiguousLayout] a/AViewModel.kt"
            report shouldContain "Other files: b/BViewModel.kt, c/CViewModel.kt, d/DViewModel.kt, and 1 more"
        }

        "実ファイル単位 — 3つ以上の役割で件数と How to fix が複数形になる" {
            val definition = architectureOf {
                "ui".group {
                    "Component" { layout { "home" { "*".ktFile() } } }
                    "ViewModel" { layout { "home" { "*ViewModel".ktFile() } } }
                    "Home" { layout { "home" { "Home*".ktFile() } } }
                }
            }

            val report = definition.validate(repositoryOf { "home" { "HomeViewModel.kt"() } }).report()

            report shouldContain "3 roles claim this file, so it belongs to all of them."
            report shouldContain "- Narrow the declarations so that each of these files is claimed by one role"
            report shouldContain "- Or keep the overlap, and make every constraint of all of them hold for these files"
        }
    }
})
