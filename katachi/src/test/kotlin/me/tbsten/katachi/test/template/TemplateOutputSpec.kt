package me.tbsten.katachi.test.template

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.assertions.withClue
import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import java.io.File
import java.nio.file.Files
import me.tbsten.katachi.template.KatachiExistingTemplateFileException
import me.tbsten.katachi.template.KatachiReservedTemplatePathException
import me.tbsten.katachi.template.KatachiTemplateEscapesProjectException
import me.tbsten.katachi.template.KatachiTemplateIoException
import me.tbsten.katachi.template.KatachiTemplateTargetNotAFileException
import me.tbsten.katachi.template.OnExisting
import me.tbsten.katachi.template.internal.writeTemplateFiles

/** The two files every spec below writes, in declaration order. */
private val FILES: Map<String, String> = linkedMapOf(
    "useCase/GetUserUseCase.kt" to "interface GetUserUseCase",
    "useCase/GetUserUseCaseImpl.kt" to "class GetUserUseCaseImpl",
)

private fun File.write(path: String, content: String) {
    val file = File(this, path)
    file.parentFile?.mkdirs()
    file.writeText(content)
}

/**
 * Putting a generated set of files on a disk.
 *
 * Deciding what the set *is* is [TemplatePlacementSpec]'s.
 */
class TemplateOutputSpec : FreeSpec({
    "何も無いところへ書く" - {
        "宣言どおりのパスに、ディレクトリごと書かれる" {
            withTempProject { root ->
                writeTemplateFiles(root, FILES, OnExisting.Fail) { } shouldBe true

                root.relativeFilePaths() shouldContainExactly listOf(
                    "gradlew",
                    "useCase/GetUserUseCase.kt",
                    "useCase/GetUserUseCaseImpl.kt",
                )
                File(root, "useCase/GetUserUseCase.kt").readText() shouldBe "interface GetUserUseCase"
            }
        }

        "書いたパスを1行ずつ報告する" {
            withTempProject { root ->
                val logs = mutableListOf<String>()

                writeTemplateFiles(root, FILES, OnExisting.Fail) { logs += it }

                logs shouldContainExactly listOf(
                    "Wrote useCase/GetUserUseCase.kt",
                    "Wrote useCase/GetUserUseCaseImpl.kt",
                )
            }
        }

        "作業用の一時ファイルを残さない" {
            withTempProject { root ->
                writeTemplateFiles(root, FILES, OnExisting.Fail) { }

                root.relativeFilePaths().none { it.endsWith(".katachi-new") } shouldBe true
            }
        }
    }

    "既存ファイルがあるとき" - {
        "fail: 1つでも既存があれば1つも書かない" {
            withTempProject { root ->
                root.write("useCase/GetUserUseCaseImpl.kt", "written by hand")

                val thrown = shouldThrow<KatachiExistingTemplateFileException> {
                    writeTemplateFiles(root, FILES, OnExisting.Fail) { }
                }

                thrown.existing shouldContainExactly listOf("useCase/GetUserUseCaseImpl.kt")
                thrown.total shouldBe 2
                // The file that was free is still absent: a half-applied template would leave a
                // tree nothing on disk explains.
                File(root, "useCase/GetUserUseCase.kt").exists() shouldBe false
                File(root, "useCase/GetUserUseCaseImpl.kt").readText() shouldBe "written by hand"
            }
        }

        "skip: 1つでも既存があれば1つも書かず、失敗もしない" {
            withTempProject { root ->
                root.write("useCase/GetUserUseCaseImpl.kt", "written by hand")
                val logs = mutableListOf<String>()

                writeTemplateFiles(root, FILES, OnExisting.Skip) { logs += it } shouldBe false

                File(root, "useCase/GetUserUseCase.kt").exists() shouldBe false
                File(root, "useCase/GetUserUseCaseImpl.kt").readText() shouldBe "written by hand"
                logs.single() shouldContain "Wrote nothing"
            }
        }

        "overwrite: 既存を置き換え、残りも書く" {
            withTempProject { root ->
                root.write("useCase/GetUserUseCaseImpl.kt", "written by hand")

                writeTemplateFiles(root, FILES, OnExisting.Overwrite) { } shouldBe true

                File(root, "useCase/GetUserUseCaseImpl.kt").readText() shouldBe
                    "class GetUserUseCaseImpl"
                File(root, "useCase/GetUserUseCase.kt").readText() shouldBe "interface GetUserUseCase"
            }
        }
    }

    "途中で書けなくなったとき" - {
        "1ファイル目が書けても、2ファイル目が失敗すればファイルは1つも残らない" {
            withTempProject { root ->
                // `docs` is a plain file, so creating the directory of the second target fails --
                // after the first one has already been rendered to disk.
                root.write("docs", "not a directory")
                val files = linkedMapOf(
                    "useCase/GetUserUseCase.kt" to "interface GetUserUseCase",
                    "docs/GetUser.md" to "# GetUser",
                )

                val thrown = shouldThrow<KatachiTemplateIoException> {
                    writeTemplateFiles(root, files, OnExisting.Fail) { }
                }

                thrown.path shouldBe "docs/GetUser.md"
                root.relativeFilePaths() shouldContainExactly listOf("docs", "gradlew")
            }
        }
    }

    "katachi 自身が使う名前が塞がれているとき" - {
        "生成物と同名の .katachi-new があれば、1つも書かずに落ちる" {
            withTempProject { root ->
                // Written by hand, and named this way by coincidence -- a leftover from an editor,
                // a file someone renamed. The run used to overwrite it and then delete it.
                root.write("useCase/GetUserUseCase.kt.katachi-new", "keep me")

                val thrown = shouldThrow<KatachiReservedTemplatePathException> {
                    writeTemplateFiles(root, FILES, OnExisting.Fail) { }
                }

                thrown.reserved shouldContainExactly listOf("useCase/GetUserUseCase.kt.katachi-new")
                File(root, "useCase/GetUserUseCase.kt.katachi-new").readText() shouldBe "keep me"
                File(root, "useCase/GetUserUseCase.kt").exists() shouldBe false
            }
        }

        ".katachi-old も同じに扱う" {
            withTempProject { root ->
                root.write("useCase/GetUserUseCaseImpl.kt.katachi-old", "keep me too")

                val thrown = shouldThrow<KatachiReservedTemplatePathException> {
                    writeTemplateFiles(root, FILES, OnExisting.Overwrite) { }
                }

                thrown.reserved shouldContainExactly
                    listOf("useCase/GetUserUseCaseImpl.kt.katachi-old")
                File(root, "useCase/GetUserUseCaseImpl.kt.katachi-old").readText() shouldBe "keep me too"
            }
        }

        "既存ファイルの報告のほうが先に出る" {
            withTempProject { root ->
                root.write("useCase/GetUserUseCase.kt", "written by hand")
                root.write("useCase/GetUserUseCase.kt.katachi-new", "leftover")

                withClue("予約語の話より、自分のファイルが既にある話のほうが読み手に近い") {
                    shouldThrow<KatachiExistingTemplateFileException> {
                        writeTemplateFiles(root, FILES, OnExisting.Fail) { }
                    }
                }
            }
        }
    }

    "的がファイルでないとき" - {
        "ディレクトリが居座っていれば、overwrite でも1つも書かずに落ちる" {
            withTempProject { root ->
                // A package directory where the layout says a file goes. Replacing it would take
                // the files inside with it, and those are not this run's to move.
                root.write("useCase/GetUserUseCase.kt/Inner.kt", "someone else's code")

                val thrown = shouldThrow<KatachiTemplateTargetNotAFileException> {
                    writeTemplateFiles(root, FILES, OnExisting.Overwrite) { }
                }

                thrown.blocked shouldContainExactly listOf("useCase/GetUserUseCase.kt")
                File(root, "useCase/GetUserUseCase.kt/Inner.kt").readText() shouldBe
                    "someone else's code"
                File(root, "useCase/GetUserUseCaseImpl.kt").exists() shouldBe false
            }
        }
    }

    "リンクで外へ出るとき" - {
        "シンボリックリンク越しにプロジェクトの外へは書かない" {
            withTempProject { root ->
                withTempProject { outside ->
                    File(outside, "GetUserUseCase.kt").writeText("written by hand, outside")
                    // `useCase` resolves out of the project. The declared path stays relative, so
                    // the check that reads the path alone cannot see this.
                    Files.createSymbolicLink(File(root, "useCase").toPath(), outside.toPath())

                    val thrown = shouldThrow<KatachiTemplateEscapesProjectException> {
                        writeTemplateFiles(root, FILES, OnExisting.Overwrite) { }
                    }

                    thrown.path shouldBe "useCase/GetUserUseCase.kt"
                    File(outside, "GetUserUseCase.kt").readText() shouldBe "written by hand, outside"
                    File(outside, "GetUserUseCaseImpl.kt").exists() shouldBe false
                }
            }
        }

        "プロジェクトの中を指すリンクは通す" {
            withTempProject { root ->
                File(root, "real").mkdirs()
                Files.createSymbolicLink(File(root, "useCase").toPath(), File(root, "real").toPath())

                writeTemplateFiles(root, FILES, OnExisting.Fail) { } shouldBe true

                File(root, "real/GetUserUseCase.kt").readText() shouldBe "interface GetUserUseCase"
            }
        }
    }
})
