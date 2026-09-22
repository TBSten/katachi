@file:OptIn(InternalKatachiApi::class)

package me.tbsten.katachi.test.konsist

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.types.shouldBeInstanceOf
import java.io.File
import me.tbsten.katachi.ExperimentalKatachiApi
import me.tbsten.katachi.InternalKatachiApi
import me.tbsten.katachi.check.ConstraintCheck
import me.tbsten.katachi.check.report
import me.tbsten.katachi.check.validate
import me.tbsten.katachi.dsl.FileSetConstraint
import me.tbsten.katachi.dsl.architecture
import me.tbsten.katachi.dsl.wholeTree
import me.tbsten.katachi.fs.RealFileSystem
import me.tbsten.katachi.konsist.KatachiKonsistDirectAssertionException
import me.tbsten.katachi.konsist.KatachiKonsistNoExpectationException
import me.tbsten.katachi.konsist.KatachiKonsistNoKotlinFilesException
import me.tbsten.katachi.konsist.KatachiKonsistScopeIncompleteException
import me.tbsten.katachi.konsist.konsist
import me.tbsten.katachi.scan.UncheckedConstraint
import me.tbsten.katachi.scan.UncheckedConstraintReason
import me.tbsten.katachi.scan.UnsatisfiedConstraint

/**
 * What every way a `konsist { }` constraint can fail to answer at all comes out as, seen through
 * the real report — the counterpart of `:katachi`'s `ConstraintFailureSpec`, one module over.
 *
 * Five paths end at `[UncheckedConstraint] reason=Failed`, each pinned once here:
 * 1. the block itself throws — covered on the `:katachi` side (`ConstraintFailureSpec`), because
 *    that propagation is generic to every `FileSetConstraint` and knows nothing about Konsist.
 * 2. scope construction itself fails ([KatachiKonsistScopeIncompleteException])
 * 3. the block never calls `must`, `mustNot` or `mustBeEmpty` ([KatachiKonsistNoExpectationException])
 * 4. the covered files hold no `.kt` Konsist can parse ([KatachiKonsistNoKotlinFilesException])
 * 5. `@Suppress("DEPRECATION_ERROR")` reaches one of Konsist's own assertions
 *    ([KatachiKonsistDirectAssertionException]) — already pinned by `KonsistShadowSpec`, from the
 *    angle of the shadow rather than the report, so it is not repeated here.
 *
 * **Why (2) is triggered by deleting a file rather than by a filesystem quirk.** This backend
 * asks Konsist to parse the same absolute paths katachi's own walk already found, once, before
 * any constraint runs (`ProjectModel.filesUnder`). If the files on disk never change between
 * that walk and Konsist's own read, the two counts always agree — `KonsistAssumptionsSpec`
 * pins what Konsist actually returns, and nothing there produces a mismatch on its own. A
 * mismatch is a bug *by construction*: the only way to provoke one without editing production
 * code is to change the disk between the two reads, which one constraint's side effect can do
 * to a second constraint's Konsist call in the same run. That is not a contrived setup; it is
 * the literal condition [KatachiKonsistScopeIncompleteException] exists for.
 */
class KonsistFailureSpec : FreeSpec({
    "スコープ生成が落ちる" - {
        "reason=Failed になり、KatachiKonsistScopeIncompleteException が cause になる" {
            fixtureProject(
                "gradlew" to "#!/bin/sh\n",
                "src/A.kt" to PUBLIC_THING_KT,
                "src/B.kt" to INTERNAL_THING_KT,
            ) { root ->
                @OptIn(ExperimentalKatachiApi::class)
                val projectArchitecture = architecture {
                    files = wholeTree()
                    "domain".group {
                        "UseCase" {
                            layout {
                                "gradlew".file()
                                "src" {
                                    "*.kt".file()
                                    // Declared before the konsist { } constraint below so that it
                                    // runs first (ConstraintFailureSpec pins declaration order as
                                    // evaluation order). It deletes B.kt, which katachi's own walk
                                    // already counted into `subject.files` for both constraints —
                                    // Konsist, reading the directory afterwards, sees one file
                                    // fewer than katachi asked for.
                                    constraint(
                                        "B.kt を消す",
                                        check = FileSetConstraint { subject ->
                                            File("${subject.projectRoot}/src/B.kt").delete()
                                            emptyList()
                                        },
                                    )
                                    "規約".konsist { classes().must { it.hasInternalModifier } }
                                }
                            }
                        }
                    }
                }

                val violations = projectArchitecture.validate(RealFileSystem(root), ConstraintCheck())

                val unchecked = violations.filterIsInstance<UncheckedConstraint>()
                    .single { it.constraintName == "規約" }
                unchecked.reason shouldBe UncheckedConstraintReason.Failed
                val cause = unchecked.cause.shouldBeInstanceOf<KatachiKonsistScopeIncompleteException>()
                cause.given shouldBe 2
                cause.visible shouldBe 1

                val report = violations.report()
                report shouldContain "[UncheckedConstraint] src"
                // `causeLine` prints only the cause's first line — "this is katachi's own bug"
                // lives in the exception's own second line (see `KatachiKonsistScopeIncompleteException`),
                // so it never reaches the report itself. What the report says instead is the
                // generic, type-driven branch below, which already points a reader at the same
                // conclusion without repeating prose the report format has no room for.
                report shouldContain "Cause: me.tbsten.katachi.konsist.KatachiKonsistScopeIncompleteException: " +
                    "Katachi asked Konsist for 2 files and got 1 back."
                report shouldContain "${STEP}${STEP}- Read the cause above: it says what stopped the constraint"
                report shouldContain "${STEP}${STEP}- Check the constraint block at"
                report shouldContain "${STEP}${STEP}- If the cause is a KatachiInternalException, report it at " +
                    "https://github.com/TBSten/katachi/issues"
            }
        }
    }

    "must を1度も呼ばないブロック" - {
        "reason=Failed になり、KatachiKonsistNoExpectationException が cause になる" {
            val violations = konsistRun("src/PublicThing.kt" to PUBLIC_THING_KT) {
                classes()
            }

            val unchecked = violations.filterIsInstance<UncheckedConstraint>().single()
            unchecked.reason shouldBe UncheckedConstraintReason.Failed
            unchecked.cause.shouldBeInstanceOf<KatachiKonsistNoExpectationException>()

            val report = violations.report()
            report shouldContain "Cause: me.tbsten.katachi.konsist.KatachiKonsistNoExpectationException"
            report shouldContain "never called must, mustNot or mustBeEmpty"
            report shouldContain "${STEP}${STEP}- Read the cause above: it says what stopped the constraint"
        }
    }

    "覆ったファイルに .kt が1つも無い" - {
        "reason=Failed になり、KatachiKonsistNoKotlinFilesException が cause になる" {
            val violations = konsistRun(
                "src/notes.md" to "# notes\n",
                declarations = listOf("notes.md"),
            ) {
                classes().mustBeEmpty()
            }

            val unchecked = violations.filterIsInstance<UncheckedConstraint>().single()
            unchecked.reason shouldBe UncheckedConstraintReason.Failed
            unchecked.cause.shouldBeInstanceOf<KatachiKonsistNoKotlinFilesException>().given shouldBe 1

            val report = violations.report()
            report shouldContain "Cause: me.tbsten.katachi.konsist.KatachiKonsistNoKotlinFilesException"
            report shouldContain "none of them a .kt file Konsist can parse"
        }
    }

    "握ってはいけない例外は konsist ブロックの中からも素通りする" {
        val fatals = listOf<() -> Throwable>(
            { StackOverflowError() },
            { InterruptedException("stop") },
            { AssertionError("this is a result, not a failure") },
            { NoClassDefFoundError("SomeClass") },
        )

        for (fatal in fatals) {
            val thrown = shouldThrow<Throwable> {
                konsistRun("src/PublicThing.kt" to PUBLIC_THING_KT) {
                    classes().must { throw fatal() }
                }
            }
            thrown::class shouldBe fatal()::class
        }
    }

    "1つ目の konsist ブロックが落ちても、2つ目は評価される" {
        fixtureProject(
            "gradlew" to "#!/bin/sh\n",
            "src/PublicThing.kt" to PUBLIC_THING_KT,
        ) { root ->
            @OptIn(ExperimentalKatachiApi::class)
            val projectArchitecture = architecture {
                files = wholeTree()
                "domain".group {
                    "UseCase" {
                        layout {
                            "gradlew".file()
                            "src" {
                                "*.kt".file()
                                "何も期待しない".konsist { classes() }
                                "internal であること".konsist { classes().must { it.hasInternalModifier } }
                            }
                        }
                    }
                }
            }

            val violations = projectArchitecture.validate(RealFileSystem(root), ConstraintCheck())

            violations.filterIsInstance<UncheckedConstraint>().single().constraintName shouldBe "何も期待しない"
            val unsatisfied = violations.filterIsInstance<UnsatisfiedConstraint>().single()
            unsatisfied.constraintName shouldBe "internal であること"
            unsatisfied.path shouldBe "src/PublicThing.kt"
        }
    }

    "役割直下の konsist 制約" - {
        "落ちても1行目がドットにならない" {
            fixtureProject(
                "gradlew" to "#!/bin/sh\n",
                "src/PublicThing.kt" to PUBLIC_THING_KT,
            ) { root ->
                @OptIn(ExperimentalKatachiApi::class)
                val projectArchitecture = architecture {
                    files = wholeTree()
                    "domain".group {
                        "UseCase" {
                            // must を呼ばないので必ず落ちる。役割直下 (anchor = null) に書いて
                            // いるのが本題: path はここでは実在する "src" ディレクトリに解決
                            // されるはずで、"." にはならない (anchorsOf, ConstraintSite.kt)。
                            "規約".konsist { classes() }
                            layout {
                                "gradlew".file()
                                "src" { "*.kt".file() }
                            }
                        }
                    }
                }

                val unchecked = projectArchitecture.validate(RealFileSystem(root), ConstraintCheck())
                    .filterIsInstance<UncheckedConstraint>()
                    .single()

                unchecked.path shouldNotBe "."
                unchecked.path shouldBe "src"
                unchecked.cause.shouldBeInstanceOf<KatachiKonsistNoExpectationException>()
            }
        }
    }
})

/** Matches `check/ViolationReport.kt`'s own indent, so the shouldContain lines above read literally. */
private const val STEP: String = "  "
