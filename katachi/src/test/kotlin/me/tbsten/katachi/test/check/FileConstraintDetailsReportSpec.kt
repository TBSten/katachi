package me.tbsten.katachi.test.check

import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldNotContain
import me.tbsten.katachi.check.FileConstraintCheck
import me.tbsten.katachi.check.internal.report
import me.tbsten.katachi.check.internal.validate
import me.tbsten.katachi.dsl.Architecture
import me.tbsten.katachi.dsl.DeclarationSite
import me.tbsten.katachi.dsl.FileConstraint
import me.tbsten.katachi.dsl.FileConstraintAllowedList
import me.tbsten.katachi.dsl.FileConstraintFailure
import me.tbsten.katachi.dsl.FileConstraintFix
import me.tbsten.katachi.dsl.FileConstraintReason

/**
 * What the details a constraint attaches to a failure print as: the `<label>: <text>` lines, the
 * allowed list, `Declared at:` pointing at the rule's own line, and the constraint's `How to fix:`.
 *
 * The blocks without any of them are [FileConstraintReportSpec]'s, unchanged -- that spec passing
 * as it was is what says a constraint that says nothing still prints exactly what it used to.
 *
 * NOTE: このファイルのパッケージを `me.tbsten.katachi.check` にしてはいけない。
 * captureDeclarationSite() がライブラリ自身のフレームとして読み飛ばしてしまう。
 */

/** Where the constraint as a whole is declared. */
private val CONSTRAINT_SITE: DeclarationSite = DeclarationSite("UseCaseRole.kt", 12)

/** Where the one line of the rule that said no is declared. */
private val RULE_SITE: DeclarationSite = DeclarationSite("UseCaseRole.kt", 16)

/**
 * One role with one constraint over the Kotlin files of `core/domain`, written on the role so it
 * has no layout path of its own.
 */
private fun useCaseArchitecture(check: FileConstraint): Architecture = architectureOf {
    "domain".group {
        "UseCase".role {
            fileConstraint(name = "ktFileContent", declaredAt = CONSTRAINT_SITE, check = check)
            layout { "core/domain" { "*.kt".file() } }
        }
    }
}

/** Answers with [failure] about every file it is handed. */
private fun answering(failure: (String) -> FileConstraintFailure): FileConstraint =
    FileConstraint { subject -> subject.files.map(failure) }

/** The report of [arch] over a tree holding `core/domain/GetUserUseCase.kt` only. */
private fun reportOf(arch: Architecture): String =
    arch.validate(repositoryOf { "core/domain" { "GetUserUseCase.kt"() } }, FileConstraintCheck()).report()

class FileConstraintDetailsReportSpec : FreeSpec({
    "理由・許可の一覧・規則の行・直し方がすべてあるブロック" {
        val arch = useCaseArchitecture(
            answering { file ->
                FileConstraintFailure(
                    file = file,
                    declaration = "GetUserUseCaseImpl",
                    line = 9,
                    reasons = listOf(FileConstraintReason("Not allowed", "class GetUserUseCaseImpl")),
                    allowed = FileConstraintAllowedList(
                        heading = "Allowed here",
                        entries = listOf(
                            "interface GetUserUseCase",
                            "class DefaultGetUserUseCase : GetUserUseCase  (missing)",
                        ),
                    ),
                    howToFix = listOf(
                        FileConstraintFix("Rename it to DefaultGetUserUseCase"),
                        FileConstraintFix("Move it to a file of another role"),
                        FileConstraintFix(
                            "Allow it in ktFileContent { } at UseCaseRole.kt:16:",
                            code = listOf("allow(`class`, \"GetUserUseCaseImpl\")"),
                        ),
                    ),
                    ruleDeclaredAt = RULE_SITE,
                )
            },
        )

        reportOf(arch) shouldBe
            """
            Katachi check failed: 1 violation (FileConstraint: 1)

            [UnsatisfiedFileConstraint] core/domain/GetUserUseCase.kt
              Role: domain.UseCase / FileConstraint: "ktFileContent"
              Declaration: GetUserUseCaseImpl (line 9)
              Not allowed: class GetUserUseCaseImpl
              Allowed here:
                interface GetUserUseCase
                class DefaultGetUserUseCase : GetUserUseCase  (missing)
              Declared at: UseCaseRole.kt:16

              How to fix:
                - Rename it to DefaultGetUserUseCase
                - Move it to a file of another role
                - Allow it in ktFileContent { } at UseCaseRole.kt:16:
                    allow(`class`, "GetUserUseCaseImpl")
            """.trimIndent()
    }

    "理由は書いた順に1行ずつ並ぶ" {
        val arch = useCaseArchitecture(
            answering { file ->
                FileConstraintFailure(
                    file = file,
                    declaration = "HomeUiState",
                    line = 3,
                    reasons = listOf(
                        FileConstraintReason("Not allowed", "class HomeUiState"),
                        FileConstraintReason("Expected", "data class HomeUiState"),
                        FileConstraintReason("Differs in", "modifiers (missing: data)"),
                    ),
                )
            },
        )

        reportOf(arch) shouldBe
            """
            Katachi check failed: 1 violation (FileConstraint: 1)

            [UnsatisfiedFileConstraint] core/domain/GetUserUseCase.kt
              Role: domain.UseCase / FileConstraint: "ktFileContent"
              Declaration: HomeUiState (line 3)
              Not allowed: class HomeUiState
              Expected: data class HomeUiState
              Differs in: modifiers (missing: data)
              Declared at: UseCaseRole.kt:12
            """.trimIndent()
    }

    "許可の一覧が空なら (nothing) と出る" {
        val arch = useCaseArchitecture(
            answering { file ->
                FileConstraintFailure(file, allowed = FileConstraintAllowedList("Allowed members of class Foo", emptyList()))
            },
        )

        reportOf(arch).lines().takeLast(3) shouldBe listOf(
            "  Allowed members of class Foo:",
            "    (nothing)",
            "  Declared at: UseCaseRole.kt:12",
        )
    }

    "直し方が無ければ How to fix: も、その前の空行も出ない" {
        val arch = useCaseArchitecture(
            answering { file ->
                FileConstraintFailure(file, reasons = listOf(FileConstraintReason("Reason", "use cases are interfaces")))
            },
        )

        val report = reportOf(arch)

        report shouldNotContain "How to fix:"
        report.lines().takeLast(2) shouldBe listOf(
            "  Reason: use cases are interfaces",
            "  Declared at: UseCaseRole.kt:12",
        )
    }

    "規則の行が無ければ、制約そのものの宣言元を出す" {
        val arch = useCaseArchitecture(answering { file -> FileConstraintFailure(file, declaration = "Foo") })

        reportOf(arch).lines().last() shouldBe "  Declared at: UseCaseRole.kt:12"
    }

    "直し方のコードは字下げを保ち、改行を含む要素は行に分かれる" {
        val arch = useCaseArchitecture(
            answering { file ->
                FileConstraintFailure(
                    file = file,
                    howToFix = listOf(
                        FileConstraintFix(
                            "Allow its members:",
                            code = listOf(
                                "allow(`class`, \"Foo\") {",
                                "    allow(private, anythingElse)",
                                "}",
                                "",
                                "allow(`object`, \"Bar\") {\n    allow(`val`, \"x\")\n}",
                            ),
                        ),
                    ),
                )
            },
        )

        reportOf(arch).lines().takeLast(9) shouldBe listOf(
            "  How to fix:",
            "    - Allow its members:",
            "        allow(`class`, \"Foo\") {",
            "            allow(private, anythingElse)",
            "        }",
            "",
            "        allow(`object`, \"Bar\") {",
            "            allow(`val`, \"x\")",
            "        }",
        )
    }

    "理由・見出し・項目・直し方の文は改行の前までを1行で出す" {
        val arch = useCaseArchitecture(
            answering { file ->
                FileConstraintFailure(
                    file = file,
                    reasons = listOf(FileConstraintReason("Not\nallowed", "class Foo\nsecond line")),
                    allowed = FileConstraintAllowedList("Allowed\nhere", listOf("  interface Foo  \nrest")),
                    howToFix = listOf(FileConstraintFix("Rename it\nplease")),
                )
            },
        )

        reportOf(arch).lines().drop(4) shouldBe listOf(
            "  Not: class Foo",
            "  Allowed:",
            "    interface Foo",
            "  Declared at: UseCaseRole.kt:12",
            "",
            "  How to fix:",
            "    - Rename it",
        )
    }

    "layout の中の制約は、規則の行の後ろに layout を添える" {
        val arch = architectureOf {
            "domain".group {
                "UseCase".role {
                    layout {
                        "core/domain" {
                            fileConstraint(
                                name = "ktFileContent",
                                declaredAt = CONSTRAINT_SITE,
                                check = answering { file -> FileConstraintFailure(file, ruleDeclaredAt = RULE_SITE) },
                            )
                            "*.kt".file()
                        }
                    }
                }
            }
        }

        reportOf(arch).lines().last() shouldBe "  Declared at: UseCaseRole.kt:16 (layout of core/domain)"
    }

    "katachi が書く文面に非 ASCII が混ざらない" {
        val arch = useCaseArchitecture(
            answering { file ->
                FileConstraintFailure(
                    file = file,
                    declaration = "Foo",
                    line = 1,
                    reasons = listOf(FileConstraintReason("Not allowed", "class Foo")),
                    allowed = FileConstraintAllowedList("Allowed here", emptyList()),
                    howToFix = listOf(FileConstraintFix("Remove it", code = listOf("not.allow(`class`, \"Foo\")"))),
                    ruleDeclaredAt = RULE_SITE,
                )
            },
        )

        reportOf(arch).filterNot { it.code < 128 } shouldBe ""
    }
})
