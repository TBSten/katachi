package me.tbsten.katachi.test.template

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import me.tbsten.katachi.dsl.files.internal.RealFileSystem
import me.tbsten.katachi.dsl.template
import me.tbsten.katachi.processor.internal.process
import me.tbsten.katachi.template.GenerateCodeFromTemplate
import me.tbsten.katachi.template.KatachiExistingTemplateFileException
import me.tbsten.katachi.template.OnExisting
import me.tbsten.katachi.test.check.architectureOf

/**
 * [GenerateCodeFromTemplate] end to end: [GenerateCodeFromTemplate.Args] in, files on disk out.
 * The path-filling rules themselves are [TemplatePlacementSpec]'s and its siblings'; this spec
 * pins the processor's own contract (`process`, `onExisting`, a real write).
 */
class GenerateCodeFromTemplateSpec : FreeSpec({
    "success: 宣言どおりの内容で、実際にファイルへ書く" {
        withTempProject { projectDir ->
            val arch = architectureOf {
                "domain".group {
                    "UseCase" {
                        layout { "useCase" / "GetUserUseCase.kt".file().template { "class GetUserUseCase" } }
                    }
                }
            }

            arch.process(
                GenerateCodeFromTemplate,
                GenerateCodeFromTemplate.Args(template = listOf("UseCase")),
                RealFileSystem(projectDir),
            ).getOrThrow()

            projectDir.relativeFilePaths() shouldContainExactly listOf("gradlew", "useCase/GetUserUseCase.kt")
            projectDir.resolve("useCase/GetUserUseCase.kt").readText() shouldBe "class GetUserUseCase"
        }
    }

    "onExisting の既定は Fail" {
        GenerateCodeFromTemplate.Args(template = listOf("UseCase")).onExisting shouldBe OnExisting.Fail
    }

    "既存ファイルがあれば、既定 (Fail) では何も書かず落ちる" {
        withTempProject { projectDir ->
            val arch = architectureOf {
                "domain".group {
                    "UseCase" {
                        layout { "useCase" / "GetUserUseCase.kt".file().template { "class GetUserUseCase" } }
                    }
                }
            }
            projectDir.resolve("useCase").mkdirs()
            projectDir.resolve("useCase/GetUserUseCase.kt").writeText("// already here")

            shouldThrow<KatachiExistingTemplateFileException> {
                arch.process(
                    GenerateCodeFromTemplate,
                    GenerateCodeFromTemplate.Args(template = listOf("UseCase")),
                    RealFileSystem(projectDir),
                ).getOrThrow()
            }
            projectDir.resolve("useCase/GetUserUseCase.kt").readText() shouldBe "// already here"
        }
    }
})
