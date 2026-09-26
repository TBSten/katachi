package me.tbsten.katachi.test.check

import io.kotest.assertions.throwables.shouldNotThrowAny
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.types.shouldBeSameInstanceAs
import me.tbsten.katachi.check.KatachiArchitectureAssertionError
import me.tbsten.katachi.check.internal.assert
import me.tbsten.katachi.check.assertNoErrors
import me.tbsten.katachi.check.internal.validate
import me.tbsten.katachi.processor.ArchitectureProcessContext
import me.tbsten.katachi.processor.ArchitectureProcessorNoArg
import me.tbsten.katachi.check.Severity
import me.tbsten.katachi.check.UncheckedCheck
import me.tbsten.katachi.check.Violation
import me.tbsten.katachi.check.ViolationKind

/**
 * How a check says what it found: [assertNoErrors] at the end of its `runCatching`, and
 * `validate()` reading the answer apart on the way in.
 */
class CheckResultSpec : FreeSpec({
    "assertNoErrors" - {
        "Warning だけならそのリストをそのまま返す" {
            val warnings = listOf<Violation>(ResultWarning("docs/a.md"), ResultWarning("docs/b.md"))

            warnings.assertNoErrors() shouldBeSameInstanceAs warnings
        }

        "何も無ければ空のリストを返す" {
            emptyList<Violation>().assertNoErrors() shouldBe emptyList()
        }

        "Error を含めば、Warning も含めた全件を持つ KatachiArchitectureAssertionError を投げる" {
            val found = listOf<Violation>(ResultWarning("docs/a.md"), ResultError("app/Foo.kt"))

            val thrown = shouldThrow<KatachiArchitectureAssertionError> { found.assertNoErrors() }

            thrown.violations shouldBe found
        }

        "投げた例外のメッセージは assert() と同じ報告になる" {
            val thrown = shouldThrow<KatachiArchitectureAssertionError> {
                listOf<Violation>(ResultError("app/Foo.kt")).assertNoErrors()
            }

            thrown.message.orEmpty().lines().first() shouldBe "Katachi check failed: 1 violation (Constraint: 1)"
        }
    }

    "validate は check の答え方を読み分ける" - {
        val definition = layoutArchitecture { ".gitignore".file() }
        val tree = { repositoryOf { ".gitignore"(); "notes.md"() } }

        "runCatching の中で assertNoErrors が投げた check は、その違反が Warning 込みで拾われる" {
            val violations = definition.validate(tree(), AnsweringCheck { FindsErrorAndWarning.assertNoErrors() })

            violations.labels() shouldBe listOf(
                "[UnexpectedFile] notes.md",
                "[ResultError] app/Foo.kt",
                "[ResultWarning] docs/a.md",
            )
        }

        "それ以外の失敗を返した check は UncheckedCheck になり、隣の違反は残る" {
            val cause = IllegalStateException("could not tell")

            val violations = definition.validate(tree(), AnsweringCheck { throw cause })

            violations.labels() shouldBe listOf("[UnexpectedFile] notes.md", "[UncheckedCheck] .")
            violations.filterIsInstance<UncheckedCheck>().single().cause shouldBe cause
        }

        "投げてしまった check も UncheckedCheck になり、隣の違反は残る" {
            val violations = definition.validate(tree(), ThrowsInsteadOfAnswering)

            violations.labels() shouldBe listOf("[UnexpectedFile] notes.md", "[UncheckedCheck] .")
            violations.filterIsInstance<UncheckedCheck>().single().check shouldContain
                "ThrowsInsteadOfAnswering"
        }

        "failure として返された AssertionError は UncheckedCheck にならず、そのまま投げられる" {
            val thrown = shouldThrow<AssertionError> {
                definition.validate(tree(), AnsweringCheck { throw AssertionError("the caller's own answer") })
            }

            thrown.message shouldBe "the caller's own answer"
        }

        "Warning だけを返した check では assert() は投げない" {
            val warningsOnly = AnsweringCheck { listOf<Violation>(ResultWarning("docs/a.md")).assertNoErrors() }

            capturingStandardError {
                shouldNotThrowAny {
                    definition.assert(repositoryOf { ".gitignore"() }, warningsOnly)
                }
            } shouldContain "[ResultWarning] file:///repo/docs/a.md"
        }
    }
})

private val FindsErrorAndWarning: List<Violation> =
    listOf(ResultError("app/Foo.kt"), ResultWarning("docs/a.md"))

private class ResultError(override val path: String) : Violation {
    override val kind: ViolationKind get() = ViolationKind.Constraint
    override val severity: Severity get() = Severity.Error
    override val label: String get() = "ResultError"
}

private class ResultWarning(override val path: String) : Violation {
    override val kind: ViolationKind get() = ViolationKind.Ambiguous
    override val severity: Severity get() = Severity.Warning
    override val label: String get() = "ResultWarning"
}

/** A check outside katachi, written the recommended way: its whole body is [body] in `runCatching`. */
private class AnsweringCheck(private val body: () -> List<Violation>) :
    ArchitectureProcessorNoArg<List<Violation>> {
    override fun process(context: ArchitectureProcessContext<Unit>): Result<List<Violation>> =
        runCatching { body() }
}

/** A check that breaks the contract and throws out of `process` instead of answering. */
private object ThrowsInsteadOfAnswering : ArchitectureProcessorNoArg<List<Violation>> {
    override fun process(context: ArchitectureProcessContext<Unit>): Result<List<Violation>> =
        throw IllegalStateException("broken")
}
