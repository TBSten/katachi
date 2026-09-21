package me.tbsten.katachi.test.check

import io.kotest.assertions.withClue
import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import java.io.File
import me.tbsten.katachi.check.FsPath
import me.tbsten.katachi.check.KatachiFileSystem
import me.tbsten.katachi.check.RealFileSystem

/** Creates an empty file at each path, and every directory leading to it. */
private fun buildRealTree(root: File, relativePaths: List<String>) {
    for (relative in relativePaths) {
        val file = File(root, relative)
        file.parentFile.mkdirs()
        file.writeText("")
    }
}

private fun createTemporaryDirectory(): File {
    val directory = File.createTempFile("katachi-fs", "")
    check(directory.delete()) { "cannot replace ${directory.absolutePath} with a directory" }
    check(directory.mkdirs()) { "cannot create ${directory.absolutePath}" }
    return directory
}

class FileSystemSpec : FreeSpec({
    "偽のファイルシステム" - {
        val fileSystem = fakeFileSystem(workingDirectory = "/repo/app") {
            "/repo" {
                "gradlew"()
                ".gitignore"()
                "docs" {
                    "README.md"()
                    "stray.txt"()
                }
            }
        }

        "ブロックで宣言したファイルが存在する" {
            fileSystem.exists(FsPath.of("/repo/docs/README.md")) shouldBe true
        }

        "宣言していないファイルは存在しない" {
            fileSystem.exists(FsPath.of("/repo/docs/CHANGELOG.md")) shouldBe false
        }

        "ブロックを与えたキーはディレクトリになる" {
            fileSystem.isDirectory(FsPath.of("/repo/docs")) shouldBe true
        }

        "ブロックを与えなかったキーはファイルになる" {
            fileSystem.exists(FsPath.of("/repo/gradlew")) shouldBe true
            fileSystem.isDirectory(FsPath.of("/repo/gradlew")) shouldBe false
        }

        "親ディレクトリは明示しなくても作られる" {
            fileSystem.isDirectory(FsPath.of("/repo")) shouldBe true
            fileSystem.isDirectory(FsPath.of("/")) shouldBe true
        }

        "何も宣言していない作業ディレクトリも存在する" {
            fileSystem.workingDirectory shouldBe FsPath.of("/repo/app")
            fileSystem.isDirectory(FsPath.of("/repo/app")) shouldBe true
        }

        "list は直下だけを名前順で返す" {
            fileSystem.list(FsPath.of("/repo")).map { it.name } shouldContainExactly
                listOf(".gitignore", "app", "docs", "gradlew")
        }

        "ファイルを list すると空になる" {
            fileSystem.list(FsPath.of("/repo/gradlew")) shouldContainExactly emptyList()
        }

        "存在しないディレクトリを list すると空になる" {
            fileSystem.list(FsPath.of("/repo/missing")) shouldContainExactly emptyList()
        }
    }

    "実ファイルシステムと偽のファイルシステムが同じ結果を返す" - {
        val root = createTemporaryDirectory()
        afterSpec { root.deleteRecursively() }

        buildRealTree(
            root,
            listOf(
                "gradlew",
                ".gitignore",
                "app/build.gradle.kts",
                "app/src/main/kotlin/Main.kt",
                "docs/README.md",
                "docs/stray.txt",
                // Names a locale aware collator would order differently from the natural
                // order of the string: `Zebra` sorts before `apple` here.
                "sorted/Zebra.txt",
                "sorted/apple.txt",
                "sorted/banana.txt",
            ),
        )
        val rootPath = FsPath.of(root.absolutePath)

        val real: KatachiFileSystem = RealFileSystem(File(root, "app"))
        val fake: KatachiFileSystem = fakeFileSystem(workingDirectory = (rootPath / "app").value) {
            rootPath.value {
                "gradlew"()
                ".gitignore"()
                "app" {
                    "build.gradle.kts"()
                    "src/main/kotlin" { "Main.kt"() }
                }
                "docs" {
                    "README.md"()
                    "stray.txt"()
                }
                "sorted" {
                    "Zebra.txt"()
                    "apple.txt"()
                    "banana.txt"()
                }
            }
        }

        "作業ディレクトリが一致する" {
            real.workingDirectory shouldBe fake.workingDirectory
        }

        "exists / isDirectory / list がすべて一致する" {
            val probes = listOf(
                rootPath,
                rootPath / "gradlew",
                rootPath / ".gitignore",
                rootPath / "app",
                rootPath / "app/src/main/kotlin",
                rootPath / "app/src/main/kotlin/Main.kt",
                rootPath / "docs",
                rootPath / "docs/README.md",
                rootPath / "sorted",
                rootPath / "missing",
                rootPath / "docs/missing.md",
            )
            for (probe in probes) {
                withClue(probe.value) {
                    real.exists(probe) shouldBe fake.exists(probe)
                    real.isDirectory(probe) shouldBe fake.isDirectory(probe)
                    real.list(probe) shouldContainExactly fake.list(probe)
                }
            }
        }

        "list の順序は大文字小文字を含めて文字列の自然順になる" {
            val expected = listOf("Zebra.txt", "apple.txt", "banana.txt")
            real.list(rootPath / "sorted").map { it.name } shouldContainExactly expected
            fake.list(rootPath / "sorted").map { it.name } shouldContainExactly expected
        }
    }

    "実ファイルシステムはシンボリックリンクをディレクトリとして扱わない" {
        val root = createTemporaryDirectory()
        try {
            File(root, "real").mkdirs()
            File(root, "real/File.kt").writeText("")
            java.nio.file.Files.createSymbolicLink(
                File(root, "link").toPath(),
                File(root, "real").toPath(),
            )

            val fileSystem = RealFileSystem(root)
            val link = FsPath.of(root.absolutePath) / "link"
            fileSystem.exists(link) shouldBe true
            fileSystem.isDirectory(link) shouldBe false
            fileSystem.list(link) shouldContainExactly emptyList()
        } finally {
            root.deleteRecursively()
        }
    }
})
