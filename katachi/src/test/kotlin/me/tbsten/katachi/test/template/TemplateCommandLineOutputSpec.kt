package me.tbsten.katachi.test.template

import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import java.io.File
import me.tbsten.katachi.dsl.Architecture
import me.tbsten.katachi.dsl.architecture
import me.tbsten.katachi.dsl.files.internal.RealFileSystem
import me.tbsten.katachi.processor.internal.runProcessors
import me.tbsten.katachi.template.GenerateCodeFromTemplate

private fun useCaseArchitecture(): Architecture = architecture {
    "domain".group {
        "UseCase" {
            layout {
                "useCase" / "*UseCase.kt".file()
                "useCase" / "*UseCaseImpl.kt".file()
            }
            template {
                val name by stringParameter()
                file("${name}UseCase.kt") { "interface ${name}UseCase" }
                file("${name}UseCaseImpl.kt") { "class ${name}UseCaseImpl" }
            }
        }
    }
}

/** Every line `katachiTemplate` prints for one run in [root]. */
private fun runTemplate(root: File, vararg args: Pair<String, String>): List<String> {
    val out = mutableListOf<String>()
    runProcessors(
        architecture = useCaseArchitecture(),
        registry = mapOf("template" to GenerateCodeFromTemplate::class.java),
        processorKeys = listOf("template"),
        rawArgs = mapOf("roleName" to "UseCase", "name" to "GetUser") + args,
        fileSystem = RealFileSystem(root),
        out = out::add,
    )
    return out
}

/** The processor's own lines and the run's ending, without the `[1/3]` / `[2/3]` header and the separators. */
private fun List<String>.reported(): List<String> =
    filter { it.startsWith("  ") || it.startsWith("[OK]") || it.startsWith("[FAILED]") }

/**
 * The lines of `katachiTemplate`'s output that the katachi IDE plugin parses.
 *
 * No code is behind this spec that the others do not already test. It exists because the IDE
 * reads these lines as a format -- `[template] Wrote file:///...`, the `Overwriting` and
 * `Wrote nothing` lists split on `, `, the conflict report under `[FAILED] template` -- and a
 * reworded message that every other spec accepts would still break the IDE.
 */
class TemplateCommandLineOutputSpec : FreeSpec({
    "成功: Generating の行、書いたファイルごとの Wrote 行、[OK] template" {
        withTempProject { root ->
            val uri = "file://${root.invariantSeparatorsPath}"

            runTemplate(root).reported() shouldContainExactly listOf(
                "  [template] Generating 2 files under $uri",
                "  [template] Wrote $uri/useCase/GetUserUseCase.kt",
                "  [template] Wrote $uri/useCase/GetUserUseCaseImpl.kt",
                "[OK] template",
            )
        }
    }

    "衝突 (onExisting=fail): [FAILED] template の下に件数の行と、4つ字下げの既存ファイルの URI" {
        withTempProject { root ->
            val uri = "file://${root.invariantSeparatorsPath}"
            File(root, "useCase").mkdirs()
            File(root, "useCase/GetUserUseCaseImpl.kt").writeText("written by hand")

            val lines = runTemplate(root).reported()

            lines.take(4) shouldContainExactly listOf(
                "  [template] Generating 2 files under $uri",
                "[FAILED] template",
                "  1 of 2 generated files already exist under $uri:",
                "    $uri/useCase/GetUserUseCaseImpl.kt",
            )
        }
    }

    "スキップ (onExisting=skip): Wrote nothing の行に既存ファイルを , 区切りで並べ、). で閉じる" {
        withTempProject { root ->
            val uri = "file://${root.invariantSeparatorsPath}"
            File(root, "useCase").mkdirs()
            File(root, "useCase/GetUserUseCase.kt").writeText("written by hand")
            File(root, "useCase/GetUserUseCaseImpl.kt").writeText("written by hand")

            runTemplate(root, "onExisting" to "skip").reported() shouldContainExactly listOf(
                "  [template] Generating 2 files under $uri",
                "  [template] Wrote nothing: 2 of 2 files are already there " +
                    "($uri/useCase/GetUserUseCase.kt, $uri/useCase/GetUserUseCaseImpl.kt).",
                "[OK] template",
            )
        }
    }

    "上書き (onExisting=overwrite): Overwriting の行に置き換えるファイルを , 区切りで並べ、全ファイルの Wrote 行が続く" {
        withTempProject { root ->
            val uri = "file://${root.invariantSeparatorsPath}"
            File(root, "useCase").mkdirs()
            File(root, "useCase/GetUserUseCaseImpl.kt").writeText("written by hand")

            runTemplate(root, "onExisting" to "overwrite").reported() shouldContainExactly listOf(
                "  [template] Generating 2 files under $uri",
                "  [template] Overwriting 1 of 2 files: $uri/useCase/GetUserUseCaseImpl.kt",
                "  [template] Wrote $uri/useCase/GetUserUseCase.kt",
                "  [template] Wrote $uri/useCase/GetUserUseCaseImpl.kt",
                "[OK] template",
            )
        }
    }

    "空白を含むパスは URI の中で %20 になり、, での分割を壊さない" {
        withTempProject { parent ->
            val root = File(parent, "my project").apply { mkdirs() }
            File(root, "gradlew").writeText("")

            val lines = runTemplate(root).reported()

            lines[1] shouldBe "  [template] Wrote file://${parent.invariantSeparatorsPath}/my%20project/useCase/GetUserUseCase.kt"
        }
    }
})
