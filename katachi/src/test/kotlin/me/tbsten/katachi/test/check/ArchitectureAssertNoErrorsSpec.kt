package me.tbsten.katachi.test.check

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.shouldBe
import me.tbsten.katachi.check.KatachiArchitectureAssertionError
import me.tbsten.katachi.check.Severity
import me.tbsten.katachi.check.Violation
import me.tbsten.katachi.check.ViolationKind
import me.tbsten.katachi.check.assertNoErrors
import me.tbsten.katachi.check.internal.assert
import me.tbsten.katachi.check.internal.assertNoErrors
import me.tbsten.katachi.check.internal.validate
import me.tbsten.katachi.dsl.FileSelection
import me.tbsten.katachi.dsl.architecture
import me.tbsten.katachi.dsl.files.FsPath
import me.tbsten.katachi.dsl.files.KatachiFileSystem
import me.tbsten.katachi.dsl.files.ProjectRoot
import me.tbsten.katachi.processor.ArchitectureProcessContext
import me.tbsten.katachi.processor.ArchitectureProcessorNoArg

private class NoteWarning(override val path: String) : Violation {
    override val label: String get() = "NoteWarning"
    override val kind: ViolationKind get() = ViolationKind.Ambiguous
    override val severity: Severity get() = Severity.Warning
}

/** A check that reports one warning and counts how many times it was run. */
private class CountingWarningCheck(private val path: String = "docs/a.md") :
    ArchitectureProcessorNoArg<List<Violation>> {
    var runs: Int = 0
        private set

    override fun process(context: ArchitectureProcessContext<Unit>): Result<List<Violation>> {
        runs++
        return runCatching { listOf<Violation>(NoteWarning(path)).assertNoErrors() }
    }
}

/** Counts every directory listing the walk asks for, so a spec can tell one walk from two. */
private class CountingFileSystem(private val delegate: KatachiFileSystem) : KatachiFileSystem by delegate {
    var listings: Int = 0
        private set

    override fun list(directory: FsPath): List<FsPath> {
        listings++
        return delegate.list(directory)
    }
}

/**
 * `Architecture.assertNoErrors()`: `assert()` and `validate()` in one run. It fails exactly
 * where `assert()` fails, and otherwise hands back what `validate()` would have, warnings
 * included, without walking the project a second time.
 */
class ArchitectureAssertNoErrorsSpec : FreeSpec({
    "Error があるとき" - {
        "assert() と同じ失敗を投げる（宣言に無いファイルは違反のまま）" {
            val definition = layoutArchitecture { ".gitignore".file() }
            val tree = { repositoryOf { ".gitignore"(); "notes.md"() } }

            val viaAssert = shouldThrow<KatachiArchitectureAssertionError> {
                definition.assert(tree(), CountingWarningCheck())
            }
            val viaAssertNoErrors = shouldThrow<KatachiArchitectureAssertionError> {
                definition.assertNoErrors(tree(), CountingWarningCheck())
            }

            viaAssertNoErrors.message shouldBe viaAssert.message
            viaAssertNoErrors.violations.labels() shouldBe
                listOf("[UnexpectedFile] notes.md", "[NoteWarning] docs/a.md")
        }

        "検査を渡さない版でも配置違反で投げる" {
            shouldThrow<KatachiArchitectureAssertionError> {
                layoutArchitecture { ".gitignore".file() }
                    .assertNoErrors(repositoryOf { ".gitignore"(); "notes.md"() })
            }.violations.labels() shouldBe listOf("[UnexpectedFile] notes.md")
        }
    }

    "Error が無いとき" - {
        "Warning も含めて validate() と同じ違反を同じ順で返す" {
            val definition = layoutArchitecture { ".gitignore".file() }

            val returned = definition.assertNoErrors(repositoryOf { ".gitignore"() }, CountingWarningCheck())
            val validated = definition.validate(repositoryOf { ".gitignore"() }, CountingWarningCheck())

            returned.labels() shouldBe validated.labels()
            returned.labels() shouldBe listOf("[NoteWarning] docs/a.md")
        }

        "標準エラーには assert() と同じ文面が出る" {
            val definition = layoutArchitecture { ".gitignore".file() }

            val viaAssert = capturingStandardError {
                definition.assert(repositoryOf { ".gitignore"() }, CountingWarningCheck())
            }
            val viaAssertNoErrors = capturingStandardError {
                definition.assertNoErrors(repositoryOf { ".gitignore"() }, CountingWarningCheck())
            }

            viaAssertNoErrors shouldBe viaAssert
        }

        "違反が1件も無ければ空のリストを返す" {
            layoutArchitecture { ".gitignore".file() }
                .assertNoErrors(repositoryOf { ".gitignore"() }) shouldBe emptyList()
        }
    }

    "1回の呼び出しで走査も検査も1回だけ" - {
        "渡した検査は1回だけ走る" {
            val check = CountingWarningCheck()

            layoutArchitecture { ".gitignore".file() }.assertNoErrors(repositoryOf { ".gitignore"() }, check)

            check.runs shouldBe 1
        }

        "ディレクトリの一覧は validate() 1回分しか読まない" {
            val definition = layoutArchitecture { ".gitignore".file() }
            val forValidate = CountingFileSystem(repositoryOf { ".gitignore"(); "docs" { "a.md"() } })
            val forAssertNoErrors = CountingFileSystem(repositoryOf { ".gitignore"(); "docs" { "a.md"() } })

            runCatching { definition.validate(forValidate, CountingWarningCheck()) }
            runCatching { definition.assertNoErrors(forAssertNoErrors, CountingWarningCheck()) }

            forAssertNoErrors.listings shouldBe forValidate.listings
        }
    }

    "実際のファイルシステムを読む公開の版" - {
        "検査を渡す版は Warning を返す" {
            val check = CountingWarningCheck()

            architecture { files = SelectsNothingForAssertNoErrors }.assertNoErrors(check).labels() shouldBe
                listOf("[NoteWarning] docs/a.md")
            check.runs shouldBe 1
        }

        "検査を渡さない版は何も見えなければ空を返す" {
            architecture { files = SelectsNothingForAssertNoErrors }.assertNoErrors() shouldBe emptyList()
        }
    }
})

/** Over the real tree, seeing nothing, so the repository's own contents cannot decide the answer. */
private object SelectsNothingForAssertNoErrors : FileSelection {
    override fun fileSystemFor(
        delegate: KatachiFileSystem,
        projectRoot: ProjectRoot,
    ): KatachiFileSystem = object : KatachiFileSystem by delegate {
        override fun list(directory: FsPath): List<FsPath> = emptyList()
    }
}
