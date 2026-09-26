package me.tbsten.katachi.test.dsl.files

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import me.tbsten.katachi.dsl.KatachiProjectRootNotFoundException
import me.tbsten.katachi.dsl.files.FsPath
import me.tbsten.katachi.dsl.files.internal.RealFileSystem
import me.tbsten.katachi.dsl.files.internal.findProjectRoot

class ProjectRootSpec : FreeSpec({
    "作業ディレクトリから上に辿ってルートを特定する" - {
        "gradlew のあるディレクトリがルートになる" {
            val fileSystem = fakeFileSystem(workingDirectory = "/repo/app/src/test") {
                "/repo" {
                    "gradlew"()
                    "app" { "src/test" { "Nothing.kt"() } }
                }
            }

            val root = findProjectRoot(fileSystem)
            root.path shouldBe FsPath.of("/repo")
        }

        "作業ディレクトリ自体がルートでもよい" {
            val fileSystem = fakeFileSystem(workingDirectory = "/repo") {
                "/repo" { "gradlew"() }
            }

            findProjectRoot(fileSystem).path shouldBe FsPath.of("/repo")
        }

        "一番近いマーカーが勝つ" {
            val fileSystem = fakeFileSystem(workingDirectory = "/outer/inner/app") {
                "/outer" {
                    "gradlew"()
                    "inner" {
                        "gradlew"()
                        "app" { "Nothing.kt"() }
                    }
                }
            }

            findProjectRoot(fileSystem).path shouldBe FsPath.of("/outer/inner")
        }
    }

    "マーカーの種類" - {
        "gradle-wrapper.properties だけでもルートになる" {
            val fileSystem = fakeFileSystem(workingDirectory = "/repo/app") {
                "/repo" {
                    "gradle/wrapper" { "gradle-wrapper.properties"() }
                    "app" { "Main.kt"() }
                }
            }

            findProjectRoot(fileSystem).path shouldBe FsPath.of("/repo")
        }

        "mvnw があればルートになる" {
            val fileSystem = fakeFileSystem(workingDirectory = "/repo/app") {
                "/repo" {
                    "mvnw"()
                    "app" { "Main.kt"() }
                }
            }

            findProjectRoot(fileSystem).path shouldBe FsPath.of("/repo")
        }

        ".git/HEAD があればルートになる" {
            val fileSystem = fakeFileSystem(workingDirectory = "/repo/app") {
                "/repo" {
                    ".git" { "HEAD"() }
                    "app" { "Main.kt"() }
                }
            }

            findProjectRoot(fileSystem).path shouldBe FsPath.of("/repo")
        }

        ".git がディレクトリではなくファイルでもルートになる" {
            // worktree と submodule の `.git` は `gitdir: <path>` の1行が入ったファイルで、
            // `.git/config` も `.git/HEAD` も `.git/refs` も存在しない。ここで止まらないと、
            // worktree で作業している利用者（と subagent）のルートが上にずれる。
            val fileSystem = fakeFileSystem(workingDirectory = "/worktree/app") {
                "/worktree" {
                    ".git"()
                    "app" { "Main.kt"() }
                }
            }

            findProjectRoot(fileSystem).path shouldBe FsPath.of("/worktree")
        }
    }

    "マーカーが1つも見つからなければ例外になる" {
        val fileSystem = fakeFileSystem(workingDirectory = "/repo/app") {
            "/repo" { "app" { "Main.kt"() } }
        }

        val exception = shouldThrow<KatachiProjectRootNotFoundException> { findProjectRoot(fileSystem) }
        exception.message.shouldNotBeNull() shouldContain "above file:///repo/app."
    }

    "このリポジトリ自身でもルートを特定できる" {
        // The working directory of a Gradle test task is the module directory, so the search
        // has to climb at least one level.
        val fileSystem = RealFileSystem()
        val root = findProjectRoot(fileSystem)
        fileSystem.exists(root.path / "settings.gradle.kts") shouldBe true
        fileSystem.exists(root.path / "gradlew") shouldBe true
    }
})
