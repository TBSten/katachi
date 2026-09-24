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

/**
 * Runs documentation generation against the real definition of this repository.
 *
 * Every other spec of the generator builds pages out of a definition written for that one spec:
 * two groups, three roles, one `layout { }`. This one runs it over twenty-odd roles, eight
 * groups, two of them `documented = false`, `konsist { }` blocks on most of the library roles,
 * and `layout { }` blocks naming four Gradle modules — the combination nothing assembled by hand
 * covers, and the one a user actually has.
 *
 * It asks almost nothing of the output. What a page says is pinned in `:katachi`'s own specs
 * against definitions small enough to write out in full; asserting the same here would pin this
 * repository's role names into a test that has no opinion about them. What it does assert is
 * that generation gets all the way to the end — path collisions, case collisions and broken
 * links are all raised from inside, so "no exception" is the claim being made.
 *
 * It writes into a temporary directory rather than into `build/`, so that nothing it leaves
 * behind can reach `gitTracked()` or the next run.
 */
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
                    "library/README.md",
                    "library/Docs.md",
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

            withClue("tool group は documented = false なので、ページも一覧の行も無い") {
                File(output, "tool").exists() shouldBe false
                File(output, "README.md").readText() shouldContain "[ライブラリ](./library/README.md)"
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

/** A directory outside the repository, removed however the block ends. */
private fun <R> withTemporaryOutput(block: (File) -> R): R {
    val directory = Files.createTempDirectory("katachi-architecture-docs").toFile()
    return try {
        block(directory)
    } finally {
        directory.deleteRecursively()
    }
}
