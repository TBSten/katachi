package me.tbsten.katachi.test.check

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.collections.shouldNotContain
import io.kotest.matchers.shouldBe
import me.tbsten.katachi.check.FileSelection
import me.tbsten.katachi.check.FsPath
import me.tbsten.katachi.check.GitTrackedFileSystem
import me.tbsten.katachi.check.KatachiGitUnavailableException
import me.tbsten.katachi.check.KatachiFileSystem
import me.tbsten.katachi.check.RealFileSystem
import me.tbsten.katachi.check.findProjectRoot
import me.tbsten.katachi.check.gitTrackedFileSystem
import me.tbsten.katachi.check.isInsideGitWorkTree

class GitTrackedFileSystemSpec : FreeSpec({
    "偽のファイルシステムに被せたフィルタ" - {
        val root = FsPath.of("/repo")
        val delegate = fakeFileSystem(workingDirectory = "/repo") {
            "/repo" {
                "gradlew"()
                ".DS_Store"()
                "build" { "libs" { "app.jar"() } }
                "app" {
                    "build.gradle.kts"()
                    "build" { "classes" { "Main.class"() } }
                    "src/main/kotlin" { "Main.kt"() }
                }
            }
        }
        val fileSystem = GitTrackedFileSystem(
            delegate = delegate,
            root = root,
            trackedPaths = listOf(
                "gradlew",
                "app/build.gradle.kts",
                "app/src/main/kotlin/Main.kt",
            ),
        )

        "git が返したファイルは見える" {
            fileSystem.exists(root / "gradlew") shouldBe true
            fileSystem.exists(root / "app/src/main/kotlin/Main.kt") shouldBe true
        }

        "git が返さなかったファイルは見えない" {
            fileSystem.exists(root / ".DS_Store") shouldBe false
            fileSystem.exists(root / "build/libs/app.jar") shouldBe false
        }

        "配下に見えるファイルが1つも無いディレクトリは見えない" {
            fileSystem.exists(root / "build") shouldBe false
            fileSystem.isDirectory(root / "build") shouldBe false
            fileSystem.exists(root / "app/build") shouldBe false
        }

        "配下に見えるファイルがあるディレクトリは見える" {
            fileSystem.isDirectory(root / "app") shouldBe true
            fileSystem.isDirectory(root / "app/src/main/kotlin") shouldBe true
        }

        "list は見えるものだけを名前順で返す" {
            fileSystem.list(root).map { it.name } shouldContainExactly listOf("app", "gradlew")
            fileSystem.list(root / "app").map { it.name } shouldContainExactly
                listOf("build.gradle.kts", "src")
        }

        "ルート自身は常に見える" {
            fileSystem.isDirectory(root) shouldBe true
        }

        "追跡ファイルの集合はルートで1回読まれるだけで、走査するファイル数によらない" {
            val tracked = CountingPaths(
                listOf("gradlew", "app/build.gradle.kts", "app/src/main/kotlin/Main.kt"),
            )
            val counted = GitTrackedFileSystem(delegate, root, tracked)
            // `git` runs while the set is being read, so reading it once is what "git runs
            // once" means from the outside. Walking every directory below the root must not
            // touch it again, however many files the walk visits.
            walkEverything(counted, root)
            tracked.reads shouldBe 1
        }

        "作業ディレクトリは委譲先のものをそのまま返す" {
            fileSystem.workingDirectory shouldBe delegate.workingDirectory
        }
    }

    "このリポジトリ自身に対して git フィルタが効く" - {
        val delegate = RealFileSystem()
        val projectRoot = findProjectRoot(delegate)
        val root = projectRoot.path

        "ルートが git リポジトリとして認識される" {
            projectRoot.isGitRepository shouldBe true
        }

        "追跡中のファイルは見える" {
            val fileSystem = gitTrackedFileSystem(delegate, root)
            fileSystem.exists(root / "settings.gradle.kts") shouldBe true
            fileSystem.exists(root / "katachi/build.gradle.kts") shouldBe true
            fileSystem.isDirectory(root / "katachi/src/main/kotlin") shouldBe true
        }

        "git が無視するものは見えない" {
            val fileSystem = gitTrackedFileSystem(delegate, root)
            val names = fileSystem.list(root).map { it.name }
            names shouldNotContain ".git"
            names shouldNotContain ".local"
            names shouldNotContain "build"
            names shouldNotContain "local.properties"
            names shouldContainExactly names.sorted()
            fileSystem.exists(root / ".local") shouldBe false
        }

        "フィルタなしなら同じツリーに無視されるものが見える" {
            delegate.exists(root / ".local") shouldBe true
        }
    }

    "FileSelection が挙動を決める" - {
        "wholeTree() は委譲先をそのまま使う" {
            val delegate = RealFileSystem()
            val projectRoot = findProjectRoot(delegate)
            val fileSystem = FileSelection.WholeTree.fileSystemFor(delegate, projectRoot)
            fileSystem.exists(projectRoot.path / ".local") shouldBe true
        }

        "git の work tree の外では gitTracked() が全走査に落ちる" {
            // The root exists only in the fake tree, so `git rev-parse` cannot run there and
            // the selection falls back instead of failing.
            val delegate = fakeFileSystem(workingDirectory = "/katachi-no-such-repo") {
                "/katachi-no-such-repo" {
                    "gradlew"()
                    "untracked.txt"()
                }
            }
            val projectRoot = findProjectRoot(delegate)

            val fileSystem = FileSelection.GitTracked.fileSystemFor(delegate, projectRoot)
            fileSystem.exists(FsPath.of("/katachi-no-such-repo/untracked.txt")) shouldBe true
        }

        ".git が直下に無くても、work tree の中なら gitTracked() が効く" {
            // The samples of this repository are exactly this shape: `sample/jvm` holds a
            // `gradlew` and is the project root, while `.git` sits two directories above.
            // Deciding by the presence of `.git` at the root would silently walk the whole
            // tree here, and `build/` would start showing up as violations.
            val delegate = RealFileSystem()
            val projectRoot = findProjectRoot(delegate)
            projectRoot.isGitRepository shouldBe true

            val sampleRoot = projectRoot.path / "sample" / "jvm"
            isInsideGitWorkTree(sampleRoot) shouldBe true

            val fileSystem = gitTrackedFileSystem(delegate, sampleRoot)
            fileSystem.exists(sampleRoot / "settings.gradle.kts") shouldBe true
            fileSystem.exists(sampleRoot / "build") shouldBe false
        }
    }
})

/**
 * The paths git reported, counting how often the collection is read.
 *
 * [GitTrackedFileSystem] takes the set once, in its constructor, and answers every later
 * question from what it kept. A read per traversed file would be a `git` process per file.
 */
private class CountingPaths(private val paths: List<String>) : Collection<String> by paths {
    var reads: Int = 0
        private set

    override fun iterator(): Iterator<String> {
        reads++
        return paths.iterator()
    }
}

/** Visits every directory and file below [root] through the four operations. */
private fun walkEverything(fileSystem: KatachiFileSystem, root: FsPath) {
    for (child in fileSystem.list(root)) {
        fileSystem.exists(child)
        if (fileSystem.isDirectory(child)) walkEverything(fileSystem, child)
    }
}
