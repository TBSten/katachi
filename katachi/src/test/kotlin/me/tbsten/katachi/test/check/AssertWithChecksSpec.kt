package me.tbsten.katachi.test.check

import io.kotest.assertions.throwables.shouldNotThrowAny
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import me.tbsten.katachi.check.KatachiArchitectureAssertionError
import me.tbsten.katachi.check.LayoutCheck
import me.tbsten.katachi.check.assert
import me.tbsten.katachi.check.report
import me.tbsten.katachi.check.validate
import me.tbsten.katachi.dsl.Architecture
import me.tbsten.katachi.dsl.architecture
import me.tbsten.katachi.fs.FileSelection
import me.tbsten.katachi.fs.FsPath
import me.tbsten.katachi.fs.KatachiFileSystem
import me.tbsten.katachi.fs.ProjectRoot
import me.tbsten.katachi.processor.ArchitectureProcessor
import me.tbsten.katachi.processor.ProjectModel
import me.tbsten.katachi.scan.Severity
import me.tbsten.katachi.scan.Violation
import me.tbsten.katachi.scan.ViolationDetail
import me.tbsten.katachi.scan.ViolationKind

/**
 * A violation the way a check outside katachi declares one: the public interface, plus
 * [Violation.details] for the values its block states.
 */
private class TodoViolation(override val path: String) : Violation {
    override val kind: ViolationKind get() = ViolationKind.Constraint
    override val severity: Severity get() = Severity.Error
    override val label: String get() = "TodoRule"
    override val details: List<ViolationDetail>
        get() = listOf(ViolationDetail("Rule", "TODO"), ViolationDetail("Line", "12"))
}

/** A check outside katachi: it reads the model and answers with violations of its own. */
private class TodoCheck(private vararg val paths: String = arrayOf("app/src/Foo.kt")) :
    ArchitectureProcessor<List<Violation>> {
    override fun process(model: ProjectModel): List<Violation> = paths.map { TodoViolation(it) }
}

/** A check that finds nothing, for proving the layout check runs without being asked for. */
private class SilentCheck : ArchitectureProcessor<List<Violation>> {
    override fun process(model: ProjectModel): List<Violation> = emptyList()
}

/** A check that is broken rather than failing: it throws instead of answering. */
private class ThrowingCheck(private val failure: () -> Throwable) :
    ArchitectureProcessor<List<Violation>> {
    override fun process(model: ProjectModel): List<Violation> = throw failure()
}

/**
 * A [FileSelection] whose walk sees no file at all.
 *
 * It is what lets a spec exercise the overloads that reach the **real** file system without
 * the repository's own contents deciding the answer: the project root is still found the real
 * way, and the layout check then has nothing to report, so whatever turns up in the report
 * came from a check that was passed in.
 */
private object SelectsNothing : FileSelection {
    override fun fileSystemFor(
        delegate: KatachiFileSystem,
        projectRoot: ProjectRoot,
    ): KatachiFileSystem = object : KatachiFileSystem by delegate {
        override fun list(directory: FsPath): List<FsPath> = emptyList()
    }
}

/** Over the real tree, seeing nothing. Paired with the overloads that take no file system. */
private fun emptyRealTree(): Architecture = architecture { files = SelectsNothing }

/** The first line of a report, which is where the counts are. */
private fun List<Violation>.summary(): String = report().lines().first()

class AssertWithChecksSpec : FreeSpec({
    "オーバーロードの解決" - {
        // Compiling at all is most of what these pin: four `assert` and four `validate`
        // overloads sit on the same name, and the older spellings have to keep landing on the
        // versions they have always landed on. The assertions then say which one ran.
        "assert() と assert(10) は検査を走らせない版に当たる" {
            shouldNotThrowAny { emptyRealTree().assert() }
            shouldNotThrowAny { emptyRealTree().assert(10) }
            shouldNotThrowAny { emptyRealTree().assert(maxViolations = 10) }
        }

        "assert(fakeFs) と assert(fakeFs, maxViolations) は偽のツリーを検査する版に当たる" {
            val definition = layoutArchitecture { ".gitignore".file() }
            val tree = repositoryOf { ".gitignore"(); repeat(12) { index -> "note-$index.md"() } }

            shouldThrow<KatachiArchitectureAssertionError> { definition.assert(tree) }
                .message!!.lines().count { it.startsWith("[") } shouldBe 10

            shouldThrow<KatachiArchitectureAssertionError> { definition.assert(tree, maxViolations = 2) }
                .message!!.lines().count { it.startsWith("[") } shouldBe 2
        }

        "assert(MyCheck()) と assert(fakeFs, MyCheck()) は検査を受け取る版に当たる" {
            shouldThrow<KatachiArchitectureAssertionError> { emptyRealTree().assert(TodoCheck()) }
                .message!! shouldContain "[TodoRule] app/src/Foo.kt"

            shouldThrow<KatachiArchitectureAssertionError> {
                layoutArchitecture { ".gitignore".file() }
                    .assert(repositoryOf { ".gitignore"(); "notes.md"() }, TodoCheck())
            }.violations.labels() shouldBe listOf("[UnexpectedFile] notes.md", "[TodoRule] app/src/Foo.kt")
        }

        "validate の4本も同じように解決する" {
            emptyRealTree().validate() shouldBe emptyList()
            emptyRealTree().validate(TodoCheck()).labels() shouldBe listOf("[TodoRule] app/src/Foo.kt")

            val definition = layoutArchitecture { ".gitignore".file() }
            val tree = repositoryOf { ".gitignore"(); "notes.md"() }

            definition.validate(tree).labels() shouldBe listOf("[UnexpectedFile] notes.md")
            definition.validate(tree, TodoCheck()).labels() shouldBe
                listOf("[UnexpectedFile] notes.md", "[TodoRule] app/src/Foo.kt")
        }
    }

    "第三者の違反がレポートに出る" - {
        "[Label] path のあとに details の行が並ぶ" {
            val failure = shouldThrow<KatachiArchitectureAssertionError> {
                emptyRealTree().assert(TodoCheck())
            }

            failure.message!! shouldBe
                """
                Katachi check failed: 1 violation (Constraint: 1)

                [TodoRule] app/src/Foo.kt
                  Rule: TODO
                  Line: 12
                """.trimIndent()
        }

        "配置違反と第三者の違反が1つのレポートに種別順で並ぶ" {
            // `Unexpected` before `Constraint`, whatever order the checks ran in: the report
            // groups by kind because the reader does something different with each group.
            val violations = layoutArchitecture { ".gitignore".file() }
                .validate(repositoryOf { ".gitignore"(); "notes.md"() }, TodoCheck("app/src/Foo.kt"))

            violations.labels() shouldBe listOf("[UnexpectedFile] notes.md", "[TodoRule] app/src/Foo.kt")
            violations.summary() shouldBe "Katachi check failed: 2 violations (Unexpected: 1, Constraint: 1)"
        }
    }

    "LayoutCheck は引数に出てこない" - {
        "何も見つけない検査を渡しても配置違反は出る" {
            layoutArchitecture { ".gitignore".file() }
                .validate(repositoryOf { ".gitignore"(); "notes.md"() }, SilentCheck())
                .labels() shouldBe listOf("[UnexpectedFile] notes.md")
        }

        "LayoutCheck を明示的に渡しても違反は倍にならない" {
            val definition = layoutArchitecture { ".gitignore".file() }
            val tree = { repositoryOf { ".gitignore"(); "notes.md"() } }

            definition.validate(tree(), LayoutCheck()).labels() shouldBe
                definition.validate(tree()).labels()
            definition.validate(tree(), LayoutCheck(), TodoCheck()).labels() shouldBe
                listOf("[UnexpectedFile] notes.md", "[TodoRule] app/src/Foo.kt")
        }

        "LayoutCheck を明示的に渡してもサマリ行の件数が倍にならない" {
            val definition = layoutArchitecture { ".gitignore".file() }

            shouldThrow<KatachiArchitectureAssertionError> {
                definition.assert(repositoryOf { ".gitignore"(); "notes.md"() }, LayoutCheck())
            }.message!!.lines().first() shouldBe "Katachi check failed: 1 violation (Unexpected: 1)"

            // The real-tree spelling resolves to the same version, and finds nothing here.
            shouldNotThrowAny { emptyRealTree().assert(LayoutCheck()) }
        }
    }

    "投げた検査は UncheckedCheck になる" - {
        "隣の検査も配置違反も残り、末尾に件数の行が出る" {
            val violations = layoutArchitecture { ".gitignore".file() }.validate(
                repositoryOf { ".gitignore"(); "notes.md"() },
                ThrowingCheck { IllegalStateException("boom") },
                TodoCheck(),
            )

            violations.labels() shouldBe listOf(
                "[UnexpectedFile] notes.md",
                "[TodoRule] app/src/Foo.kt",
                "[UncheckedCheck] .",
            )
            violations.report().lines().last() shouldBe "1 check could not be run."
        }

        "ブロックが投げた検査のクラス名と原因を名指しする" {
            val violations = layoutArchitecture { ".gitignore".file() }.validate(
                repositoryOf { ".gitignore"() },
                ThrowingCheck { IllegalStateException("boom") },
            )

            violations.report() shouldContain
                "Katachi failed while running me.tbsten.katachi.test.check.ThrowingCheck, " +
                "so nothing it would have reported is known."
            violations.report() shouldContain "Cause: java.lang.IllegalStateException: boom"
        }

        "握ってはいけない4種は素通りする" {
            val definition = layoutArchitecture { ".gitignore".file() }
            val fatals = listOf<() -> Throwable>(
                { StackOverflowError() },
                { InterruptedException("stop") },
                { AssertionError("this is a result, not a failure") },
                { NoClassDefFoundError("SomeClass") },
            )

            for (fatal in fatals) {
                val thrown = shouldThrow<Throwable> {
                    definition.validate(repositoryOf { ".gitignore"() }, ThrowingCheck(fatal))
                }
                thrown::class shouldBe fatal()::class
            }
        }
    }
})
