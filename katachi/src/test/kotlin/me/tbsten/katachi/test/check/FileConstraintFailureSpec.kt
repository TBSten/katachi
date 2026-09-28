package me.tbsten.katachi.test.check

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.types.shouldBeInstanceOf
import java.io.IOException
import me.tbsten.katachi.check.KatachiFileConstraintSubjectException
import me.tbsten.katachi.check.FileConstraintCheck
import me.tbsten.katachi.check.UncheckedFileConstraintReason
import me.tbsten.katachi.check.Violation
import me.tbsten.katachi.check.internal.report
import me.tbsten.katachi.check.internal.validate
import me.tbsten.katachi.dsl.FileConstraintFailure
import me.tbsten.katachi.dsl.FileConstraint
import me.tbsten.katachi.processor.ArchitectureProcessNoArgContext
import me.tbsten.katachi.processor.ArchitectureProcessorNoArg
import me.tbsten.katachi.test.dsl.files.failingAt

/**
 * What happens when a constraint cannot answer at all.
 *
 * Every case here ends the same way — one `[UncheckedFileConstraint]` with `reason=Failed`, and
 * every other constraint, check and violation of the run still reported. That is the rule
 * errors.md states per file, applied one level up: if this one fails, the neighbour can still
 * answer, so the neighbour must still be asked.
 *
 * What a satisfied or unsatisfied constraint does is [FileConstraintCheckSpec]'s; the exact text of
 * these blocks is [FileConstraintReportSpec]'s.
 *
 * NOTE: このファイルのパッケージを `me.tbsten.katachi.check` にしてはいけない。
 * captureDeclarationSite() がライブラリ自身のフレームとして読み飛ばしてしまう。
 */

/** A check outside katachi that is broken rather than failing: it throws instead of answering. */
private class ThrowingProcessor(private val failure: () -> Throwable) :
    ArchitectureProcessorNoArg<List<Violation>> {
    override fun process(context: ArchitectureProcessNoArgContext): Result<List<Violation>> =
        throw failure()
}

/**
 * The sentences a report ends with: the unindented full stops, which is what tells them apart
 * from a block's own lines (indented) and from the summary and truncation lines (neither ends
 * in a full stop).
 */
private fun List<Violation>.tailSentences(): List<String> = report().lines()
    .filter { it.isNotBlank() && !it.startsWith(" ") && !it.startsWith("[") && it.endsWith('.') }

class FileConstraintFailureSpec : FreeSpec({
    "ブロックが投げる" - {
        "reason=Failed になり、原因がそのまま乗る" {
            val arch = architectureOf {
                "domain".group {
                    "UseCase" {
                        layout {
                            "alpha" {
                                fileConstraint("invoke", check = throwing { IllegalStateException("boom") })
                                "*.kt".file()
                            }
                        }
                    }
                }
            }

            val unchecked = arch
                .validate(repositoryOf { "alpha" { "A.kt"() } }, FileConstraintCheck())
                .unchecked()
                .single()

            unchecked.reason shouldBe UncheckedFileConstraintReason.Failed
            unchecked.constraintName shouldBe "invoke"
            unchecked.layoutPath shouldBe "alpha"
            unchecked.cause.shouldBeInstanceOf<IllegalStateException>().message shouldBe "boom"
        }

        "1つ目が落ちても残りの制約は評価される" {
            val arch = architectureOf {
                "domain".group {
                    "UseCase" {
                        layout {
                            "alpha" {
                                fileConstraint("first", check = throwing { IllegalStateException("boom") })
                                fileConstraint("second", check = rejectsEverything())
                                "*.kt".file()
                            }
                        }
                    }
                }
            }

            val violations = arch.validate(repositoryOf { "alpha" { "A.kt"() } }, FileConstraintCheck())

            violations.labels() shouldBe listOf(
                "[UnsatisfiedFileConstraint] alpha/A.kt",
                "[UncheckedFileConstraint] alpha",
            )
            violations.unsatisfied().single().constraintName shouldBe "second"
            violations.unchecked().single().constraintName shouldBe "first"
        }

        "役割直下の制約が落ちても1行目がドットにならない" {
            val arch = architectureOf {
                "domain".group {
                    "UseCase" {
                        fileConstraint("role wide", check = throwing { IllegalStateException("boom") })
                        layout { "alpha" / "*.kt".file() }
                    }
                }
            }

            arch.validate(repositoryOf { "alpha" { "A.kt"() } }, FileConstraintCheck())
                .unchecked().single().path shouldBe "alpha"
        }

        "握ってはいけない4種は素通りする" {
            val fatals = listOf<() -> Throwable>(
                { StackOverflowError() },
                { InterruptedException("stop") },
                { AssertionError("this is a result, not a failure") },
                { NoClassDefFoundError("SomeClass") },
            )

            for (fatal in fatals) {
                val arch = architectureOf {
                    "domain".group {
                        "UseCase" {
                            layout { "alpha" { fileConstraint("fatal", check = throwing(fatal)); "*.kt".file() } }
                        }
                    }
                }

                val thrown = shouldThrow<Throwable> {
                    arch.validate(repositoryOf { "alpha" { "A.kt"() } }, FileConstraintCheck())
                }
                thrown::class shouldBe fatal()::class
            }
        }
    }

    "subject の外を答える" - {
        "答え全体が退けられ、reason=Failed になる" {
            val outsider = FileConstraint { subject ->
                subject.files.map { FileConstraintFailure(it) } + FileConstraintFailure("build.gradle.kts")
            }
            val arch = architectureOf {
                "domain".group {
                    "UseCase" {
                        layout { "alpha" { fileConstraint("no outsiders", check = outsider); "*.kt".file() } }
                    }
                }
            }

            val violations = arch.validate(
                repositoryOf {
                    "alpha" { "A.kt"() }
                    "build.gradle.kts"()
                },
                FileConstraintCheck(),
            )

            // 覆っていたファイルの違反も残らない。1 つでも外を答えたバックエンドは、
            // 自分が正しいファイルを見ているという根拠を失っている。
            violations.unsatisfied().shouldBeEmpty()
            val unchecked = violations.unchecked().single()
            unchecked.reason shouldBe UncheckedFileConstraintReason.Failed
            val cause = unchecked.cause.shouldBeInstanceOf<KatachiFileConstraintSubjectException>()
            cause.outside shouldBe listOf("build.gradle.kts")
            cause.role shouldBe "domain.UseCase"
            cause.constraintName shouldBe "no outsiders"
        }

        "何件落としたかがレポートの Cause 行に出る" {
            val outsider = FileConstraint {
                listOf("a.kt", "b.kt", "c.kt", "d.kt").map { FileConstraintFailure(it) }
            }
            val arch = architectureOf {
                "domain".group {
                    "UseCase" {
                        layout { "alpha" { fileConstraint("no outsiders", check = outsider); "*.kt".file() } }
                    }
                }
            }

            val violations = arch.validate(repositoryOf { "alpha" { "A.kt"() } }, FileConstraintCheck())

            violations.unchecked().single().cause
                .shouldBeInstanceOf<KatachiFileConstraintSubjectException>()
                .outside shouldBe listOf("a.kt", "b.kt", "c.kt", "d.kt")
            // 件数が先、名前は3件まで。残りは "..." に畳まれるが、件数は畳まれない。
            violations.report() shouldContain
                "Cause: me.tbsten.katachi.check.KatachiFileConstraintSubjectException: " +
                "File constraint \"no outsiders\" of role \"domain.UseCase\" answered about 4 files " +
                "it was not asked about: file:///repo/a.kt, file:///repo/b.kt, file:///repo/c.kt, ..."
        }
    }

    "他の失敗との共存" - {
        "末尾の3文が並び、ファイルも検査も制約もそれぞれ数えられる" {
            val arch = architectureOf {
                "domain".group {
                    "UseCase" {
                        layout {
                            // `src` はモジュール探索が降りていかないディレクトリ。降りる場所で
                            // `isDirectory` を落とすと、走査より前のモジュール探索が素通しで
                            // 投げてしまい、このスペックの本題に届かない。
                            "src" {
                                fileConstraint("invoke", check = throwing { IllegalStateException("constraint boom") })
                                anyFile()
                            }
                        }
                    }
                }
            }
            val tree = repositoryOf { "src" { "A.kt"(); "Broken.kt"() } }
                .failingAt("/repo/src/Broken.kt") { IOException("cannot read it") }

            val violations = arch.validate(
                tree,
                FileConstraintCheck(),
                ThrowingProcessor { IllegalStateException("check boom") },
            )

            violations.labels() shouldBe listOf(
                "[UncheckedFile] src/Broken.kt",
                "[UncheckedFileConstraint] src",
                "[UncheckedCheck] .",
            )
            violations.tailSentences() shouldBe listOf(
                "1 file could not be checked.",
                "1 constraint could not be evaluated.",
                "1 check could not be run.",
            )
        }

        "打ち切っても末尾の件数行は消えない" {
            val arch = architectureOf {
                "domain".group {
                    "UseCase" {
                        layout {
                            "alpha" {
                                fileConstraint("invoke", check = throwing { IllegalStateException("boom") })
                                "*.kt".file()
                            }
                        }
                    }
                }
            }

            arch.validate(repositoryOf { "alpha" { "A.kt"() } }, FileConstraintCheck())
                .report(maxViolations = 0)
                .lines().last() shouldBe "1 constraint could not be evaluated."
        }

        "複数落ちれば複数形になる" {
            val arch = architectureOf {
                "domain".group {
                    "UseCase" {
                        layout {
                            "alpha" {
                                fileConstraint("first", check = throwing { IllegalStateException("boom") })
                                fileConstraint("second", check = throwing { IllegalStateException("boom") })
                                "*.kt".file()
                            }
                        }
                    }
                }
            }

            arch.validate(repositoryOf { "alpha" { "A.kt"() } }, FileConstraintCheck())
                .report().lines().last() shouldBe "2 constraints could not be evaluated."
        }
    }
})
