package me.tbsten.katachi.test.dsl.files

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.collections.shouldNotBeEmpty
import io.kotest.matchers.collections.shouldNotContain
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldStartWith
import io.kotest.matchers.types.shouldBeInstanceOf
import io.kotest.matchers.types.shouldBeSameInstanceAs
import java.io.File
import java.io.IOException
import java.nio.file.Files
import me.tbsten.katachi.check.validate
import me.tbsten.katachi.dsl.FileSelection
import me.tbsten.katachi.dsl.KatachiGitUnavailableException
import me.tbsten.katachi.dsl.architecture
import me.tbsten.katachi.dsl.files.FsPath
import me.tbsten.katachi.dsl.files.KatachiFileSystem
import me.tbsten.katachi.dsl.files.internal.GitProblem
import me.tbsten.katachi.dsl.files.internal.GitTrackedFileSystem
import me.tbsten.katachi.dsl.files.internal.RealFileSystem
import me.tbsten.katachi.dsl.files.internal.findProjectRoot
import me.tbsten.katachi.dsl.files.internal.gitTrackedFileSystem
import me.tbsten.katachi.dsl.files.internal.dotGitAtOrAbove
import me.tbsten.katachi.dsl.files.internal.isInsideGitWorkTree
import me.tbsten.katachi.dsl.files.internal.namesBeyond
import me.tbsten.katachi.dsl.files.internal.withGitWorkTree
import me.tbsten.katachi.internal.fileUri

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

    "見えるディレクトリの集め方" - {
        val root = FsPath.of("/repo")
        val tracked = listOf(
            "a/b/c/d/Deep.kt",
            "a/b/Shallow.kt",
            "a/e/f/Other.kt",
            "a/b/c/g/Sibling.kt",
            "Top.kt",
        )
        val expectedDirectories = setOf("a", "a/b", "a/b/c", "a/b/c/d", "a/b/c/g", "a/e", "a/e/f")
        // Every ancestor of a tracked file, plus the directories next to them that hold nothing.
        val probes = expectedDirectories + setOf("a/b/c/d/x", "a/b/h", "a/e/f/g", "b", "a/e/b")

        "追跡ファイルがどの順で並んでいても、祖先ディレクトリはすべて見え、それ以外は見えない" {
            for (order in permutations(tracked)) {
                val fileSystem = GitTrackedFileSystem(EveryPathIsADirectory(root), root, order)
                probes.filter { fileSystem.isDirectory(root / it) }.toSet() shouldBe expectedDirectories
            }
        }

        "同じパスが何度報告されても結果は変わらない" {
            val fileSystem = GitTrackedFileSystem(EveryPathIsADirectory(root), root, tracked + tracked.reversed())
            probes.filter { fileSystem.isDirectory(root / it) }.toSet() shouldBe expectedDirectories
        }

        "ルートの外を指すパスはルートの中のディレクトリを増やさない" {
            val fileSystem = GitTrackedFileSystem(
                EveryPathIsADirectory(root),
                root,
                listOf("../outside/deep/File.kt", "a/File.kt"),
            )
            probes.filter { fileSystem.isDirectory(root / it) }.toSet() shouldBe setOf("a")
        }
    }

    "git とファイルシステムで名前の Unicode 正規化形が違っても、同じ名前は同じファイルとして見える（KATACHI-433）" - {
        val root = FsPath.of("/repo")
        // ビュー and ゴミ箱 in NFC, as macOS's JDK lists them, and in NFD (a voiced kana as its plain kana and U+3099),
        // as git there prints a name Finder or a Mac zip wrote unless core.precomposeunicode is true.
        val nfc = NameSpelling(view = "\u30D3\u30E5\u30FC", trash = "\u30B4\u30DF\u7BB1")
        val nfd = NameSpelling(view = "\u30D2\u3099\u30E5\u30FC", trash = "\u30B3\u3099\u30DF\u7BB1")

        "前提: NFD の綴りは NFC の綴りと別の文字列で、NFC にそろえると同じ名前になる" {
            for ((decomposed, composed) in listOf(nfd.view to nfc.view, nfd.trash to nfc.trash)) {
                (decomposed == composed) shouldBe false
                java.text.Normalizer.normalize(decomposed, java.text.Normalizer.Form.NFC) shouldBe composed
            }
        }

        for ((shape, listed, printed) in listOf(
            Triple("ファイルシステムは NFC、git は NFD（core.precomposeunicode が true でない macOS の形）", nfc, nfd),
            Triple("ファイルシステムは NFD、git は NFC", nfd, nfc),
        )) {
            shape - {
                val delegate = fakeFileSystem(workingDirectory = "/repo") {
                    "/repo" {
                        "gradlew"()
                        listed.view {
                            ".DS_Store"()
                            "Main.kt"()
                            "${listed.trash}.kt"()
                        }
                        "app" {
                            "${listed.trash}.kt"()
                            "build" { "classes" { "Main.class"() } }
                        }
                    }
                }
                val fileSystem = GitTrackedFileSystem(
                    delegate = delegate,
                    root = root,
                    trackedPaths = listOf(
                        "gradlew",
                        "${printed.view}/Main.kt",
                        "${printed.view}/${printed.trash}.kt",
                        "app/${printed.trash}.kt",
                    ),
                )

                "list は git が返したものを、ファイルシステムが返した綴りのまま返す" {
                    fileSystem.list(root).map { it.name } shouldContainExactly listOf("app", "gradlew", listed.view)
                    fileSystem.list(root / listed.view).map { it.name } shouldContainExactly
                        listOf("Main.kt", "${listed.trash}.kt")
                    fileSystem.list(root / "app").map { it.name } shouldContainExactly listOf("${listed.trash}.kt")
                }

                "日本語のディレクトリと、その下の ASCII 名・日本語名のファイルが見える" {
                    fileSystem.isDirectory(root / listed.view) shouldBe true
                    fileSystem.exists(root / listed.view / "Main.kt") shouldBe true
                    fileSystem.exists(root / listed.view / "${listed.trash}.kt") shouldBe true
                    fileSystem.holdsNothingOfTheProject(root / listed.view) shouldBe false
                }

                "ASCII のディレクトリの下の日本語名のファイルが見える" {
                    fileSystem.isDirectory(root / "app") shouldBe true
                    fileSystem.exists(root / "app" / "${listed.trash}.kt") shouldBe true
                }

                "git が返さなかったものは、綴りをそろえても見えないまま" {
                    fileSystem.exists(root / listed.view / ".DS_Store") shouldBe false
                    fileSystem.exists(root / "app/build") shouldBe false
                    fileSystem.isDirectory(root / "app/build") shouldBe false
                    fileSystem.holdsNothingOfTheProject(root / "app/build") shouldBe true
                }
            }
        }

        "ルートのパスに NFD の名前があり、その下が NFC で並んでも、git が返したものは見える" {
            // The root is spelled as the working directory is, not as a listing is, so it need not be NFC like
            // the names below it.
            val nfdRoot = FsPath.of("/${nfd.view}")
            val delegate = fakeFileSystem(workingDirectory = nfdRoot.value) {
                nfdRoot.value {
                    ".DS_Store"()
                    "app" { "${nfc.trash}.kt"() }
                }
            }
            val fileSystem = GitTrackedFileSystem(delegate, nfdRoot, listOf("app/${nfd.trash}.kt"))

            fileSystem.list(nfdRoot).map { it.name } shouldContainExactly listOf("app")
            fileSystem.isDirectory(nfdRoot / "app") shouldBe true
            fileSystem.list(nfdRoot / "app").map { it.name } shouldContainExactly listOf("${nfc.trash}.kt")
        }
    }

    "ファイル名の文字コードが日本語を持てないとき (KATACHI-442)" - {
        "US-ASCII なら ASCII でない名前だけを挙げる" {
            namesBeyond("ANSI_X3.4-1968", listOf("a/Lib.kt", "a/日本語.kt", "b/ok.txt")) shouldContainExactly
                listOf("a/日本語.kt")
        }

        "UTF-8 なら何も挙げない" {
            namesBeyond("UTF-8", listOf("a/日本語.kt")) shouldBe emptyList()
        }

        "JDK が知らない文字コードは判断せず何も挙げない" {
            namesBeyond("no-such-encoding", listOf("a/日本語.kt")) shouldBe emptyList()
            namesBeyond(null, listOf("a/日本語.kt")) shouldBe emptyList()
        }

        "黙って落とさず、LC_ALL=C.UTF-8 を案内して止まる" {
            val problem = GitProblem.UnreadableNames("ANSI_X3.4-1968", listOf("a/日本語.kt"))
            val message = problem.explain("git ls-files", FsPath.of("/work/app"))
            message shouldContain "ANSI_X3.4-1968"
            message shouldContain "a/日本語.kt"
            message shouldContain "LC_ALL=C.UTF-8"
        }
    }

    "このリポジトリ自身に対して git フィルタが効く" - {
        val delegate = RealFileSystem()
        val projectRoot = findProjectRoot(delegate)
        val root = projectRoot.path

        "ルートが git の work tree の中にあると判定される" {
            isInsideGitWorkTree(root) shouldBe true
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

        "ignore 対象の中で追跡しているファイルは見える" {
            // This repository's `.gitignore` says `.idea/*` and then takes `dictionaries/project.xml`
            // back. `--cached` reports it, so the filter shows it — and nothing else below `.idea`.
            val fileSystem = gitTrackedFileSystem(delegate, root)
            fileSystem.exists(root / ".idea/dictionaries/project.xml") shouldBe true
            fileSystem.list(root / ".idea").map { it.name } shouldContainExactly listOf("dictionaries")
        }

        "フィルタなしなら同じツリーに無視されるものが見える" {
            // `.git` であって `.local` ではない。`.local/` は .gitignore 済みで**コミットされない**
            // ので、clone したてのチェックアウトや新しい worktree には存在しない。実際にここは
            // `:katachi-konsist:test` が fixture 用に `.local/tmp/` を作るかどうか、つまり
            // どちらのモジュールのテストが先に走ったかで結果が変わっていた。
            // `.git` なら必ずある（worktree ではディレクトリではなくファイルだが、どちらでも
            // `exists` は true）。すぐ上のケースがフィルタ越しには見えないことを pin している。
            delegate.exists(root / ".git") shouldBe true
        }
    }

    "FileSelection が挙動を決める" - {
        "wholeTree() は委譲先をそのまま使う" {
            val delegate = RealFileSystem()
            val projectRoot = findProjectRoot(delegate)
            val fileSystem = FileSelection.WholeTree.fileSystemFor(delegate, projectRoot)
            // 上と同じ理由で `.git`。`.local/` の有無はテストの実行順で変わる。
            fileSystem.exists(projectRoot.path / ".git") shouldBe true
        }

        "git の work tree の外では gitTracked() が全走査に落ちる" {
            // The root exists only in the fake tree, so there is no repository on the disk for
            // git to answer about, and the selection falls back instead of failing.
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

            val sampleRoot = projectRoot.path / "sample" / "jvm"
            isInsideGitWorkTree(sampleRoot) shouldBe true

            val fileSystem = gitTrackedFileSystem(delegate, sampleRoot)
            fileSystem.exists(sampleRoot / "settings.gradle.kts") shouldBe true
            fileSystem.exists(sampleRoot / "build") shouldBe false
        }
    }

    "GIT_CEILING_DIRECTORIES で git が上へ探さない場所では、その上の .git を「リポジトリがある」とみなさない（KATACHI-440）" - {
        "天井に指した場所とその上の .git は数えず、根そのものの .git は数える" {
            withTemporaryDirectory { directory ->
                val outer = File(directory, "outer").also { File(it, ".git").mkdirs() }
                val project = File(outer, "project").also { it.mkdirs() }

                dotGitAtOrAbove(FsPath.of(project.path), "") shouldBe FsPath.of("${outer.path}/.git")
                dotGitAtOrAbove(FsPath.of(project.path), outer.path) shouldBe null
                dotGitAtOrAbove(FsPath.of(project.path), "/nonexistent:${outer.path}") shouldBe null
                File(project, ".git").mkdirs()
                dotGitAtOrAbove(FsPath.of(project.path), outer.path) shouldBe FsPath.of("${project.path}/.git")
            }
        }
    }

    "根やその上に .git があるのに git が答えられなければ、全走査に落ちずに止まる（KATACHI-422）" - {
        "git が dubious ownership で断ると、git の文面を添えて止まる" {
            withTemporaryDirectory { directory ->
                val project = File(directory, "project").also { File(it, ".git").mkdirs() }
                val git = fakeGit(directory, exitCode = 128, stderr = dubiousOwnership(project))

                val failure = shouldThrow<KatachiGitUnavailableException> {
                    isInsideGitWorkTree(FsPath.of(project.path), git)
                }

                failure.command shouldBe "$git rev-parse --is-inside-work-tree"
                failure.problem.shouldBeInstanceOf<GitProblem.Failed>().dotGit shouldBe FsPath.of("${project.path}/.git")
                val message = failure.message.orEmpty()
                message.lines().first() shouldBe "`$git rev-parse --is-inside-work-tree` in ${fileUri(project.path)} " +
                    "exited with 128, although ${fileUri("${project.path}/.git")} exists."
                message shouldContain "git said:\n  fatal: detected dubious ownership in repository at '${project.path}'\n"
                message shouldContain "\n  \tgit config --global --add safe.directory ${project.path}\n"
                message.lines().last() shouldBe "Fix what git reports, $WHOLE_TREE_FIX"
            }
        }

        "根より上の .git でも止まり、その .git を指す" {
            withTemporaryDirectory { directory ->
                val repository = File(directory, "repository").also { File(it, ".git").mkdirs() }
                val project = File(repository, "app").also { it.mkdirs() }
                val git = fakeGit(directory, exitCode = 128, stderr = dubiousOwnership(repository))

                val failure = shouldThrow<KatachiGitUnavailableException> {
                    isInsideGitWorkTree(FsPath.of(project.path), git)
                }

                failure.problem.shouldBeInstanceOf<GitProblem.Failed>().dotGit shouldBe
                    FsPath.of("${repository.path}/.git")
            }
        }

        "git を起動できなければ、git を入れるか wholeTree() にするよう言って止まる" {
            withTemporaryDirectory { directory ->
                val project = File(directory, "project").also { File(it, ".git").mkdirs() }
                val git = File(directory, "no-such-git").path

                val failure = shouldThrow<KatachiGitUnavailableException> {
                    isInsideGitWorkTree(FsPath.of(project.path), git)
                }

                failure.problem.shouldBeInstanceOf<GitProblem.CannotStart>().dotGit shouldBe
                    FsPath.of("${project.path}/.git")
                failure.cause.shouldBeInstanceOf<IOException>()
                failure.message.orEmpty().lines().last() shouldBe
                    "Install git, or put it on the PATH the check runs with; $WHOLE_TREE_FIX"
            }
        }

        "worktree の .git が指す先が消えていれば、本物の git の文面を添えて validate() が止まる" {
            withTemporaryDirectory { directory ->
                val project = File(directory, "feature").also { it.mkdirs() }
                val missing = File(directory, "moved-away/.git/worktrees/feature")
                File(project, ".git").writeText("gitdir: ${missing.path}\n")

                // git の文面は版で違いうる（手元の git 2.43 の文面で書いた期待が、CI の git 2.56 では一致しなかった）ので、
                // 同じ git に同じ場所で聞いた答えが、そのまま字下げして報告に入っていることを見る
                val gitSaid = gitStderr(project, "rev-parse", "--is-inside-work-tree")

                val failure = shouldThrow<KatachiGitUnavailableException> {
                    architecture { }.validate(RealFileSystem(project))
                }

                gitSaid.shouldNotBeEmpty()
                gitSaid.forEach { line -> failure.message shouldContain "  $line" }
            }
        }

        "どこにも .git が無ければ、git が失敗しても起動できなくても全走査のまま" {
            withTemporaryDirectory { directory ->
                val project = File(directory, "project").also { it.mkdirs() }
                File(project, "gradlew").writeText("")
                val root = FsPath.of(project.path)
                val delegate = RealFileSystem(project)

                FileSelection.GitTracked.fileSystemFor(delegate, findProjectRoot(delegate)) shouldBeSameInstanceAs delegate
                isInsideGitWorkTree(root, fakeGit(directory, exitCode = 128, stderr = dubiousOwnership(project))) shouldBe false
                isInsideGitWorkTree(root, File(directory, "no-such-git").path) shouldBe false
            }
        }
    }

    "git が答えられなかったときの文面" - {
        "実行した場所は file URI で出る" {
            val root = FsPath.of("/repo")
            GitProblem.CannotStart().explain("git ls-files", root) shouldStartWith
                "Cannot run `git ls-files` in file:///repo. "
            GitProblem.TimedOut(60).explain("git ls-files", root) shouldBe
                "`git ls-files` in file:///repo did not finish within 60 seconds."
            GitProblem.Failed(128, "fatal").explain("git ls-files", root) shouldBe
                "`git ls-files` in file:///repo exited with 128: fatal"
        }

        "根の上に .git があって git を起動できないときは、.git の場所と直し方を言う" {
            GitProblem.CannotStart(FsPath.of("/repo/.git"))
                .explain("git rev-parse --is-inside-work-tree", FsPath.of("/repo/app")) shouldBe """
                    Cannot run `git rev-parse --is-inside-work-tree` in file:///repo/app , although file:///repo/.git exists.
                    $GIT_REQUIRED
                    Install git, or put it on the PATH the check runs with; $WHOLE_TREE_FIX
                """.trimIndent()
        }

        "git が .git を断ったときは、git の出力を字下げして添える" {
            GitProblem.Failed(128, "fatal: not a git repository: /moved/.git/worktrees/app", FsPath.of("/repo/.git"))
                .explain("git rev-parse --is-inside-work-tree", FsPath.of("/repo")) shouldBe """
                    `git rev-parse --is-inside-work-tree` in file:///repo exited with 128, although file:///repo/.git exists.
                    git said:
                      fatal: not a git repository: /moved/.git/worktrees/app
                    $GIT_REQUIRED
                    Fix what git reports, $WHOLE_TREE_FIX
                """.trimIndent()
        }

        "git が何も言わずに失敗したときは、自分で走らせて確かめるよう言う" {
            GitProblem.Failed(1, "", FsPath.of("/repo/.git"))
                .explain("git rev-parse --is-inside-work-tree", FsPath.of("/repo"))
                .lines().drop(1) shouldBe listOf(
                GIT_REQUIRED,
                "Run `git rev-parse --is-inside-work-tree` there to see why, $WHOLE_TREE_FIX",
            )
        }
    }
    "git のフックの GIT_DIR（linked worktree の gitdir）から、作業ツリーを GIT_WORK_TREE に足す" {
        val base = Files.createTempDirectory("katachi-hook").toFile()
        try {
            val gitDir = File(base, "main/.git/worktrees/wt").apply { mkdirs() }
            File(gitDir, "gitdir").writeText("${base.path}/wt/.git\n")
            val builder = ProcessBuilder("true")
            builder.environment()["GIT_DIR"] = gitDir.path
            builder.environment().remove("GIT_WORK_TREE")

            builder.withGitWorkTree().environment()["GIT_WORK_TREE"] shouldBe "${base.path}/wt"

            builder.environment()["GIT_WORK_TREE"] = "/elsewhere"
            builder.withGitWorkTree().environment()["GIT_WORK_TREE"] shouldBe "/elsewhere"

            // The main work tree's .git has no `gitdir` file: nothing to add.
            val plain = ProcessBuilder("true")
            plain.environment()["GIT_DIR"] = File(base, "main/.git").path
            plain.environment().remove("GIT_WORK_TREE")
            plain.withGitWorkTree().environment()["GIT_WORK_TREE"] shouldBe null
        } finally {
            base.deleteRecursively()
        }
    }

})

private const val GIT_REQUIRED: String =
    "With a .git at the project root or above it, katachi takes the project's files from git, and does not " +
        "fall back to every file on disk: that would check build output and ignored files too, and give other " +
        "results than a run where git works."

private const val WHOLE_TREE_FIX: String =
    "or set `files = wholeTree()` in `architecture { }` to check every file on disk instead."

/** What git prints for a repository whose owner is not the user running it (dubious ownership). */
private fun dubiousOwnership(repository: File): List<String> = listOf(
    "fatal: detected dubious ownership in repository at '${repository.path}'",
    "To add an exception for this directory, call:",
    "",
    "\tgit config --global --add safe.directory ${repository.path}",
)

/** A `git` in [directory] that prints [stderr] to standard error and exits with [exitCode], whatever it is asked. */
private fun fakeGit(directory: File, exitCode: Int, stderr: List<String>): String {
    val script = File(directory, "bin/git")
    script.parentFile.mkdirs()
    script.writeText(
        buildString {
            appendLine("#!/bin/sh")
            appendLine("cat >&2 <<'EOF'")
            stderr.forEach { appendLine(it) }
            appendLine("EOF")
            appendLine("exit $exitCode")
        },
    )
    script.setExecutable(true)
    return script.path
}

/** A fresh directory outside any git repository, removed afterwards. */
/** The non-blank lines that the real `git <args>` writes to stderr when run in [directory]. */
private fun gitStderr(directory: File, vararg args: String): List<String> {
    val process = ProcessBuilder(listOf("git", *args))
        .directory(directory)
        .redirectOutput(ProcessBuilder.Redirect.DISCARD)
        .start()
    val stderr = process.errorStream.bufferedReader().readText()
    process.waitFor()
    return stderr.lines().filter { it.isNotBlank() }
}

private inline fun withTemporaryDirectory(block: (File) -> Unit) {
    val directory = Files.createTempDirectory("katachi-git").toFile()
    try {
        block(directory)
    } finally {
        directory.deleteRecursively()
    }
}

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

/** A tree in which every path exists and is a directory, so only the filter decides. */
private class EveryPathIsADirectory(override val workingDirectory: FsPath) : KatachiFileSystem {
    override fun exists(path: FsPath): Boolean = true

    override fun isDirectory(path: FsPath): Boolean = true

    override fun list(directory: FsPath): List<FsPath> = emptyList()
}

/** Every ordering of [items]; only meant for the handful of paths a spec lists by hand. */
private fun <T> permutations(items: List<T>): List<List<T>> =
    if (items.size <= 1) {
        listOf(items)
    } else {
        items.indices.flatMap { index ->
            permutations(items.filterIndexed { other, _ -> other != index }).map { listOf(items[index]) + it }
        }
    }

/** The two Japanese names of the KATACHI-433 tree, both in one Unicode normalization form. */
private class NameSpelling(val view: String, val trash: String)
