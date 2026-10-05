package me.tbsten.katachi.test.check

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import me.tbsten.katachi.check.DEFAULT_MAX_VIOLATIONS
import me.tbsten.katachi.check.FileConstraintCheck
import me.tbsten.katachi.check.KatachiArchitectureAssertionError
import me.tbsten.katachi.check.Severity
import me.tbsten.katachi.check.UncheckedFile
import me.tbsten.katachi.check.UnexpectedFile
import me.tbsten.katachi.check.Violation
import me.tbsten.katachi.check.ViolationKind
import me.tbsten.katachi.check.internal.BaselineEnvironment
import me.tbsten.katachi.check.internal.assertNoErrors
import me.tbsten.katachi.check.internal.assertWith
import me.tbsten.katachi.dsl.Architecture
import me.tbsten.katachi.processor.ArchitectureProcessNoArgContext
import me.tbsten.katachi.processor.ArchitectureProcessorNoArg
import me.tbsten.katachi.test.dsl.files.FakeFileSystem

private const val PROPERTY = "katachi.maxViolations"

private const val HINT = "  To show every block, run with -Dkatachi.maxViolations=all, or set maxViolations in assert()."

/** `.gitignore` is the only allowed file, so every `note-N.md` is one `[UnexpectedFile]`. */
private val definition: Architecture = layoutArchitecture { ".gitignore".file() }

private fun treeWithNotes(count: Int = 12): FakeFileSystem = repositoryOf {
    ".gitignore"()
    repeat(count) { index -> "note-$index.md"() }
}

/** An environment whose only system property is `-Dkatachi.maxViolations=[value]`, when given. */
private class Environment(value: String?) {
    val printed: MutableList<String> = mutableListOf()
    val environment: BaselineEnvironment = BaselineEnvironment(
        systemProperty = { name -> value.takeIf { name == PROPERTY } },
        standardError = { printed += it },
    )
}

private fun Architecture.failure(
    tree: FakeFileSystem,
    environment: Environment,
    maxViolations: Int? = null,
    vararg checks: ArchitectureProcessorNoArg<List<Violation>>,
): KatachiArchitectureAssertionError = shouldThrow<KatachiArchitectureAssertionError> {
    assertWith(tree, checks.toList(), maxViolations, environment.environment)
}

private fun String.blockCount(): Int = lines().count { it.startsWith("[") }

private fun String.lineAfter(prefix: String): String? {
    val lines = lines()
    val index = lines.indexOfFirst { it.startsWith(prefix) }
    return if (index < 0) null else lines.getOrNull(index + 1)
}

private class BudgetWarning(override val path: String) : Violation {
    override val label: String get() = "BudgetWarning"
    override val kind: ViolationKind get() = ViolationKind.Ambiguous
    override val severity: Severity get() = Severity.Warning
}

/** A check outside katachi that reports [count] warnings and nothing else. */
private class WarningsCheck(private val count: Int) : ArchitectureProcessorNoArg<List<Violation>> {
    override fun process(context: ArchitectureProcessNoArgContext): Result<List<Violation>> =
        runCatching { List(count) { BudgetWarning("docs/w$it.md") } }
}

/**
 * Where the budget of a report comes from: the test's own `maxViolations`, else
 * `-Dkatachi.maxViolations`, else [DEFAULT_MAX_VIOLATIONS] -- and what is said on standard
 * error when a `-D` is ignored.
 */
class MaxViolationsSpec : FreeSpec({
    "コードで指定した maxViolations は -Dkatachi.maxViolations より優先される" {
        val env = Environment("all")

        val message = definition.failure(treeWithNotes(), env, maxViolations = 3).message!!

        message.blockCount() shouldBe 3
        message shouldContain "Showing first 3 (9 more)"
    }

    "コードの値が -Dkatachi.maxViolations に勝ったときは標準エラーにその旨が 1 行出る" {
        val env = Environment("all")

        definition.failure(treeWithNotes(), env, maxViolations = 3)

        env.printed shouldBe listOf(
            "Warning: -Dkatachi.maxViolations=all was ignored because assert() sets maxViolations = 3, " +
                "which wins. Drop maxViolations from the test to set the budget from the command line.",
        )
    }

    "コードで指定が無ければ -Dkatachi.maxViolations の数値の件数まで出る" {
        val env = Environment("5")

        val message = definition.failure(treeWithNotes(), env).message!!

        message.blockCount() shouldBe 5
        message shouldContain "Showing first 5 (7 more)"
        env.printed.shouldBeEmpty()
    }

    "-Dkatachi.maxViolations=all で打ち切り行が出ず全件のブロックが出る" {
        val env = Environment("all")

        val message = definition.failure(treeWithNotes(), env).message!!

        message.blockCount() shouldBe 12
        message shouldNotContain "Showing first"
        message shouldNotContain "To show every block"
        env.printed.shouldBeEmpty()
    }

    "大文字小文字が違う ALL も all として読む" {
        val env = Environment(" ALL ")

        definition.failure(treeWithNotes(), env).message!!.blockCount() shouldBe 12
        env.printed.shouldBeEmpty()
    }

    "数値でも all でもない値は標準エラーに警告を出して既定の 10 件に戻る" {
        val env = Environment("10件")

        val message = definition.failure(treeWithNotes(), env).message!!

        message.blockCount() shouldBe DEFAULT_MAX_VIOLATIONS
        env.printed shouldBe listOf(
            "Warning: -Dkatachi.maxViolations=10件 is neither \"all\" nor a whole number of 0 or more, " +
                "so it was ignored and the default of 10 was used. " +
                "Give -Dkatachi.maxViolations=all to show every block.",
        )
    }

    "負の数は警告を出して既定の 10 件に戻る" {
        val env = Environment("-1")

        definition.failure(treeWithNotes(), env).message!!.blockCount() shouldBe DEFAULT_MAX_VIOLATIONS
        env.printed.size shouldBe 1
        env.printed.single() shouldContain "-Dkatachi.maxViolations=-1 is neither"
    }

    "0 は 1 ブロックも出さず打ち切り行だけになる" {
        val env = Environment("0")

        val message = definition.failure(treeWithNotes(), env).message!!

        message.blockCount() shouldBe 0
        message shouldContain "Showing first 0 (12 more)"
        env.printed.shouldBeEmpty()
    }

    "どちらも無ければ既定の 10 件で打ち切る" {
        val env = Environment(null)

        val message = definition.failure(treeWithNotes(), env).message!!

        message.blockCount() shouldBe DEFAULT_MAX_VIOLATIONS
        message shouldContain "Showing first 10 (2 more)"
        env.printed.shouldBeEmpty()
    }

    "警告だけの実行でも -Dkatachi.maxViolations が標準エラーの Warning 節に効く" {
        val env = Environment("all")

        val returned = definition.assertWith(
            repositoryOf { ".gitignore"() },
            listOf(WarningsCheck(12)),
            maxViolations = null,
            environment = env.environment,
        )

        returned.size shouldBe 12
        val printed = env.printed.joinToString("\n")
        printed.blockCount() shouldBe 12
        printed shouldNotContain "Showing first"
    }

    "全件出力でも部分性の行は最後のブロックの後ろに残る" {
        val env = Environment("all")
        val violations = (1..12).map { UnexpectedFile("note-$it.md", nearby = emptyList()) } +
            listOf(UncheckedFile(path = "src/A.kt", cause = IllegalStateException("boom")))

        val message = shouldThrow<KatachiArchitectureAssertionError> {
            violations.assertNoErrors(env.environment.systemProperty, env.environment.standardError)
        }.message!!

        val lines = message.lines()
        lines.count { it.startsWith("[") } shouldBe 13
        lines.last() shouldBe "1 file could not be checked."
        message shouldNotContain "Showing first"
    }

    "不正な値で assert(FileConstraintCheck()) を 1 回走らせても警告は標準エラーに 1 行だけ出る" {
        val env = Environment("abc")

        // `LayoutCheck` and `FileConstraintCheck` both throw a `KatachiArchitectureAssertionError`
        // of their own inside the run. Neither may read the property: the warning counted on
        // the injected stream has to be one, and the real stream has to stay empty.
        val leaked = capturingStandardError {
            definition.failure(treeWithNotes(), env, null, FileConstraintCheck())
        }

        env.printed.size shouldBe 1
        env.printed.single() shouldContain "-Dkatachi.maxViolations=abc is neither"
        leaked shouldBe ""
    }

    "第三者の check が投げる assertNoErrors にも -Dkatachi.maxViolations が効く" {
        val violations = (1..12).map { UnexpectedFile("note-$it.md", nearby = emptyList()) }
        val printed = mutableListOf<String>()

        val withAll = shouldThrow<KatachiArchitectureAssertionError> {
            violations.assertNoErrors(systemProperty = { "all" }, standardError = { printed += it })
        }.message!!
        val withDefault = shouldThrow<KatachiArchitectureAssertionError> {
            violations.assertNoErrors(systemProperty = { null }, standardError = { printed += it })
        }.message!!

        withAll.blockCount() shouldBe 12
        withAll shouldNotContain "Showing first"
        withDefault.blockCount() shouldBe DEFAULT_MAX_VIOLATIONS
        withDefault shouldContain "Showing first 10 (2 more)"
        printed.shouldBeEmpty()
    }

    "打ち切り行の直後に全件を出す方法の行が出る" {
        val env = Environment(null)

        val message = definition.failure(treeWithNotes(), env).message!!

        message.lineAfter("Showing first 10 (2 more)") shouldBe HINT
    }
})
