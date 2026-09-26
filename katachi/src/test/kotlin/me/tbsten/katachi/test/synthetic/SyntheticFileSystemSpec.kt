package me.tbsten.katachi.test.synthetic

import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.ints.shouldBeGreaterThan
import io.kotest.matchers.shouldBe
import me.tbsten.katachi.check.internal.validate
import me.tbsten.katachi.dsl.files.FsPath
import me.tbsten.katachi.test.dsl.files.fakeFileSystem

class SyntheticFileSystemSpec : FreeSpec({
    "IndexedFileSystem" - {
        "どのディレクトリでも FakeFileSystem と同じ子を同じ順で返す" {
            val project = generateProject(files = 300, roles = 5, modules = 10, seed = 2)
            val paths = (project.files + ".git/HEAD").map { (project.root / it).value }
            val indexed = project.inMemoryFileSystem()
            val fake = fakeFileSystem(project.root.value) { paths.forEach { it() } }

            val directories = paths.flatMap { ancestorsOf(FsPath.of(it)) }.toSet()
            for (directory in directories) {
                indexed.list(directory) shouldBe fake.list(directory)
                indexed.isDirectory(directory) shouldBe true
            }
            for (file in paths.map { FsPath.of(it) }) {
                indexed.exists(file) shouldBe true
                indexed.isDirectory(file) shouldBe false
                indexed.list(file) shouldBe emptyList()
            }
            indexed.exists(project.root / "nowhere") shouldBe false
        }

        "作業ディレクトリは何も置かなくてもディレクトリになる" {
            val fileSystem = IndexedFileSystem.of("/repo/app", emptyList())

            fileSystem.isDirectory(FsPath.of("/repo/app")) shouldBe true
            fileSystem.list(FsPath.of("/repo")) shouldBe listOf(FsPath.of("/repo/app"))
        }
    }

    "CountingFileSystem" - {
        "操作ごと・パスごとに呼び出し回数を数える" {
            val fileSystem = CountingFileSystem(IndexedFileSystem.of("/repo", listOf("/repo/a/B.kt")))

            fileSystem.list(FsPath.of("/repo"))
            fileSystem.list(FsPath.of("/repo"))
            fileSystem.isDirectory(FsPath.of("/repo/a"))
            fileSystem.exists(FsPath.of("/repo/a/B.kt")) shouldBe true

            fileSystem.listCalls shouldBe mapOf(FsPath.of("/repo") to 2)
            fileSystem.isDirectoryCalls shouldBe mapOf(FsPath.of("/repo/a") to 1)
            fileSystem.existsCalls shouldBe mapOf(FsPath.of("/repo/a/B.kt") to 1)
            fileSystem.totalCalls shouldBe 4
        }

        "reset で数え直せる" {
            val fileSystem = CountingFileSystem(IndexedFileSystem.of("/repo", emptyList()))
            fileSystem.list(FsPath.of("/repo"))

            fileSystem.reset()

            fileSystem.totalCalls shouldBe 0
        }

        "validate に渡すと走査の呼び出しが数えられ、答えは変わらない" {
            val project = generateProject(files = 1_000, roles = 5, modules = 10, seed = 4)
            val fileSystem = CountingFileSystem(project.inMemoryFileSystem())

            val labels = project.architecture().validate(fileSystem).map { "[${it.label}] ${it.path}" }

            labels.sorted() shouldBe project.expectedViolationLabels
            fileSystem.listCalls.getValue(project.root) shouldBeGreaterThan 0
        }
    }
})

private fun ancestorsOf(path: FsPath): List<FsPath> = generateSequence(path.parent) { it.parent }.toList()
