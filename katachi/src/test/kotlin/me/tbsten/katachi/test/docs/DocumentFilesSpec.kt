package me.tbsten.katachi.test.docs

import com.google.common.jimfs.Configuration
import com.google.common.jimfs.Jimfs
import io.kotest.assertions.withClue
import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.PathWalkOption
import kotlin.io.path.createDirectories
import kotlin.io.path.exists
import kotlin.io.path.invariantSeparatorsPathString
import kotlin.io.path.readText
import kotlin.io.path.relativeTo
import kotlin.io.path.walk
import kotlin.io.path.writeText
import me.tbsten.katachi.docs.internal.DocumentDifference
import me.tbsten.katachi.docs.internal.compareDocuments
import me.tbsten.katachi.docs.internal.writeDocuments
import me.tbsten.katachi.dsl.Architecture
import me.tbsten.katachi.dsl.architecture

/**
 * The output directory on a file system that ignores case but keeps it, as macOS and Windows do by
 * default.
 *
 * Linux keeps `Api.md` and `API.md` apart, so a temporary directory on the CI machine cannot say
 * anything about this; Jimfs's `Configuration.osX()` answers lookups the way APFS does. What
 * [GenerateDocumentationSpec] checks on the real disk is not repeated here.
 */
class DocumentFilesSpec : FreeSpec({
    "前提: このファイルシステムは大文字小文字を区別せず、作ったときの綴りを保つ" {
        withCaseInsensitiveOutput { output ->
            output.createDirectories()
            output.resolve("Api.md").writeText("old")

            output.resolve("API.md").writeText("new")

            Files.isSameFile(output.resolve("Api.md"), output.resolve("API.md")) shouldBe true
            withClue("書き込んでも、一覧に出る名前は最初に作ったときの Api.md のまま") {
                output.entries() shouldContainExactly listOf("Api.md")
            }
        }
    }

    "書き出し" - {
        "Role の名前の大文字小文字だけを変えて書き直すと、ページのファイル名も新しい綴りになる" {
            withCaseInsensitiveOutput { output ->
                architecture { "Api".role { } }.writeTo(output)
                val renamed = architecture { "API".role { } }

                val logs = renamed.writeTo(output)

                withClue("README は ./API.md を指すので、Api.md のまま残すと区別するファイルシステムでリンクが切れる") {
                    output.entries() shouldContainExactly listOf("API.md", "README.md")
                }
                output.resolve("README.md").readText() shouldContain "(./API.md)"
                withClue("区別するファイルシステムと同じく、前の綴りのページを消したと言う") {
                    logs.filter { it.startsWith("Removed ") } shouldContainExactly listOf(
                        "Removed file:///work/docs/Api.md, which this definition no longer produces.",
                    )
                }
                compareDocuments(output, renamed.documents()).isUpToDate shouldBe true
            }
        }

        "group の名前の大文字小文字だけを変えて書き直すと、ディレクトリも新しい綴りになる" {
            withCaseInsensitiveOutput { output ->
                architecture { "Api".group { "Endpoint".role { } } }.writeTo(output)
                val renamed = architecture { "API".group { "Endpoint".role { } } }

                renamed.writeTo(output)

                output.entries() shouldContainExactly listOf("API", "API/Endpoint.md", "API/README.md", "README.md")
                compareDocuments(output, renamed.documents()).isUpToDate shouldBe true
            }
        }

        "区別するファイルシステムで書き直したときと同じ綴りになる" {
            val before = architecture {
                "Api".role { }
                "Web".group { "Client".role { } }
            }
            val after = architecture {
                "API".role { }
                "WEB".group { "Client".role { } }
            }

            val sensitive = withCaseSensitiveOutput { output ->
                before.writeTo(output)
                after.writeTo(output)
                output.entries()
            }
            val insensitive = withCaseInsensitiveOutput { output ->
                before.writeTo(output)
                after.writeTo(output)
                output.entries()
            }

            withClue("macOS で生成してコミットしたものを、Linux の CI の mode=check が受け入れられるように") {
                insensitive shouldBe sensitive
            }
        }

        "前の綴りのディレクトリに katachi が書かないファイルがあれば、そのファイルは消さない" {
            withCaseInsensitiveOutput { output ->
                architecture { "Api".group { "Endpoint".role { } } }.writeTo(output)
                // Finder leaves one of these in every folder it has shown.
                output.resolve("Api/.DS_Store").writeText("")

                architecture { "API".group { "Endpoint".role { } } }.writeTo(output)

                withClue("消してよいと言い切れるのは katachi 自身が書く .md だけ") {
                    output.entries().map { it.lowercase() } shouldContain "api/.ds_store"
                }
            }
        }
    }

    "mode=check" - {
        "README は ./API.md を指すのにページが Api.md の綴りで残っていれば、欠けているページと余分なページとして報告される" {
            withCaseInsensitiveOutput { output ->
                val pages = architecture { "API".role { } }.documents()
                // What a write that kept the old spelling leaves behind: the new pages, API.md named Api.md.
                output.createDirectories()
                output.resolve("README.md").writeText(pages.getValue("README.md"))
                output.resolve("Api.md").writeText(pages.getValue("API.md"))

                val difference = compareDocuments(output, pages)

                withClue("API.md を開けば Api.md の中身が読めても、ディレクトリにある名前は Api.md") {
                    difference.missing shouldContainExactly listOf("API.md")
                    difference.different.shouldBeEmpty()
                    difference.extra shouldContainExactly listOf("Api.md")
                }
            }
        }

        "group のディレクトリが前の綴りで残っていても、同じく報告される" {
            withCaseInsensitiveOutput { output ->
                val pages = architecture { "API".group { "Endpoint".role { } } }.documents()
                output.resolve("Api").createDirectories()
                output.resolve("README.md").writeText(pages.getValue("README.md"))
                output.resolve("Api/README.md").writeText(pages.getValue("API/README.md"))
                output.resolve("Api/Endpoint.md").writeText(pages.getValue("API/Endpoint.md"))

                val difference = compareDocuments(output, pages)

                difference.missing shouldContainExactlyInAnyOrder listOf("API/README.md", "API/Endpoint.md")
                difference.different.shouldBeEmpty()
                difference.extra shouldContainExactly listOf("Api/Endpoint.md", "Api/README.md")
            }
        }

        "区別するファイルシステムと同じ食い違いを報告する" {
            val pages = architecture { "API".role { } }.documents()
            val leftBehind: (Path) -> DocumentDifference = { output ->
                output.createDirectories()
                output.resolve("README.md").writeText(pages.getValue("README.md"))
                output.resolve("Api.md").writeText(pages.getValue("API.md"))
                compareDocuments(output, pages)
            }

            val sensitive = withCaseSensitiveOutput(leftBehind)
            val insensitive = withCaseInsensitiveOutput(leftBehind)

            withClue("macOS で最新と言われたものが、Linux の CI で古いと言われないように") {
                insensitive.summary() shouldBe sensitive.summary()
            }
        }
    }

    "空の outputDir" - {
        "作業ディレクトリにある .md は消さず、そこにページも書かない" {
            Jimfs.newFileSystem(Configuration.osX()).use { fileSystem ->
                val workingDirectory = fileSystem.getPath("").toAbsolutePath()
                workingDirectory.resolve("notes.md").writeText("hand written")

                writeDocuments(fileSystem.getPath(""), architecture { "Api".role { } }.documents()) { }

                withClue("空のパスは Path では作業ディレクトリを指すが、katachi の出力先として指されたわけではない") {
                    workingDirectory.resolve("notes.md").exists() shouldBe true
                    workingDirectory.resolve("README.md").exists() shouldBe false
                }
            }
        }
    }
})

/** Runs [block] on a directory of a fresh in-memory file system that ignores case but keeps it, like APFS. */
private fun <R> withCaseInsensitiveOutput(block: (Path) -> R): R =
    Jimfs.newFileSystem(Configuration.osX()).use { block(it.getPath("/work/docs")) }

/** Runs [block] on a directory of a fresh in-memory file system that keeps `Api.md` and `API.md` apart, like Linux. */
private fun <R> withCaseSensitiveOutput(block: (Path) -> R): R =
    Jimfs.newFileSystem(Configuration.unix()).use { block(it.getPath("/work/docs")) }

/** Writes this definition's documentation into [output] and answers what it logged. */
private fun Architecture.writeTo(output: Path): List<String> =
    buildList { writeDocuments(output, documents()) { add(it) } }

/** Every file and directory below [this], spelled as the directory lists it, relative and sorted. */
private fun Path.entries(): List<String> = walk(PathWalkOption.INCLUDE_DIRECTORIES)
    .filter { it != this }
    .map { it.relativeTo(this).invariantSeparatorsPathString }
    .sorted()
    .toList()

private fun DocumentDifference.summary(): List<List<String>> = listOf(missing, different, extra)
