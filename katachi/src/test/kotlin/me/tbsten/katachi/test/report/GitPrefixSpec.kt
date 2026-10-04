package me.tbsten.katachi.test.report

import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import java.io.File
import java.nio.file.Files
import me.tbsten.katachi.dsl.files.FsPath
import me.tbsten.katachi.report.internal.gitShowPrefix

/**
 * `git rev-parse --show-prefix` against this repository itself, the way
 * `GitTrackedFileSystemSpec` asks git about it: under Gradle the working directory is the
 * `katachi` module, one level below the repository root.
 */
class GitPrefixSpec : FreeSpec({
    "このリポジトリ自身に対して" - {
        "モジュールのディレクトリでは末尾に / の付いた接頭辞が返る" {
            val module = File("").absoluteFile
            val expected = module.name + "/"
            gitShowPrefix(FsPath.of(module.path)) shouldBe expected
        }

        "リポジトリのルートでは空文字が返る" {
            val root = File("").absoluteFile.parentFile
            gitShowPrefix(FsPath.of(root.path)) shouldBe ""
        }
    }

    "git の work tree の外では null" {
        val outside = Files.createTempDirectory("katachi-no-git").toFile()
        try {
            gitShowPrefix(FsPath.of(outside.path)).shouldBeNull()
        } finally {
            outside.deleteRecursively()
        }
    }

    "存在しないディレクトリでは null" {
        gitShowPrefix(FsPath.of("/nonexistent/katachi/dir")).shouldBeNull()
    }
})
