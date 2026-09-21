package me.tbsten.katachi.test.check

import io.kotest.assertions.throwables.shouldNotThrowAny
import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.shouldBe
import me.tbsten.katachi.check.LayoutCheck
import me.tbsten.katachi.check.validate
import me.tbsten.katachi.processor.process

class LayoutCheckSpec : FreeSpec({
    "違反があっても投げずに返す" {
        val definition = layoutArchitecture { "core" { "App.kt".file() } }
        val tree = repositoryOf { "core" { "App.kt"(); "notes.md"() } }

        shouldNotThrowAny {
            definition.process(LayoutCheck(), tree)
                .labels() shouldBe listOf("[UnexpectedFile] core/notes.md")
        }
    }

    "違反が無ければ空のリストを返す" {
        val definition = layoutArchitecture { "core" { "App.kt".file() } }

        definition.process(LayoutCheck(), repositoryOf { "core" { "App.kt"() } })
            .labels() shouldBe emptyList()
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

        val throughProcessor = definition.process(LayoutCheck(), tree).labels()

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

        val read = definition.process(tree) { model ->
            LayoutCheck().process(model).labels() to model.filesOf(model.roles.single())
        }

        read shouldBe (listOf("[UnexpectedFile] core/notes.md") to listOf("core/App.kt"))
    }
})
