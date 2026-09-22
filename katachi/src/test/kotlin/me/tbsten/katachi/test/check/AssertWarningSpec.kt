package me.tbsten.katachi.test.check

import io.kotest.assertions.throwables.shouldNotThrowAny
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import me.tbsten.katachi.check.KatachiArchitectureAssertionError
import me.tbsten.katachi.check.assert
import me.tbsten.katachi.processor.ArchitectureProcessor
import me.tbsten.katachi.processor.ProjectModel
import me.tbsten.katachi.scan.Severity
import me.tbsten.katachi.scan.Violation
import me.tbsten.katachi.scan.ViolationKind

/**
 * A [Violation] with [Severity.Warning], the way a third-party check would declare one. Stands
 * in for [me.tbsten.katachi.scan.AmbiguousLayout] / `MissingDescription` until step 5-2 / 5-3
 * give katachi its own Warning detectors.
 */
private class TestWarningViolation(override val path: String) : Violation {
    override val label: String get() = "TestWarning"
    override val kind: ViolationKind get() = ViolationKind.Ambiguous
    override val severity: Severity get() = Severity.Warning
}

/** A check outside katachi that always reports one warning at [path]. */
private class WarningCheck(private val path: String = "docs/a.md") : ArchitectureProcessor<List<Violation>> {
    override fun process(model: ProjectModel): List<Violation> = listOf(TestWarningViolation(path))
}

/**
 * `assert()`'s severity-based branch: nothing but warnings goes to standard error and does not
 * throw, an error still throws with the warnings appended to the message, and a run with
 * neither prints nothing.
 */
class AssertWarningSpec : FreeSpec({
    "Warning だけのとき" - {
        "assert() は投げず、report() と同じ文面が標準エラーに出る" {
            val definition = layoutArchitecture { ".gitignore".file() }
            val tree = repositoryOf { ".gitignore"() }

            val printed = capturingStandardError {
                shouldNotThrowAny { definition.assert(tree, WarningCheck()) }
            }

            printed.trim() shouldBe
                """
                Katachi check found 1 warning. Warnings never fail the check.

                [TestWarning] docs/a.md
                """.trimIndent()
        }
    }

    "Error と Warning が両方あるとき" - {
        "assert() は投げ、失敗メッセージの末尾に Warning セクションが来る" {
            val definition = layoutArchitecture { ".gitignore".file() }
            val tree = repositoryOf { ".gitignore"(); "notes.md"() }

            val failure = shouldThrow<KatachiArchitectureAssertionError> {
                definition.assert(tree, WarningCheck())
            }

            failure.message!! shouldContain "[UnexpectedFile] notes.md"
            failure.message!! shouldContain "Katachi check found 1 warning. Warnings never fail the check."
            // The Warning section is the last thing in the message, after the error blocks.
            failure.message!!.trimEnd().lines().last() shouldBe "[TestWarning] docs/a.md"
        }

        "標準エラーには何も出ない（同じ文面は失敗メッセージに入っている）" {
            val definition = layoutArchitecture { ".gitignore".file() }
            val tree = repositoryOf { ".gitignore"(); "notes.md"() }

            val printed = capturingStandardError {
                shouldThrow<KatachiArchitectureAssertionError> { definition.assert(tree, WarningCheck()) }
            }

            printed shouldBe ""
        }
    }

    "Warning も Error も無いとき" - {
        "標準エラーに何も出ず assert() も投げない" {
            val definition = layoutArchitecture { ".gitignore".file() }
            val tree = repositoryOf { ".gitignore"() }

            val printed = capturingStandardError {
                shouldNotThrowAny { definition.assert(tree) }
            }

            printed shouldBe ""
        }
    }
})
