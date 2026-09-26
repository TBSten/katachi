package me.tbsten.katachi.test.check

import io.kotest.assertions.throwables.shouldNotThrowAny
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import me.tbsten.katachi.KatachiDeclarationException
import me.tbsten.katachi.check.KatachiArchitectureAssertionError
import me.tbsten.katachi.check.FileConstraintCheck
import me.tbsten.katachi.check.LayoutCheck
import me.tbsten.katachi.check.internal.assert
import me.tbsten.katachi.check.internal.validate
import me.tbsten.katachi.dsl.KatachiModuleOutsideLayoutRootException
import me.tbsten.katachi.dsl.KatachiProjectRootNotFoundException
import me.tbsten.katachi.dsl.gradle.module
import me.tbsten.katachi.processor.internal.process
import me.tbsten.katachi.processor.process
import me.tbsten.katachi.test.dsl.files.fakeFileSystem

class LayoutCheckSpec : FreeSpec({
    "Error の違反は投げずに failure として返し、例外が違反を全件持つ" {
        val definition = layoutArchitecture { "core" { "App.kt".file() } }
        val tree = repositoryOf { "core" { "App.kt"(); "notes.md"() } }

        val result = shouldNotThrowAny { definition.process(LayoutCheck(), tree) }

        val failure = result.exceptionOrNull().shouldBeInstanceOf<KatachiArchitectureAssertionError>()
        failure.violations.labels() shouldBe listOf("[UnexpectedFile] core/notes.md")
    }

    "違反が無ければ空のリストを success で返す" {
        val definition = layoutArchitecture { "core" { "App.kt".file() } }

        definition.process(LayoutCheck(), repositoryOf { "core" { "App.kt"() } }).getOrThrow() shouldBe
            emptyList()
    }

    "validate は同じ違反を同じ順で返す" {
        // What this pins is that `validate()` adds nothing of its own on top of the processor:
        // no reordering, no filtering, no second walk of the tree.
        val definition = layoutArchitecture {
            "core" { "App.kt".file() }
            "docs" / "README.md".file()
        }
        val tree = repositoryOf {
            "core" { "App.kt"(); "notes.md"() }
            "docs" { }
            "tmp" { "scratch.kt"() }
        }

        val throughProcessor = definition.process(LayoutCheck(), tree).found().labels()

        throughProcessor shouldBe listOf(
            "[UnexpectedFile] core/notes.md",
            "[UnexpectedDirectory] tmp",
            "[MissingFile] docs/README.md",
        )
        definition.validate(tree).labels() shouldBe throughProcessor
    }

    "同じモデルから違反と役割のファイルの両方を読める" {
        val definition = layoutArchitecture { "core" { "App.kt".file() } }
        val tree = repositoryOf { "core" { "App.kt"(); "notes.md"() } }

        val read = definition.process(tree) { context ->
            LayoutCheck().process(context).found().labels() to context.filesOf(context.roles.single())
        }

        read shouldBe (listOf("[UnexpectedFile] core/notes.md") to listOf("core/App.kt"))
    }

    "答えられなかったときも投げない" - {
        "プロジェクトルートが見つからなければ failure として返す" {
            val result = shouldNotThrowAny {
                layoutArchitecture { "core" { "App.kt".file() } }.process(LayoutCheck(), treeWithoutRoot())
            }

            result.exceptionOrNull().shouldBeInstanceOf<KatachiProjectRootNotFoundException>()
        }

        "定義の誤りは failure として返す" {
            val result = shouldNotThrowAny {
                moduleOutsideLayoutRoot().process(LayoutCheck(), repositoryOf { })
            }

            result.exceptionOrNull().shouldBeInstanceOf<KatachiModuleOutsideLayoutRootException>()
        }
    }

    "validate と assert は、LayoutCheck が答えられなかった失敗を UncheckedCheck にせず投げる" - {
        "プロジェクトルートが見つからない" {
            val definition = layoutArchitecture { "core" { "App.kt".file() } }

            shouldThrow<KatachiProjectRootNotFoundException> { definition.validate(treeWithoutRoot()) }
            shouldThrow<KatachiProjectRootNotFoundException> { definition.assert(treeWithoutRoot()) }
        }

        "定義の誤り" {
            val definition = moduleOutsideLayoutRoot()

            shouldThrow<KatachiDeclarationException> { definition.validate(repositoryOf { }) }
            shouldThrow<KatachiDeclarationException> { definition.assert(repositoryOf { }) }
        }

        "追加の check を渡しても同じ" {
            val definition = moduleOutsideLayoutRoot()

            shouldThrow<KatachiDeclarationException> { definition.validate(repositoryOf { }, FileConstraintCheck()) }
        }
    }
})

/** A tree with no Gradle, Maven or git marker anywhere above the working directory. */
private fun treeWithoutRoot() = fakeFileSystem(workingDirectory = "/repo/app") {
    "/repo" { "app" { "Main.kt"() } }
}

/** A definition whose `layout { }` is wrong in a way only evaluating it reveals. */
private fun moduleOutsideLayoutRoot() = layoutArchitecture { "app" { ":core:data".module { } } }
