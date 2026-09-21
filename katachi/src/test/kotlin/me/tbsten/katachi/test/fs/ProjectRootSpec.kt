package me.tbsten.katachi.test.fs

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import me.tbsten.katachi.fs.FsPath
import me.tbsten.katachi.fs.KatachiProjectRootNotFoundException
import me.tbsten.katachi.fs.ProjectRootMarker
import me.tbsten.katachi.fs.RealFileSystem
import me.tbsten.katachi.fs.findProjectRoot

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
            root.markers shouldContainExactlyInAnyOrder listOf(ProjectRootMarker.Gradle)
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
        "gradle-wrapper.properties だけでも Gradle として認識する" {
            val fileSystem = fakeFileSystem(workingDirectory = "/repo") {
                "/repo" { "gradle/wrapper" { "gradle-wrapper.properties"() } }
            }

            findProjectRoot(fileSystem).markers shouldContainExactlyInAnyOrder listOf(ProjectRootMarker.Gradle)
        }

        "mvnw があれば Maven として認識する" {
            val fileSystem = fakeFileSystem(workingDirectory = "/repo") {
                "/repo" { "mvnw"() }
            }

            val root = findProjectRoot(fileSystem)
            root.markers shouldContainExactlyInAnyOrder listOf(ProjectRootMarker.Maven)
            root.isGitRepository shouldBe false
        }

        ".git/HEAD があれば Git として認識する" {
            val fileSystem = fakeFileSystem(workingDirectory = "/repo") {
                "/repo" { ".git" { "HEAD"() } }
            }

            val root = findProjectRoot(fileSystem)
            root.markers shouldContainExactlyInAnyOrder listOf(ProjectRootMarker.Git)
            root.isGitRepository shouldBe true
        }

        "同じディレクトリにある複数のマーカーをすべて返す" {
            val fileSystem = fakeFileSystem(workingDirectory = "/repo") {
                "/repo" {
                    "gradlew"()
                    ".git" { "config"() }
                }
            }

            val root = findProjectRoot(fileSystem)
            root.markers shouldContainExactlyInAnyOrder
                listOf(ProjectRootMarker.Gradle, ProjectRootMarker.Git)
            root.isGitRepository shouldBe true
        }
    }

    "マーカーが1つも見つからなければ例外になる" {
        val fileSystem = fakeFileSystem(workingDirectory = "/repo/app") {
            "/repo" { "app" { "Main.kt"() } }
        }

        val exception = shouldThrow<KatachiProjectRootNotFoundException> { findProjectRoot(fileSystem) }
        exception.message.shouldNotBeNull() shouldContain "/repo/app"
    }

    "このリポジトリ自身でもルートを特定できる" {
        // The working directory of a Gradle test task is the module directory, so the search
        // has to climb at least one level.
        val fileSystem = RealFileSystem()
        val root = findProjectRoot(fileSystem)
        fileSystem.exists(root.path / "settings.gradle.kts") shouldBe true
        (ProjectRootMarker.Gradle in root.markers) shouldBe true
    }
})
