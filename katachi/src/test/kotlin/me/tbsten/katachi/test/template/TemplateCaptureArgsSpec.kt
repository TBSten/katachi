package me.tbsten.katachi.test.template

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.shouldBe
import me.tbsten.katachi.dsl.Architecture
import me.tbsten.katachi.dsl.architecture
import me.tbsten.katachi.dsl.gradle.module
import me.tbsten.katachi.processor.ArchitectureProcessor
import me.tbsten.katachi.processor.KatachiUnknownProcessorArgException
import me.tbsten.katachi.processor.internal.FakeArchitectureProcessContext
import me.tbsten.katachi.processor.internal.checkNoUnknownArgs
import me.tbsten.katachi.template.GenerateCodeFromTemplate
import me.tbsten.katachi.test.dsl.files.ForbiddenFileSystem

private fun capturesArchitecture(): Architecture = architecture {
    "feature".group {
        "ViewModel" {
            layout { "feature" / capture("feature") / "*ViewModel.kt".file() }
            template {
                val name by stringParameter()
                file("${name}ViewModel.kt") { "" }
            }
        }
        "Screen" {
            layout { ":feature:*".module(capture = "module") { "*Screen.kt".file() } }
            template {
                val name by stringParameter()
                file("${name}Screen.kt") { "" }
            }
        }
    }
}

/** A context over a tree that refuses to be read, which is what proves no name costs a walk. */
private fun contextOf(values: Map<String, String>) = FakeArchitectureProcessContext(
    architecture = capturesArchitecture(),
    args = Unit,
    fileSystem = ForbiddenFileSystem,
    rawArgs = values,
)

private fun checkTemplateArgs(values: Map<String, String>) {
    val selected: List<Pair<String, ArchitectureProcessor<*, *>>> = listOf("template" to GenerateCodeFromTemplate)
    checkNoUnknownArgs(selected = selected, context = contextOf(values), values = values)
}

/**
 * The capture names of a role are `--arg` names its template run accepts, decided from the
 * declarations alone.
 */
class TemplateCaptureArgsSpec : FreeSpec({
    "undeclaredArgNames は役割の capture の名前も答える" {
        GenerateCodeFromTemplate.undeclaredArgNames(contextOf(mapOf("roleName" to "ViewModel"))) shouldBe
            setOf("name", "feature")
    }

    "モジュールの capture の名前も答える（ディスクを読まない）" {
        GenerateCodeFromTemplate.undeclaredArgNames(contextOf(mapOf("roleName" to "Screen"))) shouldBe
            setOf("name", "module")
    }

    "capture の名前を --arg で渡しても未知の引数にならない" {
        checkTemplateArgs(mapOf("roleName" to "ViewModel", "feature" to "home", "name" to "Home"))
    }

    "capture の名前の打ち間違いは未知の引数として落ちる" {
        val thrown = shouldThrow<KatachiUnknownProcessorArgException> {
            checkTemplateArgs(mapOf("roleName" to "ViewModel", "featur" to "home", "name" to "Home"))
        }

        thrown.unknown shouldBe setOf("featur")
    }

    "別の役割の capture の名前は受け付けない" {
        val thrown = shouldThrow<KatachiUnknownProcessorArgException> {
            checkTemplateArgs(mapOf("roleName" to "ViewModel", "module" to "home", "name" to "Home"))
        }

        thrown.unknown shouldBe setOf("module")
    }
})
