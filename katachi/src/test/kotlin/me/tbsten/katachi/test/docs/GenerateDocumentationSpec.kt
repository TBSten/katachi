package me.tbsten.katachi.test.docs

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.assertions.withClue
import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import java.io.File
import me.tbsten.katachi.docs.DocumentationMode
import me.tbsten.katachi.docs.GenerateDocumentation
import me.tbsten.katachi.docs.KatachiStaleDocumentationException
import me.tbsten.katachi.dsl.Architecture
import me.tbsten.katachi.dsl.architecture
import me.tbsten.katachi.fs.KatachiFileSystem
import me.tbsten.katachi.processor.FakeArchitectureProcessContext
import me.tbsten.katachi.processor.KatachiInvalidProcessorArgException
import me.tbsten.katachi.processor.decodeFromStringMap
import me.tbsten.katachi.test.fs.ForbiddenFileSystem

/**
 * The shell of documentation generation: what reaches the disk, and what it says while doing it.
 *
 * What is *on* a page belongs to [ContainerPageSpec] and [RolePageSpec]; every spec here runs
 * against [ForbiddenFileSystem], so each of them also says, without repeating it, that writing
 * the pages reads nothing of the project through katachi's own file system.
 *
 * NOTE: このファイルのパッケージを `me.tbsten.katachi.docs` にしてはいけない。
 * captureDeclarationSite() がライブラリ自身のフレームとして読み飛ばしてしまい、
 * 宣言位置が kotest 内部を指すようになる。
 */
class GenerateDocumentationSpec : FreeSpec({
    "書き出し" - {
        "ページ一式が組み立てたとおりのパスと中身で書き出される" {
            val arch = architecture {
                "domain".group {
                    title = "ドメイン"
                    "UseCase" { title = "ユースケース" }
                }
            }

            withTempDirectory { output ->
                arch.generateDocumentation(output)

                output.relativeFilePaths() shouldContainExactly listOf(
                    "README.md",
                    "domain/README.md",
                    "domain/UseCase.md",
                )
                for ((path, content) in arch.documents()) {
                    withClue(path) { File(output, path).readText() shouldBe content }
                }
            }
        }

        "出力先のディレクトリがまだ無ければ作られる" {
            val arch = architecture { "domain".group { "UseCase" { } } }

            withTempDirectory { output ->
                val nested = File(output, "build/katachi/docs")
                arch.generateDocumentation(nested)

                nested.relativeFilePaths() shouldContainExactly listOf(
                    "README.md",
                    "domain/README.md",
                    "domain/UseCase.md",
                )
            }
        }

        "引数を省略すると build/katachi/docs に出る" {
            withClue("JavaExec はタスクの属するモジュールで起動するので、そこからの相対") {
                GenerateDocumentation.Args().outputDir shouldBe "build/katachi/docs"
            }
        }

        "宣言から消えた役割のページは削除される" {
            val arch = architecture { "domain".group { "UseCase" { } } }

            withTempDirectory { output ->
                File(output, "domain").mkdirs()
                File(output, "domain/Interactor.md").writeText("# 消えた役割\n")

                arch.generateDocumentation(output)

                withClue("残しておくと、どこからもリンクされないページを読み手が拾ってしまう") {
                    output.relativeFilePaths() shouldContainExactly listOf(
                        "README.md",
                        "domain/README.md",
                        "domain/UseCase.md",
                    )
                }
            }
        }

        "katachi が書かない拡張子のファイルは消さない" {
            val arch = architecture { "domain".group { "UseCase" { } } }

            withTempDirectory { output ->
                File(output, "diagram.png").writeText("not a page")
                File(output, "notes.txt").writeText("hand written")

                arch.generateDocumentation(output)

                withClue("消してよいと言い切れるのは katachi 自身が書く .md だけ") {
                    output.relativeFilePaths() shouldContainExactly listOf(
                        "README.md",
                        "diagram.png",
                        "domain/README.md",
                        "domain/UseCase.md",
                        "notes.txt",
                    )
                }
            }
        }

        "削除で空になったディレクトリも残さない" {
            val arch = architecture { "domain".group { "UseCase" { } } }

            withTempDirectory { output ->
                File(output, "legacy/deep").mkdirs()
                File(output, "legacy/deep/Old.md").writeText("# 消えた group\n")

                arch.generateDocumentation(output)

                File(output, "legacy").exists() shouldBe false
            }
        }

        "出力ルート自体は、生成物が1枚も無くても消さない" {
            withTempDirectory { output ->
                architecture { }.generateDocumentation(output)

                withClue("outputDir は利用者が指したディレクトリで、katachi が消してよいものではない") {
                    output.isDirectory shouldBe true
                }
            }
        }

        "書いたページも消したページも context.log に出る" {
            val arch = architecture { "domain".group { "UseCase" { } } }

            withTempDirectory { output ->
                File(output, "Stale.md").writeText("# 古いページ\n")
                val context = writeContextFor(arch, output)

                GenerateDocumentation.process(context)

                context.logs shouldContainExactly listOf(
                    "Writing 3 pages to ${output.path}",
                    "Removed Stale.md, which this definition no longer produces.",
                    "Wrote README.md",
                    "Wrote domain/README.md",
                    "Wrote domain/UseCase.md",
                )
            }
        }
    }

    "検査とは独立している" - {
        "制約は1つも評価されない" {
            val arch = architecture {
                "domain".group {
                    "UseCase" {
                        constraint("走らせてはいけない制約") { error("この制約は評価されたら失敗する") }
                        layout { "domain" { "*UseCase.kt".file() } }
                    }
                }
            }

            withTempDirectory { output ->
                withClue("評価されたら error() が投げるので、例外が出ないことが証拠になる") {
                    arch.generateDocumentation(output)
                }

                File(output, "domain/UseCase.md") shouldContainText "## 制約"
            }
        }

        "プロジェクトのファイルを1つも読まない" {
            val arch = architecture { "domain".group { "UseCase" { layout { "domain" { "*.kt".file() } } } } }

            withTempDirectory { output ->
                withClue("読んだら ForbiddenFileSystem が投げる。違反があっても生成が通るのはこれが理由") {
                    arch.generateDocumentation(output)
                }
            }
        }
    }

    "mode=check" - {
        "生成物が最新なら何も起きない" {
            val arch = architecture { "domain".group { "UseCase" { } } }

            withTempDirectory { output ->
                arch.generateDocumentation(output)
                val context = checkContextFor(arch, output)

                GenerateDocumentation.process(context)

                context.logs.last() shouldBe "${output.path} is up to date."
            }
        }

        "欠けているページが報告される" {
            val arch = architecture { "domain".group { "UseCase" { } } }

            withTempDirectory { output ->
                arch.generateDocumentation(output)
                File(output, "domain/UseCase.md").delete()

                val failure = shouldThrow<KatachiStaleDocumentationException> { arch.checkDocumentation(output) }

                failure.missing shouldContainExactly listOf("domain/UseCase.md")
                failure.message.orEmpty() shouldContain "[missing]   domain/UseCase.md"
            }
        }

        "中身が違うページが報告される" {
            val arch = architecture { "domain".group { "UseCase" { } } }

            withTempDirectory { output ->
                arch.generateDocumentation(output)
                File(output, "README.md").writeText("# 手で書き換えた\n")

                val failure = shouldThrow<KatachiStaleDocumentationException> { arch.checkDocumentation(output) }

                failure.different shouldContainExactly listOf("README.md")
                failure.message.orEmpty() shouldContain "[different] README.md"
            }
        }

        "もう生成しないページが余分として報告される" {
            val arch = architecture { "domain".group { "UseCase" { } } }

            withTempDirectory { output ->
                arch.generateDocumentation(output)
                File(output, "domain/Interactor.md").writeText("# 消えた役割\n")

                val failure = shouldThrow<KatachiStaleDocumentationException> { arch.checkDocumentation(output) }

                failure.extra shouldContainExactly listOf("domain/Interactor.md")
                failure.message.orEmpty() shouldContain "[extra]     domain/Interactor.md"
            }
        }

        "3種類は1つの例外にまとめて載る" {
            val arch = architecture { "domain".group { "UseCase" { } } }

            withTempDirectory { output ->
                arch.generateDocumentation(output)
                File(output, "domain/UseCase.md").delete()
                File(output, "README.md").writeText("# 手で書き換えた\n")
                File(output, "domain/Interactor.md").writeText("# 消えた役割\n")

                val failure = shouldThrow<KatachiStaleDocumentationException> { arch.checkDocumentation(output) }

                withClue("1件ずつ落ちると、直すのに3往復かかる") {
                    failure.message.orEmpty().lines().first() shouldBe
                        "3 documentation pages under \"${output.path}\" are not what this " +
                        "definition produces: 1 missing, 1 different, 1 extra."
                }
                failure.outputDir shouldBe output.path
            }
        }

        "出力先がまだ無ければ、全ページが欠けていると報告される" {
            val arch = architecture { "domain".group { "UseCase" { } } }

            withTempDirectory { output ->
                val absent = File(output, "never-generated")

                val failure = shouldThrow<KatachiStaleDocumentationException> { arch.checkDocumentation(absent) }

                failure.missing shouldContainExactly listOf(
                    "README.md",
                    "domain/README.md",
                    "domain/UseCase.md",
                )
            }
        }

        "1文字も書かない" {
            val arch = architecture { "domain".group { "UseCase" { } } }

            withTempDirectory { output ->
                arch.generateDocumentation(output)
                File(output, "README.md").writeText("# 手で書き換えた\n")
                File(output, "domain/Interactor.md").writeText("# 消えた役割\n")

                shouldThrow<KatachiStaleDocumentationException> { arch.checkDocumentation(output) }

                withClue("比べるだけなので、余分なページも書き換えた中身もそのまま残る") {
                    output.relativeFilePaths() shouldContainExactly listOf(
                        "README.md",
                        "domain/Interactor.md",
                        "domain/README.md",
                        "domain/UseCase.md",
                    )
                    File(output, "README.md").readText() shouldBe "# 手で書き換えた\n"
                }
            }
        }
    }

    "--arg" - {
        "mode=check が DocumentationMode になる" {
            decodeFromStringMap(GenerateDocumentation.argsSerializer, mapOf("mode" to "check")) shouldBe
                GenerateDocumentation.Args(mode = DocumentationMode.Check)
        }

        "mode=write も書ける" {
            decodeFromStringMap(GenerateDocumentation.argsSerializer, mapOf("mode" to "write")) shouldBe
                GenerateDocumentation.Args(mode = DocumentationMode.Write)
        }

        "outputDir はそのまま文字列として渡る" {
            decodeFromStringMap(
                GenerateDocumentation.argsSerializer,
                mapOf("outputDir" to "docs/architecture"),
            ) shouldBe GenerateDocumentation.Args(outputDir = "docs/architecture")
        }

        "1つも渡さなければ両方とも既定値になる" {
            decodeFromStringMap(GenerateDocumentation.argsSerializer, emptyMap()) shouldBe
                GenerateDocumentation.Args()
        }

        "知らない mode は例外になる" {
            withClue("Write / Check という Kotlin 側の綴りではなく、--arg に書く綴りで受ける") {
                shouldThrow<KatachiInvalidProcessorArgException> {
                    decodeFromStringMap(GenerateDocumentation.argsSerializer, mapOf("mode" to "Check"))
                }
            }
        }
    }
})

/** Writes this definition's documentation into [output], reading nothing of the project. */
private fun Architecture.generateDocumentation(output: File) =
    GenerateDocumentation.process(writeContextFor(this, output))

/** Compares this definition's documentation against [output] without writing anything. */
private fun Architecture.checkDocumentation(output: File) =
    GenerateDocumentation.process(checkContextFor(this, output))

private fun writeContextFor(
    architecture: Architecture,
    output: File,
    fileSystem: KatachiFileSystem = ForbiddenFileSystem,
) = FakeArchitectureProcessContext(
    architecture = architecture,
    args = GenerateDocumentation.Args(outputDir = output.path),
    fileSystem = fileSystem,
)

private fun checkContextFor(
    architecture: Architecture,
    output: File,
    fileSystem: KatachiFileSystem = ForbiddenFileSystem,
) = FakeArchitectureProcessContext(
    architecture = architecture,
    args = GenerateDocumentation.Args(outputDir = output.path, mode = DocumentationMode.Check),
    fileSystem = fileSystem,
)

/** Reads better than nesting a `readText()` inside the clue every one of these needs. */
private infix fun File.shouldContainText(text: String) {
    withClue("$path:\n${readText()}") { readText().contains(text) shouldBe true }
}
