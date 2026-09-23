package me.tbsten.katachi.test.check

import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldNotContain
import me.tbsten.katachi.check.KonsistCheck
import me.tbsten.katachi.check.report
import me.tbsten.katachi.check.validate
import me.tbsten.katachi.dsl.ConstraintFailure
import me.tbsten.katachi.dsl.DeclarationSite
import me.tbsten.katachi.dsl.FileSetConstraint

/**
 * What the two constraint blocks actually print.
 *
 * Snapshots rather than `shouldContain`, because the shape of a block is the thing: an agent
 * greps `^\[` and reads what hangs under it, so a line gained, lost or reordered is a change
 * to the format whether or not any single assertion noticed.
 *
 * Every declaration site here is passed explicitly. A captured one would be this file's own
 * line number, and a snapshot that has to be re-typed every time a case moves is a snapshot
 * nobody keeps honest.
 *
 * NOTE: このファイルのパッケージを `me.tbsten.katachi.check` にしてはいけない。
 * captureDeclarationSite() がライブラリ自身のフレームとして読み飛ばしてしまう。
 */

/** The site every snapshot below points at, so the expected text can be written out. */
private val DECLARED_AT: DeclarationSite = DeclarationSite("ProjectArchitecture.kt", 61)

/** Rejects one file, naming what inside it was rejected. */
private fun rejectingDeclaration(file: String, declaration: String?, line: Int?): FileSetConstraint =
    FileSetConstraint { subject ->
        subject.files.filter { it == file }.map { ConstraintFailure(it, declaration, line) }
    }

class ConstraintReportSpec : FreeSpec({
    "UnsatisfiedConstraint" - {
        "名前・宣言・行・layout をすべて持つブロック" {
            val arch = architectureOf {
                "domain".group {
                    "UseCase" {
                        layout {
                            "alpha" {
                                constraint(
                                    name = "no helper",
                                    declaredAt = DECLARED_AT,
                                    check = rejectingDeclaration("alpha/Helper.kt", "Helper", 12),
                                )
                                "*.kt".file()
                            }
                        }
                    }
                }
            }

            arch.validate(repositoryOf { "alpha" { "Helper.kt"() } }, KonsistCheck())
                .report() shouldBe
                """
                Katachi check failed: 1 violation (Constraint: 1)

                [UnsatisfiedConstraint] alpha/Helper.kt
                  Role: domain/UseCase / Constraint: "no helper"
                  Declaration: Helper (line 12)
                  Declared at: ProjectArchitecture.kt:61 (layout of alpha)
                """.trimIndent()
        }

        "名前も宣言も layout も無いブロックは2行に縮む" {
            val arch = architectureOf {
                "domain".group {
                    "UseCase" {
                        constraint(
                            declaredAt = DECLARED_AT,
                            check = rejectingDeclaration("alpha/Helper.kt", null, null),
                        )
                        layout { "alpha" / "*.kt".file() }
                    }
                }
            }

            arch.validate(repositoryOf { "alpha" { "Helper.kt"() } }, KonsistCheck())
                .report() shouldBe
                """
                Katachi check failed: 1 violation (Constraint: 1)

                [UnsatisfiedConstraint] alpha/Helper.kt
                  Role: domain/UseCase
                  Declared at: ProjectArchitecture.kt:61
                """.trimIndent()
        }

        "行が分からないときは宣言名だけを出す" {
            val arch = architectureOf {
                "domain".group {
                    "UseCase" {
                        layout {
                            "alpha" {
                                constraint(
                                    name = "no helper",
                                    declaredAt = DECLARED_AT,
                                    check = rejectingDeclaration("alpha/Helper.kt", "Helper", null),
                                )
                                "*.kt".file()
                            }
                        }
                    }
                }
            }

            arch.validate(repositoryOf { "alpha" { "Helper.kt"() } }, KonsistCheck())
                .report().lines()[4] shouldBe "  Declaration: Helper"
        }

        "How to fix は出さない" {
            // 制約の中身は任意の Kotlin 式なので、何を満たせばよいかを katachi は知らない。
            // 推測を書いた瞬間、それは katachi が作った嘘になる。
            val arch = architectureOf {
                "domain".group {
                    "UseCase" {
                        layout {
                            "alpha" {
                                constraint(
                                    name = "no helper",
                                    declaredAt = DECLARED_AT,
                                    check = rejectsEverything(),
                                )
                                "*.kt".file()
                            }
                        }
                    }
                }
            }

            arch.validate(repositoryOf { "alpha" { "Helper.kt"() } }, KonsistCheck())
                .report() shouldNotContain "How to fix:"
        }
    }

    "UncheckedConstraint" - {
        "NotEvaluated のブロックと末尾の件数行" {
            val arch = architectureOf {
                "domain".group {
                    "UseCase" {
                        layout {
                            "alpha" {
                                constraint(
                                    name = "invoke",
                                    declaredAt = DECLARED_AT,
                                    check = silentCheck(),
                                )
                                "*.kt".file()
                            }
                        }
                    }
                }
            }

            arch.validate(repositoryOf { "alpha" { "A.kt"() } }).report() shouldBe
                """
                Katachi check failed: 1 violation (Failed: 1)

                [UncheckedConstraint] alpha
                  Nothing evaluated this constraint, so nothing is known about it.
                  Role: domain/UseCase / Constraint: "invoke"
                  Declared at: ProjectArchitecture.kt:61 (layout of alpha)

                  How to fix:
                    - Pass KonsistCheck() to assert(): projectArchitecture.assert(KonsistCheck())
                    - Remove the constraint at ProjectArchitecture.kt:61 if it is no longer wanted

                1 constraint could not be evaluated.
                """.trimIndent()
        }

        "Failed のブロックは Cause を持ち、How to fix が3行になる" {
            val arch = architectureOf {
                "domain".group {
                    "UseCase" {
                        layout {
                            "alpha" {
                                constraint(
                                    name = "invoke",
                                    declaredAt = DECLARED_AT,
                                    check = throwing { IllegalStateException("boom") },
                                )
                                "*.kt".file()
                            }
                        }
                    }
                }
            }

            arch.validate(repositoryOf { "alpha" { "A.kt"() } }, KonsistCheck()).report() shouldBe
                """
                Katachi check failed: 1 violation (Failed: 1)

                [UncheckedConstraint] alpha
                  Katachi failed while evaluating this constraint, so nothing is known about it.
                  Role: domain/UseCase / Constraint: "invoke"
                  Declared at: ProjectArchitecture.kt:61 (layout of alpha)
                  Cause: java.lang.IllegalStateException: boom

                  How to fix:
                    - Read the cause above: it says what stopped the constraint
                    - Check the constraint block at ProjectArchitecture.kt:61
                    - If the cause is a KatachiInternalException, report it at https://github.com/TBSten/katachi/issues

                1 constraint could not be evaluated.
                """.trimIndent()
        }
    }

    "打ち切り" - {
        "Constraint のブロックが1件は残り、打ち切り行が出る" {
            val arch = architectureOf {
                "domain".group {
                    "UseCase" {
                        layout {
                            "alpha" {
                                constraint(
                                    name = "no helper",
                                    declaredAt = DECLARED_AT,
                                    check = rejectsEverything(),
                                )
                                "*.kt".file()
                            }
                        }
                    }
                }
            }
            val tree = repositoryOf {
                "alpha" { "Helper.kt"() }
                repeat(12) { index -> "note-$index.md"() }
            }

            val lines = arch.validate(tree, KonsistCheck()).report(maxViolations = 2).lines()

            lines.filter { it.startsWith("[") } shouldBe listOf(
                "[UnexpectedFile] note-0.md",
                "[UnsatisfiedConstraint] alpha/Helper.kt",
            )
            lines.last() shouldBe "Showing first 2 (11 more)"
        }
    }

    "ASCII のみ" - {
        "katachi が書く文面に非 ASCII が混ざらない" {
            val arch = architectureOf {
                "domain".group {
                    "UseCase" {
                        constraint(
                            name = "role wide",
                            declaredAt = DECLARED_AT,
                            check = throwing { IllegalStateException("boom") },
                        )
                        layout {
                            "alpha" {
                                constraint(
                                    name = "alpha only",
                                    declaredAt = DECLARED_AT,
                                    check = rejectsEverything(),
                                )
                                "*.kt".file()
                            }
                        }
                    }
                }
            }

            val report = arch.validate(repositoryOf { "alpha" { "A.kt"() } }, KonsistCheck()).report()

            report.filterNot { it.code < 128 } shouldBe ""
        }
    }
})
