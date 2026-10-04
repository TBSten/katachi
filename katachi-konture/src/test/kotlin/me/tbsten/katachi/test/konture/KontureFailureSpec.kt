@file:OptIn(InternalKatachiApi::class, ExperimentalKatachiApi::class)

package me.tbsten.katachi.test.konture

import io.github.baole.konture.Konture
import io.github.baole.konture.core.model.SourceLocation
import io.github.baole.konture.core.model.Subject
import io.github.baole.konture.core.model.Violation
import io.github.baole.konture.scope
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.types.shouldBeInstanceOf
import java.io.File
import me.tbsten.katachi.ExperimentalKatachiApi
import me.tbsten.katachi.InternalKatachiApi
import me.tbsten.katachi.check.FileConstraintCheck
import me.tbsten.katachi.check.UncheckedFileConstraint
import me.tbsten.katachi.check.UncheckedFileConstraintReason
import me.tbsten.katachi.check.UnsatisfiedFileConstraint
import me.tbsten.katachi.check.internal.report
import me.tbsten.katachi.check.internal.validate
import me.tbsten.katachi.dsl.FileConstraint
import me.tbsten.katachi.dsl.architecture
import me.tbsten.katachi.dsl.files.internal.RealFileSystem
import me.tbsten.katachi.dsl.wholeTree
import me.tbsten.katachi.konture.KatachiKontureDefaultGraphException
import me.tbsten.katachi.konture.KatachiKontureDirectAssertionException
import me.tbsten.katachi.konture.KatachiKontureGraphIncompleteException
import me.tbsten.katachi.konture.KatachiKontureNoExpectationException
import me.tbsten.katachi.konture.KatachiKontureNoKotlinFilesException
import me.tbsten.katachi.konture.KatachiKontureParseException
import me.tbsten.katachi.konture.KatachiKontureRuleRefusedException
import me.tbsten.katachi.konture.KatachiKontureUnlocatableViolationException
import me.tbsten.katachi.konture.internal.KontureCoveredFiles
import me.tbsten.katachi.konture.internal.SuiteKind
import me.tbsten.katachi.konture.internal.siteOf
import me.tbsten.katachi.konture.konture

/**
 * What every way a `konture { }` constraint can fail to answer at all comes out as, seen through
 * the real report — the counterpart of `:katachi`'s `FileConstraintFailureSpec`, one module over.
 *
 * Eight exceptions end at `[UncheckedFileConstraint] reason=Failed`, each pinned once here or in
 * [KontureShadowSpec]:
 * 1. graph construction lost a file ([KatachiKontureGraphIncompleteException])
 * 2. the block declared no rule ([KatachiKontureNoExpectationException])
 * 3. the covered files hold no `.kt` Konture can parse ([KatachiKontureNoKotlinFilesException])
 * 4. a rule has no `should()` ([KatachiKontureRuleRefusedException]) — and only that; an
 *    `AssertionError` a predicate threw while the rule ran is (5), not this
 * 5. an `AssertionError` came out of the block itself, or out of a predicate while a rule was
 *    being evaluated ([KatachiKontureDirectAssertionException], told apart by `suite`)
 * 6. the block reached Konture's default graph ([KatachiKontureDefaultGraphException]) — the
 *    shadowed spelling is pinned by [KontureShadowSpec], the unshadowed one here; a `require`
 *    of the reader's own is not dressed up as this
 * 7. a violation pointed outside the covered files ([KatachiKontureUnlocatableViolationException])
 * 8. Konture's parser threw on a file that exists ([KatachiKontureParseException])
 *
 * **Why (1) is triggered by deleting a file rather than by a filesystem quirk.** This backend
 * asks Konture to parse the same absolute paths katachi's own walk already found. If the files
 * on disk never change between that walk and Konture's read, the two counts always agree —
 * `KontureAssumptionsSpec` pins what Konture returns, and nothing there produces a mismatch on
 * its own. The only way to provoke one without editing production code is to change the disk
 * between the two reads, which one constraint's side effect can do to a second constraint's
 * parse in the same run.
 */
class KontureFailureSpec : FreeSpec({
    "graph の生成が落ちる" - {
        "reason=Failed になり、KatachiKontureGraphIncompleteException が cause になる" {
            fixtureProject(
                "gradlew" to "#!/bin/sh\n",
                "src/A.kt" to PUBLIC_THING_KT,
                "src/B.kt" to INTERNAL_THING_KT,
            ) { root ->
                val projectArchitecture = architecture {
                    files = wholeTree()
                    "domain".group {
                        "UseCase".role {
                            layout {
                                "gradlew".file()
                                "src" {
                                    "*.kt".file()
                                    // Declared before the konture { } constraint below so that it
                                    // runs first (FileConstraintFailureSpec pins declaration order as
                                    // evaluation order). It deletes B.kt, which katachi's own walk
                                    // already counted into `subject.files` for both constraints —
                                    // Konture, reading the files afterwards, sees one fewer than
                                    // katachi asked for.
                                    fileConstraint(
                                        "B.kt を消す",
                                        check = FileConstraint { subject ->
                                            File("${subject.projectRoot}/src/B.kt").delete()
                                            emptyList()
                                        },
                                    )
                                    "規約".konture { classes { should().beInternal() } }
                                }
                            }
                        }
                    }
                }

                val violations = projectArchitecture.validate(RealFileSystem(root), FileConstraintCheck())

                val unchecked = violations.filterIsInstance<UncheckedFileConstraint>()
                    .single { it.constraintName == "規約" }
                unchecked.reason shouldBe UncheckedFileConstraintReason.Failed
                val cause = unchecked.cause.shouldBeInstanceOf<KatachiKontureGraphIncompleteException>()
                cause.given shouldBe 2
                cause.visible shouldBe 1

                val report = violations.report()
                report shouldContain "[UncheckedFileConstraint] src"
                report shouldContain "Cause: me.tbsten.katachi.konture.KatachiKontureGraphIncompleteException: " +
                    "Katachi asked Konture for 2 files and got 1 back."
                report shouldContain "${STEP}${STEP}- Read the cause above: it says what stopped the constraint"
                report shouldContain "${STEP}${STEP}- If the cause is a KatachiInternalException, report it at " +
                    "https://github.com/TBSten/katachi/issues"
            }
        }
    }

    "規則を 1 つも書かないブロック" - {
        "reason=Failed になり、KatachiKontureNoExpectationException が cause になる" {
            val violations = kontureRun("src/PublicThing.kt" to PUBLIC_THING_KT) {
            }

            val unchecked = violations.filterIsInstance<UncheckedFileConstraint>().single()
            unchecked.reason shouldBe UncheckedFileConstraintReason.Failed
            unchecked.cause.shouldBeInstanceOf<KatachiKontureNoExpectationException>()

            val report = violations.report()
            report shouldContain "Cause: me.tbsten.katachi.konture.KatachiKontureNoExpectationException"
            report shouldContain "declares no rule"
            report shouldContain "${STEP}${STEP}- Read the cause above: it says what stopped the constraint"
        }
    }

    "覆ったファイルに .kt が1つも無い" - {
        "reason=Failed になり、KatachiKontureNoKotlinFilesException が cause になる" {
            val violations = kontureRun(
                "src/notes.md" to "# notes\n",
                declarations = listOf("notes.md"),
            ) {
                classes { should().beInternal() }
            }

            val unchecked = violations.filterIsInstance<UncheckedFileConstraint>().single()
            unchecked.reason shouldBe UncheckedFileConstraintReason.Failed
            unchecked.cause.shouldBeInstanceOf<KatachiKontureNoKotlinFilesException>().given shouldBe 1

            val report = violations.report()
            report shouldContain "Cause: me.tbsten.katachi.konture.KatachiKontureNoKotlinFilesException"
            report shouldContain "none of them a .kt file Konture can parse"
        }
    }

    "should() の無い規則" - {
        "reason=Failed になり、KatachiKontureRuleRefusedException が suite の名前を持つ" {
            val violations = kontureRun("src/PublicThing.kt" to PUBLIC_THING_KT) {
                files { that() nameEndsWith ".kt" }
            }

            val unchecked = violations.filterIsInstance<UncheckedFileConstraint>().single()
            unchecked.reason shouldBe UncheckedFileConstraintReason.Failed
            val cause = unchecked.cause.shouldBeInstanceOf<KatachiKontureRuleRefusedException>()
            cause.suite shouldBe "files"
            cause.role shouldBe "domain.UseCase"
            cause.constraintName shouldBe "規約"
            cause.message.orEmpty() shouldContain "Konture refused the files rule"
            cause.message.orEmpty() shouldContain "should()"
            cause.cause.shouldBeInstanceOf<AssertionError>()
        }

        "should() の無い規則が 2 つ目でも、1 つ目の違反は捨てて制約ごと落とす" {
            // A half-answered constraint would read as "these are all the violations", which it
            // is not. Refusing the whole constraint is what keeps the report honest.
            val violations = kontureRun("src/PublicThing.kt" to PUBLIC_THING_KT) {
                classes { should().beInternal() }
                classes { }
            }

            violations.filterIsInstance<UnsatisfiedFileConstraint>().size shouldBe 0
            violations.filterIsInstance<UncheckedFileConstraint>().single()
                .cause.shouldBeInstanceOf<KatachiKontureRuleRefusedException>().suite shouldBe "classes"
        }
    }

    "ブロックの中から AssertionError が出る" - {
        "手で呼んだ check() は KatachiKontureDirectAssertionException になる" {
            // Under audit a hand-written `check()` on a complete rule returns a report that is
            // dropped; on an incomplete one Konture still asserts, and that assertion is the
            // reader's own doing rather than a rule's answer.
            val violations = kontureRun("src/PublicThing.kt" to PUBLIC_THING_KT) {
                classes { check() }
            }

            val cause = violations.filterIsInstance<UncheckedFileConstraint>().single()
                .cause.shouldBeInstanceOf<KatachiKontureDirectAssertionException>()
            cause.spelling shouldBe "AssertionError"
            cause.suite shouldBe null
            cause.message.orEmpty() shouldContain "A konture { } block called Konture's own AssertionError"
            cause.message.orEmpty() shouldContain "with that() ... should() instead"
        }

        "規則の述語が投げた AssertionError は should() 無しと区別され、suite の名前を持つ" {
            // Konture runs `satisfy { }` on each declaration with nothing catching what it throws,
            // so a predicate that asserts comes out of `check()` as an AssertionError too. It must
            // not be reported as "end the rule with should()": the rule has one.
            val violations = kontureRun("src/PublicThing.kt" to PUBLIC_THING_KT) {
                classes { should() satisfy { throw AssertionError("asserted in a predicate") } }
            }

            val unchecked = violations.filterIsInstance<UncheckedFileConstraint>().single()
            unchecked.reason shouldBe UncheckedFileConstraintReason.Failed
            val cause = unchecked.cause.shouldBeInstanceOf<KatachiKontureDirectAssertionException>()
            cause.suite shouldBe "classes"
            cause.spelling shouldBe "AssertionError"
            cause.message.orEmpty() shouldContain
                "came out of the classes rule of a konture { } block while Konture was evaluating it: " +
                "java.lang.AssertionError: asserted in a predicate"
            cause.message.orEmpty() shouldContain "Return a Boolean from the predicate"
            cause.message.orEmpty().contains("should()") shouldBe false
        }
    }

    "shadow されていない綴りで既定の graph に触る" - {
        "Konture.scope は KatachiKontureDefaultGraphException になり、layout_v2.json の話が cause に残る" {
            val violations = kontureRun("src/PublicThing.kt" to PUBLIC_THING_KT) {
                Konture.scope
            }

            val cause = violations.filterIsInstance<UncheckedFileConstraint>().single()
                .cause.shouldBeInstanceOf<KatachiKontureDefaultGraphException>()
            cause.spelling shouldBe "IllegalArgumentException"
            cause.message.orEmpty() shouldContain "reached Konture's default project graph through IllegalArgumentException"
            cause.message.orEmpty() shouldContain "layout_v2.json"
            cause.message.orEmpty() shouldContain "module rules need Konture's own Gradle plugin"
        }

        "利用者自身の require / error は既定の graph の話にされず、そのまま cause になる" {
            // The same two exception types, thrown by the reader rather than by Konture reaching
            // for its default graph. Dressing them up as KatachiKontureDefaultGraphException would
            // tell the reader about layout_v2.json for a message of their own.
            val fromRequire = kontureRun("src/PublicThing.kt" to PUBLIC_THING_KT) {
                classes { require(false) { "mine" } }
            }.filterIsInstance<UncheckedFileConstraint>().single().cause.shouldBeInstanceOf<Throwable>()
            fromRequire::class shouldBe IllegalArgumentException::class
            fromRequire.message shouldBe "mine"

            val fromError = kontureRun("src/PublicThing.kt" to PUBLIC_THING_KT) {
                error("also mine")
            }.filterIsInstance<UncheckedFileConstraint>().single().cause.shouldBeInstanceOf<Throwable>()
            fromError::class shouldBe IllegalStateException::class
            fromError.message shouldBe "also mine"
        }
    }

    "覆っていないファイルを指す Violation" - {
        "KatachiKontureUnlocatableViolationException になる" {
            // Konture never produces one of these from a graph katachi built -- the graph holds
            // the covered files and nothing else -- so the mapping is exercised directly.
            val violation = Violation(
                ruleId = "classes.rule",
                subject = Subject.ClassSubject(
                    fqName = "com.example.Stray",
                    simpleName = "Stray",
                    location = SourceLocation("/repo/src/Stray.kt", line = 3),
                ),
                message = "stray",
            )

            val outside = shouldThrow<KatachiKontureUnlocatableViolationException> {
                siteOf(SuiteKind.Classes, violation, KontureCoveredFiles("/repo", setOf("src/Covered.kt")))
            }
            outside.ruleId shouldBe "classes.rule"
            outside.subject shouldBe "com.example.Stray"
            outside.filePath shouldBe "/repo/src/Stray.kt"
            outside.message.orEmpty() shouldContain "not a file this constraint covers"
            outside.message.orEmpty() shouldContain "https://github.com/TBSten/katachi/issues"

            // A different project root is the same mistake from the other side.
            shouldThrow<KatachiKontureUnlocatableViolationException> {
                siteOf(SuiteKind.Classes, violation, KontureCoveredFiles("/elsewhere", setOf("src/Stray.kt")))
            }

            // No location at all.
            val unplaced = shouldThrow<KatachiKontureUnlocatableViolationException> {
                siteOf(
                    SuiteKind.Classes,
                    violation.copy(subject = Subject.CustomSubject("nowhere", location = null), sourceLocation = null),
                    KontureCoveredFiles("/repo", setOf("src/Stray.kt")),
                )
            }
            unplaced.filePath shouldBe null
        }

        "覆ったファイルの別の綴りは、覆った綴りに戻して報告される" {
            // Konture reports the spelling its parse cache remembers, which can differ from the one
            // katachi asked for (`src/../src/`, `/tmp` against `/private/tmp`). Same file, same block.
            val violation = Violation(
                ruleId = "classes.rule",
                subject = Subject.ClassSubject(
                    fqName = "com.example.Covered",
                    simpleName = "Covered",
                    location = SourceLocation("/repo/src/../src/Covered.kt", line = 3),
                ),
                message = "spelled differently",
            )

            val site = siteOf(SuiteKind.Classes, violation, KontureCoveredFiles("/repo", setOf("src/Covered.kt")))

            site.file shouldBe "src/Covered.kt"
            site.declaration shouldBe "Covered"
            site.line shouldBe 3
        }
    }

    "Konture が読めないファイル" - {
        "reason=Failed になり、KatachiKontureParseException が cause になる" {
            fixtureProject("src/PublicThing.kt" to PUBLIC_THING_KT) { root ->
                val violations = withUnreadable(File(root, "src/PublicThing.kt")) {
                    kontureRunIn(root) { classes { should().beInternal() } }
                }

                val cause = violations.filterIsInstance<UncheckedFileConstraint>().single()
                    .cause.shouldBeInstanceOf<KatachiKontureParseException>()
                cause.file shouldBe root.fixturePath("src/PublicThing.kt")
                cause.message.orEmpty() shouldContain "Konture could not parse"
                cause.message.orEmpty() shouldContain "report it to Konture"
            }
        }
    }

    "握ってはいけない例外は規則の述語の中からも素通りする" {
        val fatals = listOf<() -> Throwable>(
            { StackOverflowError() },
            { InterruptedException("stop") },
            { NoClassDefFoundError("SomeClass") },
        )

        for (fatal in fatals) {
            val thrown = shouldThrow<Throwable> {
                kontureRun("src/PublicThing.kt" to PUBLIC_THING_KT) {
                    classes { should() satisfy { throw fatal() } }
                }
            }
            thrown::class shouldBe fatal()::class
        }
    }

    "1つ目の konture ブロックが落ちても、2つ目は評価される" {
        fixtureProject(
            "gradlew" to "#!/bin/sh\n",
            "src/PublicThing.kt" to PUBLIC_THING_KT,
        ) { root ->
            val projectArchitecture = architecture {
                files = wholeTree()
                "domain".group {
                    "UseCase".role {
                        layout {
                            "gradlew".file()
                            "src" {
                                "*.kt".file()
                                "何も期待しない".konture { }
                                "internal であること".konture { classes { should().beInternal() } }
                            }
                        }
                    }
                }
            }

            val violations = projectArchitecture.validate(RealFileSystem(root), FileConstraintCheck())

            violations.filterIsInstance<UncheckedFileConstraint>().single().constraintName shouldBe "何も期待しない"
            val unsatisfied = violations.filterIsInstance<UnsatisfiedFileConstraint>().single()
            unsatisfied.constraintName shouldBe "internal であること"
            unsatisfied.path shouldBe "src/PublicThing.kt"
        }
    }

    "役割直下の konture 制約" - {
        "落ちても1行目がドットにならない" {
            fixtureProject(
                "gradlew" to "#!/bin/sh\n",
                "src/PublicThing.kt" to PUBLIC_THING_KT,
            ) { root ->
                val projectArchitecture = architecture {
                    files = wholeTree()
                    "domain".group {
                        "UseCase".role {
                            // 規則を書かないので必ず落ちる。役割直下 (anchor = null) に書いて
                            // いるのが本題: path はここでは実在する "src" ディレクトリに解決
                            // されるはずで、"." にはならない (anchorsOf, FlattenLayout.kt)。
                            "規約".konture { }
                            layout {
                                "gradlew".file()
                                "src" { "*.kt".file() }
                            }
                        }
                    }
                }

                val unchecked = projectArchitecture.validate(RealFileSystem(root), FileConstraintCheck())
                    .filterIsInstance<UncheckedFileConstraint>()
                    .single()

                unchecked.path shouldNotBe "."
                unchecked.path shouldBe "src"
                unchecked.cause.shouldBeInstanceOf<KatachiKontureNoExpectationException>()
            }
        }
    }
})

/** Matches `check/internal/ViolationReport.kt`'s own indent, so the shouldContain lines above read literally. */
private const val STEP: String = "  "
