package me.tbsten.katachi.test.architecture

import io.kotest.assertions.withClue
import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.collections.shouldContainAll
import io.kotest.matchers.ints.shouldBeGreaterThan
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import java.io.File
import java.nio.file.Files
import me.tbsten.katachi.ExperimentalKatachiApi
import me.tbsten.katachi.docs.DocumentationMode
import me.tbsten.katachi.docs.GenerateDocumentation
import me.tbsten.katachi.processor.process

@OptIn(ExperimentalKatachiApi::class)
class DocumentationGenerationSpec : FreeSpec({
    "この定義のドキュメントが最後まで生成できる" {
        withTemporaryOutput { output ->
            projectArchitecture.process(
                GenerateDocumentation,
                GenerateDocumentation.Args(outputDir = output.path),
            )

            val pages = output.walkTopDown()
                .filter { it.isFile }
                .map { it.toRelativeString(output).replace(File.separatorChar, '/') }
                .toList()

            withClue("生成されたページ:\n${pages.sorted().joinToString("\n")}") {
                pages.size shouldBeGreaterThan 20
                pages shouldContainAll listOf(
                    "README.md",
                    "Library/README.md",
                    "Library/Docs.md",
                )
            }
        }
    }

    "documented = false の group はディレクトリごと出ない" {
        withTemporaryOutput { output ->
            projectArchitecture.process(
                GenerateDocumentation,
                GenerateDocumentation.Args(outputDir = output.path),
            )

            withClue("Tool group は documented = false なので、ページも一覧の行も無い") {
                File(output, "Tool").exists() shouldBe false
                File(output, "README.md").readText() shouldContain "[ライブラリ](./Library/README.md)"
            }
        }
    }

    "生成した直後の出力は mode=check を通る" {
        withTemporaryOutput { output ->
            val args = GenerateDocumentation.Args(outputDir = output.path)
            projectArchitecture.process(GenerateDocumentation, args)

            withClue("書いたものと比べたものが違えば KatachiStaleDocumentationException が出る") {
                projectArchitecture.process(
                    GenerateDocumentation,
                    args.copy(mode = DocumentationMode.Check),
                )
            }
        }
    }
})

private fun <R> withTemporaryOutput(block: (File) -> R): R {
    val directory = Files.createTempDirectory("katachi-architecture-docs").toFile()
    return try {
        block(directory)
    } finally {
        directory.deleteRecursively()
    }
}
