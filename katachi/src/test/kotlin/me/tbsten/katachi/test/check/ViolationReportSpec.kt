package me.tbsten.katachi.test.check

import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldStartWith
import me.tbsten.katachi.check.MissingFile
import me.tbsten.katachi.check.UnexpectedDirectory
import me.tbsten.katachi.check.Violation
import me.tbsten.katachi.check.internal.report
import me.tbsten.katachi.check.validate
import me.tbsten.katachi.dsl.files.FsPath
import me.tbsten.katachi.dsl.gradle.gradle
import me.tbsten.katachi.dsl.kotlin.ktFile
import me.tbsten.katachi.dsl.kotlin.ktsFile

class ViolationReportSpec : FreeSpec({
    "Unexpected なファイル" - {
        val definition = architectureOf {
            "domain".group {
                "UseCase".role { layout { "core/domain/useCase" / "*UseCase".ktFile() } }
                "Repository".role { layout { "core/domain/repository" / "*Repository".ktFile() } }
            }
        }
        val tree = repositoryOf {
            "core/domain" {
                "TokenRefresher.kt"()
                "useCase" { "GetUserUseCase.kt"() }
                "repository" { "UserRepository.kt"() }
            }
        }

        "移動先の候補にファイル名のパターンが添えられ貼り付けられる DSL 断片が出る" {
            definition.validate(tree).report() shouldBe
                """
                Katachi check failed: 1 violation (Unexpected: 1)

                [UnexpectedFile] core/domain/TokenRefresher.kt
                  No role is defined for this file.

                  Nearby locations:
                    domain.UseCase    core/domain/useCase/ (*UseCase.kt)
                    domain.Repository core/domain/repository/ (*Repository.kt)

                  How to fix:
                    - Move it to one of the locations above, with a name matching the pattern in parentheses
                    - Delete it if it is not needed
                    - Add a new role for it:
                        "TokenRefresher".role {
                          layout {
                            "core/domain" / "TokenRefresher".ktFile()
                          }
                        }
                """.trimIndent()
        }

        "ルートを渡すとパスと移動先は file URI になり、DSL 断片は相対のまま" {
            definition.validate(tree).report(projectRoot = FsPath.of("/repo")) shouldBe
                """
                Katachi check failed: 1 violation (Unexpected: 1)

                [UnexpectedFile] file:///repo/core/domain/TokenRefresher.kt
                  No role is defined for this file.

                  Nearby locations:
                    domain.UseCase    file:///repo/core/domain/useCase/ (*UseCase.kt)
                    domain.Repository file:///repo/core/domain/repository/ (*Repository.kt)

                  How to fix:
                    - Move it to one of the locations above, with a name matching the pattern in parentheses
                    - Delete it if it is not needed
                    - Add a new role for it:
                        "TokenRefresher".role {
                          layout {
                            "core/domain" / "TokenRefresher".ktFile()
                          }
                        }
                """.trimIndent()
        }

        "グロブの文字を名前に含む実在ファイルも file URI になる" {
            val bracketed = repositoryOf {
                "core/domain" {
                    "[id].kt"()
                    "useCase" { "GetUserUseCase.kt"() }
                    "repository" { "UserRepository.kt"() }
                }
            }
            val firstViolationLine = definition.validate(bracketed).report(projectRoot = FsPath.of("/repo"))
                .lines().first { it.startsWith("[UnexpectedFile]") }
            firstViolationLine shouldBe "[UnexpectedFile] file:///repo/core/domain/%5Bid%5D.kt"
        }

        "出力に ANSI エスケープと非 ASCII の記号が含まれない" {
            definition.validate(tree).report().all { it.code in 0x20..0x7E || it == '\n' } shouldBe true
            definition.validate(tree).report(projectRoot = FsPath.of("/repo/日本 語"))
                .all { it.code in 0x20..0x7E || it == '\n' } shouldBe true
        }

        "anyFile の置き場所にはパターンが添えられず Move の行は今までの文のまま" {
            val open = architectureOf {
                "domain".group {
                    "UseCase".role { layout { "core/domain/useCase" { anyFile() } } }
                }
            }
            val report = open.validate(repositoryOf { "core/domain" { "TokenRefresher.kt"(); "useCase" { "A.kt"() } } }).report()

            report.lines().filter { it.contains("domain.UseCase") || it.startsWith("    - Move") } shouldBe listOf(
                "    domain.UseCase core/domain/useCase/",
                "    - Move it to one of the locations above",
            )
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
                        "TmpExperiment".role {
                          layout {
                            "tmp-experiment".ignore()
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
                        "BuildGradle".role {
                          layout {
                            "build.gradle".ktsFile()
                          }
                        }

                [UnexpectedFile] libs.versions.toml
                  No role is defined for this file.

                  How to fix:
                    - Delete it if it is not needed
                    - Add a new role for it:
                        "LibsVersionsToml".role {
                          layout {
                            "libs.versions.toml".file()
                          }
                        }
                """.trimIndent()
        }
    }

    "DSL 断片の Role 名" - {
        "gradle() の group と同じ名前は避け、先頭の点を Dot にして区別する" {
            architectureOf { gradle(requireWrapper = false) }
                .validate(repositoryOf { ".gradle" { "file-system.probe"() } })
                .suggestedRoleLines() shouldBe listOf("\"DotGradle\".role {")
        }

        "ルートの Role と同じ名前は避け、親のディレクトリ名を前に足す" {
            architectureOf {
                "Model".role { layout { "model" / "*".ktFile() } }
                "Docs".role { layout { "xdocs" / "index.md".file() } }
            }.validate(repositoryOf { "xdocs" { "index.md"(); "model" { "User.md"() } } })
                .suggestedRoleLines() shouldBe listOf("\"XdocsModel\".role {")
        }

        "親のディレクトリ名を足した名前も埋まっていれば番号を付ける" {
            architectureOf {
                "Model".role { layout { "model" / "*".ktFile() } }
                "XdocsModel".role { layout { "xdocs" / "index.md".file() } }
            }.validate(repositoryOf { "xdocs" { "index.md"(); "model" { "User.md"() } } })
                .suggestedRoleLines() shouldBe listOf("\"XdocsModel2\".role {")
        }

        "1 つの報告の中で先に勧めた名前も避ける" {
            architectureOf {
                "App".role { layout { "app" / "Main".ktFile() } }
                "Lib".role { layout { "lib" / "Lib".ktFile() } }
            }.validate(repositoryOf { "app" { "Main.kt"(); "Util.kt"() }; "lib" { "Lib.kt"(); "Util.kt"() } })
                .suggestedRoleLines() shouldBe listOf("\"Util\".role {", "\"LibUtil\".role {")
        }

        "大文字小文字だけ違う名前は同じ名前として避ける (KATACHI-443)" {
            architectureOf { "UseCase".role { layout { "usecase" / "*".ktFile() } } }
                .validate(repositoryOf { "usecase" { "A.kt"() }; "other" { "Usecase.kt"() } })
                .suggestedRoleLines() shouldBe listOf("\"Other\".role {")
        }

        "docs の根のページ README と重なる名前は避ける (KATACHI-443)" {
            architectureOf { "App".role { layout { "app" / "Main".ktFile() } } }
                .validate(repositoryOf { "app" { "Main.kt"() }; "readme" { "a.txt"() } })
                .suggestedRoleLines() shouldBe listOf("\"Readme2\".role {")
        }

        "group の中の Role とは名前が重ならないので避けない" {
            layoutArchitecture(group = "App", role = "Util") { "app" / "Main".ktFile() }
                .validate(repositoryOf { "app" { "Main.kt"(); "Util.kt"() } })
                .suggestedRoleLines() shouldBe listOf("\"Util\".role {")
        }

        "勧めた断片をそのまま貼った定義は読み込めて違反が無くなる" {
            val repository = repositoryOf { ".gradle" { "file-system.probe"() } }
            val suggested = architectureOf { gradle(requireWrapper = false) }
                .validate(repository)
                .filterIsInstance<UnexpectedDirectory>()
                .map { it.suggestedRole }

            suggested shouldBe listOf("DotGradle")
            architectureOf {
                gradle(requireWrapper = false)
                for (name in suggested.filterNotNull()) name.role { layout { ".gradle".ignore() } }
            }.validate(repository) shouldBe emptyList()
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
                  No file has been created yet for role tool.VersionCatalog.
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
            report.lines().dropLast(1).last() shouldBe "Showing first 10 (2 more)"
            report.lines().count { it.startsWith("[") } shouldBe 10
        }

        "maxViolations を変えるとその件数まで出る" {
            val report = definition.validate(tree).report(maxViolations = 3)

            report shouldStartWith "Katachi check failed: 12 violations (Unexpected: 12)\n"
            report.lines().dropLast(1).last() shouldBe "Showing first 3 (9 more)"
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

private fun List<Violation>.suggestedRoleLines(): List<String> =
    report().lines().map { it.trim() }.filter { it.endsWith(".role {") }
