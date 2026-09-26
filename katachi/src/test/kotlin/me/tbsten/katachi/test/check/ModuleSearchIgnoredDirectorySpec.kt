package me.tbsten.katachi.test.check

import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.shouldBe
import java.io.IOException
import me.tbsten.katachi.check.internal.validate
import me.tbsten.katachi.dsl.Architecture
import me.tbsten.katachi.dsl.FileSelection
import me.tbsten.katachi.dsl.architecture
import me.tbsten.katachi.dsl.files.FsPath
import me.tbsten.katachi.dsl.files.KatachiFileSystem
import me.tbsten.katachi.dsl.files.ProjectRoot
import me.tbsten.katachi.dsl.files.internal.GitTrackedFileSystem
import me.tbsten.katachi.dsl.gradle.kotlin
import me.tbsten.katachi.dsl.gradle.mainSourceSet
import me.tbsten.katachi.dsl.gradle.module
import me.tbsten.katachi.dsl.kotlin.ktFile
import me.tbsten.katachi.dsl.kotlin.ktsFile
import me.tbsten.katachi.dsl.LayoutScope
import me.tbsten.katachi.test.dsl.files.failingToListAt

/**
 * The search for the modules skipping what git leaves out, under `files = gitTracked()`.
 *
 * The search runs on the unfiltered tree on purpose — a module whose build file the selection
 * leaves out is still a module, and a module nobody has `git add`ed yet is still a module. What
 * it can skip is a directory git reports **nothing** below: `node_modules/`, `docs/dist/`. The
 * walk under `gitTracked()` never enters such a directory either, so no module found there
 * could ever have had a file checked.
 *
 * The selection here is [GitTrackedFileSystem] over a fake with a fixed list, standing in for
 * what `git ls-files --cached --others --exclude-standard` would print.
 */
class ModuleSearchIgnoredDirectorySpec : FreeSpec({
    "git が何も報告しないディレクトリ" - {
        val tree = repositoryOf {
            "settings.gradle.kts"()
            "build.gradle.kts"()
            "app" { "build.gradle.kts"() }
            "dist" { "build.gradle.kts"(); "assets" { "deep" { "index.js"() } } }
            "node_modules" { "pkg" { "android" { "build.gradle"() } } }
        }
        val reported = listOf("settings.gradle.kts", "build.gradle.kts", "app/build.gradle.kts")

        "モジュール探索でも中を list しない" {
            val recording = RecordingFileSystem(tree)

            gitReporting(reported) {
                ":".module { "settings.gradle".ktsFile() }
                ":*".module { }
            }.validate(recording)

            recording.listed.filter { it.startsWith("/repo/dist") || it.startsWith("/repo/node_modules") }
                .shouldBeEmpty()
        }

        "中のモジュールはワイルドカードに展開されない" {
            // Before, `:dist` was found and its build file — which the walk never sees — came
            // back as missing although it is sitting right there on disk.
            gitReporting(reported) {
                ":".module { "settings.gradle".ktsFile() }
                ":*".module { }
            }.validate(tree).labels().shouldBeEmpty()
        }

        "中の読めないディレクトリは検査できなかったと報告しない" {
            gitReporting(reported) {
                ":".module { "settings.gradle".ktsFile() }
                ":*".module { }
            }.validate(tree.failingToListAt("/repo/node_modules/pkg") { IOException("cannot list it") })
                .labels().shouldBeEmpty()
        }
    }

    "git が報告するものは今までどおり探索する" - {
        "まだ add していない新しいモジュールも見つかり、中の宣言外のファイルは Unexpected になる" {
            val tree = repositoryOf {
                "settings.gradle.kts"()
                "build.gradle.kts"()
                "feature" { "home" { "build.gradle.kts"(); "Stray.kt"() } }
            }

            gitReporting(
                // `feature/home/*` are what `--others` adds: not tracked yet, not ignored.
                listOf("settings.gradle.kts", "build.gradle.kts", "feature/home/build.gradle.kts", "feature/home/Stray.kt"),
            ) {
                ":".module { "settings.gradle".ktsFile() }
                ":feature:*".module { }
            }.validate(tree).labels() shouldBe listOf("[UnexpectedFile] feature/home/Stray.kt")
        }

        "build.gradle.kts だけが報告されないモジュールも見つかる" {
            val tree = repositoryOf {
                "settings.gradle.kts"()
                "build.gradle.kts"()
                "feature" { "home" { "build.gradle.kts"(); "src/main/kotlin" { "HomeScreen.kt"() } } }
            }

            gitReporting(
                listOf("settings.gradle.kts", "build.gradle.kts", "feature/home/src/main/kotlin/HomeScreen.kt"),
            ) {
                ":".module { "settings.gradle".ktsFile() }
                ":feature:*".module { mainSourceSet / kotlin / "HomeScreen".ktFile() }
            }.validate(tree).labels() shouldBe listOf("[MissingFile] feature/home/build.gradle.kts")
        }

        "1つのパスとして報告されるディレクトリ（サブモジュール、入れ子のリポジトリ）の中も探索する" {
            val tree = repositoryOf {
                "settings.gradle.kts"()
                "build.gradle.kts"()
                "libs" { "vendored" { "core" { "build.gradle.kts"() } } }
            }
            val recording = RecordingFileSystem(tree)

            gitReporting(listOf("settings.gradle.kts", "build.gradle.kts", "libs/vendored")) {
                ":".module { "settings.gradle".ktsFile() }
            }.validate(recording)

            recording.listed shouldContain "/repo/libs/vendored"
        }
    }

    "wholeTree() では何も飛ばさない" {
        val tree = repositoryOf {
            "settings.gradle.kts"()
            "build.gradle.kts"()
            "node_modules" { "pkg" { "android" { "build.gradle"() } } }
        }
        val recording = RecordingFileSystem(tree)

        layoutArchitecture { ":".module { "settings.gradle".ktsFile() } }.validate(recording)

        recording.listed shouldContain "/repo/node_modules/pkg/android"
    }
})

/** An architecture whose `files` shows only [reported], the way `gitTracked()` does. */
private fun gitReporting(reported: List<String>, block: LayoutScope.() -> Unit): Architecture = architecture {
    files = object : FileSelection {
        override fun fileSystemFor(delegate: KatachiFileSystem, projectRoot: ProjectRoot): KatachiFileSystem =
            GitTrackedFileSystem(delegate, projectRoot.path, reported)
    }
    "app".group { "Role" { layout(block) } }
}

/** [delegate], remembering every directory it was asked to list. */
private class RecordingFileSystem(private val delegate: KatachiFileSystem) : KatachiFileSystem by delegate {
    val listed = mutableListOf<String>()

    override fun list(directory: FsPath): List<FsPath> {
        listed += directory.value
        return delegate.list(directory)
    }
}
