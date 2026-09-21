package me.tbsten.katachi.test.check

import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldStartWith
import me.tbsten.katachi.check.MissingFile
import me.tbsten.katachi.check.report
import me.tbsten.katachi.check.validate
import me.tbsten.katachi.dsl.InternalKatachiApi
import me.tbsten.katachi.dsl.ktFile
import me.tbsten.katachi.dsl.ktsFile

@OptIn(InternalKatachiApi::class)
class ViolationReportSpec : FreeSpec({
    "Unexpected なファイル" - {
        val definition = architectureOf {
            "domain".group {
                "UseCase" { layout { "core/domain/useCase" / "*UseCase".ktFile() } }
                "Repository" { layout { "core/domain/repository" / "*Repository".ktFile() } }
            }
        }
        val tree = repositoryOf {
            "core/domain" {
                "TokenRefresher.kt"()
                "useCase" { "GetUserUseCase.kt"() }
                "repository" { "UserRepository.kt"() }
            }
        }

        "移動先の候補と貼り付けられる DSL 断片が出る" {
            definition.validate(tree).report() shouldBe
                """
                Katachi check failed: 1 violation (Unexpected: 1)

                [UnexpectedFile] core/domain/TokenRefresher.kt
                  No role is defined for this file.
                  Nearby locations:
                    domain/UseCase    core/domain/useCase/
                    domain/Repository core/domain/repository/
                  How to fix:
                    - Move it to one of the locations above
                    - Delete it if it is not needed
                    - Add a new role for it:
                        "TokenRefresher" {
                          summary = "TODO"
                          layout {
                            "core/domain" / "TokenRefresher".ktFile()
                          }
                        }
                """.trimIndent()
        }

        "出力に ANSI エスケープと非 ASCII の記号が含まれない" {
            definition.validate(tree).report().all { it.code in 0x20..0x7E || it == '\n' } shouldBe true
        }
    }

    "Unexpected なディレクトリ" - {
        "打ち切ったことと対処が出る" {
            layoutArchitecture { ".gitignore".file() }
                .validate(repositoryOf { ".gitignore"(); "tmp-experiment" { "a.kt"(); "b.kt"() } })
                .report() shouldBe
                """
                Katachi check failed: 1 violation (Unexpected: 1)

                [UnexpectedDirectory] tmp-experiment
                  No role is defined for this directory. Nothing below it was checked.
                  How to fix:
                    - Delete it if it is not needed
                    - Declare what belongs in it in the layout of an existing role
                    - Add a new role for it:
                        "TmpExperiment" {
                          summary = "TODO"
                          layout {
                            "tmp-experiment" { ignore() }
                          }
                        }
                """.trimIndent()
        }
    }

    "DSL 断片" - {
        "近い配置場所が無ければ移動の案内を出さず拡張子ごとの記法で断片を出す" {
            layoutArchitecture { ".gitignore".file() }
                .validate(repositoryOf { ".gitignore"(); "build.gradle.kts"(); "libs.versions.toml"() })
                .report() shouldBe
                """
                Katachi check failed: 2 violations (Unexpected: 2)

                [UnexpectedFile] build.gradle.kts
                  No role is defined for this file.
                  How to fix:
                    - Delete it if it is not needed
                    - Add a new role for it:
                        "BuildGradle" {
                          summary = "TODO"
                          layout {
                            "build.gradle".ktsFile()
                          }
                        }

                [UnexpectedFile] libs.versions.toml
                  No role is defined for this file.
                  How to fix:
                    - Delete it if it is not needed
                    - Add a new role for it:
                        "LibsVersionsToml" {
                          summary = "TODO"
                          layout {
                            "libs.versions.toml".file()
                          }
                        }
                """.trimIndent()
        }
    }

    "Missing なファイル" - {
        "まだ作られていないという中立なトーンで宣言位置つきに出る" {
            val violations = layoutArchitecture(group = "tool", role = "VersionCatalog") {
                "gradle" / "libs.versions.toml".file()
            }.validate(repositoryOf { })

            val declaredAt = violations.filterIsInstance<MissingFile>().single().declaredAt
            declaredAt.fileName shouldBe "ViolationReportSpec.kt"

            violations.report() shouldBe
                """
                Katachi check failed: 1 violation (Missing: 1)

                [MissingFile] gradle/libs.versions.toml
                  No file has been created yet for role tool/VersionCatalog.
                  Declared at: $declaredAt
                  How to fix:
                    - If it is not implemented yet, this error is expected
                    - If it is no longer needed, remove the declaration at $declaredAt
                """.trimIndent()
        }
    }

    "打ち切り" - {
        val definition = layoutArchitecture { ".gitignore".file() }
        val tree = repositoryOf {
            ".gitignore"()
            repeat(12) { index -> "note-$index.md"() }
        }

        "11件以上あると既定では先頭10件だけが出てサマリ行は全件数を示す" {
            val report = definition.validate(tree).report()

            report shouldStartWith "Katachi check failed: 12 violations (Unexpected: 12)\n"
            report.lines().last() shouldBe "Showing first 10 (2 more)"
            report.lines().count { it.startsWith("[") } shouldBe 10
        }

        "maxViolations を変えるとその件数まで出る" {
            val report = definition.validate(tree).report(maxViolations = 3)

            report shouldStartWith "Katachi check failed: 12 violations (Unexpected: 12)\n"
            report.lines().last() shouldBe "Showing first 3 (9 more)"
            report.lines().count { it.startsWith("[") } shouldBe 3
        }

        "全件表示すると打ち切りの行が出ない" {
            val report = definition.validate(tree).report(maxViolations = Int.MAX_VALUE)

            report.lines().count { it.startsWith("[") } shouldBe 12
            report.lines().last() shouldStartWith "  "
        }
    }

    "種別の並び" - {
        "サマリ行に種別ごとの件数が出て Unexpected のブロックが Missing より先に来る" {
            val report = layoutArchitecture { "gradle" / "libs.versions.toml".file() }
                .validate(repositoryOf { "notes.md"() })
                .report()

            report shouldStartWith "Katachi check failed: 2 violations (Unexpected: 1, Missing: 1)\n"
            report.lines().filter { it.startsWith("[") } shouldBe listOf(
                "[UnexpectedFile] notes.md",
                "[MissingFile] gradle/libs.versions.toml",
            )
        }
    }
})
