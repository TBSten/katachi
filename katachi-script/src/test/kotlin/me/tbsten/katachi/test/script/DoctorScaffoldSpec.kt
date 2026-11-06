package me.tbsten.katachi.test.script

import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import java.nio.file.Path
import java.nio.file.attribute.PosixFilePermissions
import kotlin.io.path.createDirectories
import kotlin.io.path.createTempDirectory
import kotlin.io.path.readText
import kotlin.io.path.setPosixFilePermissions
import kotlin.io.path.writeText

/** `doctor` and a `scaffold` that writes (the dry run is in RunScriptSpec) (KATACHI-266). */
class DoctorScaffoldSpec : FreeSpec({
    "doctor" - {
        "そろっていれば版と Kotlin を出して 0" {
            val project = gradleProject(toolchain = 21)

            val run = runCapturing(project, listOf("doctor"), env = mapOf("PATH" to pathWithGit().toString()))

            run.status shouldBe 0
            run.out shouldContain "必要なものはそろっています（katachi "
            run.out shouldContain "Kotlin: 2.4.10"
            run.err shouldNotContain "git:"
        }

        "git が無ければ入れ方を添えて警告し、止めはしない" {
            val run = runCapturing(gradleProject(toolchain = 21), listOf("doctor"), env = mapOf("PATH" to ""))
            run.status shouldBe 0
            run.err shouldContain "次のコマンドが見つかりません。無くても進めますが、あったほうが確実です:\n    - git: macOS: xcode-select --install"
        }

        "Gradle を動かす JDK が古ければ止まる" {
            val run = runCapturing(gradleProject(toolchain = 11), listOf("doctor"), env = mapOf("PATH" to pathWithGit().toString()))
            run.status shouldBe 1
            run.err shouldContain "Gradle を動かす JDK が 11 です（gradle/gradle-daemon-jvm.properties の toolchainVersion）。"
        }

        "--lang en で英語にし、知らないオプションは止まる" {
            val project = gradleProject(toolchain = 21)
            runCapturing(project, listOf("doctor", "--lang", "en"), env = mapOf("PATH" to pathWithGit().toString())).out shouldContain
                "Everything this needs is available (katachi "
            runCapturing(project, listOf("doctor", "--fast")).err shouldContain "doctor: 知らないオプションです: --fast"
        }
    }

    "scaffold" - {
        "検査用モジュールを作り、settings に include を足す" {
            val project = gradleProject(toolchain = 21)

            val run = runCapturing(project, listOf("scaffold", "--package", "com.example", "--katachi", "0.4.0"), env = javaHome())

            run.status shouldBe 0
            run.out shouldContain "== 作成しました"
            run.out shouldContain "次: katachi test"
            val src = project.resolve("architecture-test/src/test/kotlin/com/example/test/architecture")
            src.resolve("ProjectArchitecture.kt").readText() shouldContain "package com.example.test.architecture"
            src.resolve("ProjectArchitectureTest.kt").readText() shouldContain "package com.example.test.architecture"
            project.resolve("architecture-test/build.gradle.kts").readText() shouldContain "0.4.0"
            project.resolve("settings.gradle.kts").readText() shouldContain "include(\":architecture-test\")"
        }

        "もう一度実行するには --force が要り、そのときも書きかけの ProjectArchitecture.kt は残して include も重ねない" {
            val project = gradleProject(toolchain = 21)
            val args = listOf("scaffold", "--package", "com.example", "--katachi", "0.4.0")
            runCapturing(project, args, env = javaHome()).status shouldBe 0
            val architecture = project.resolve("architecture-test/src/test/kotlin/com/example/test/architecture/ProjectArchitecture.kt")
            architecture.writeText("// written by hand\n")

            val refused = runCapturing(project, args, env = javaHome())
            refused.status shouldBe 1
            refused.err shouldContain "architecture-test/ がすでに存在します。上書きするなら --force を付けてください。"

            val again = runCapturing(project, args + "--force", env = javaHome())

            again.status shouldBe 0
            again.out shouldContain "すでにあるので残しました: "
            architecture.readText() shouldBe "// written by hand\n"
            Regex(Regex.escape("include(\":architecture-test\")")).findAll(project.resolve("settings.gradle.kts").readText()).count() shouldBe 1
        }

        "--module-dir の置き場所に作り、init の作業用ディレクトリに記録する" {
            val project = initializedProject()
            project.resolve("build.gradle.kts").writeText("plugins {\n    kotlin(\"jvm\") version \"2.4.10\" apply false\n}\n")

            val run = runCapturing(project, listOf("scaffold", "--package", "com.example", "--katachi", "0.4.0", "--module-dir", "static-analysis/katachi"), env = javaHome())

            run.status shouldBe 0
            project.resolve("static-analysis/katachi/build.gradle.kts").readText() shouldContain "0.4.0"
            project.resolve("settings.gradle.kts").readText() shouldContain "include(\":static-analysis:katachi\")"
            jsonBlockOf(project.installWorkdir.resolve("check-list.html"), "checklist")["meta"].toString() shouldContain
                "\"moduleDir\":\"static-analysis/katachi\""
        }
    }
})

/** A Gradle root with the Kotlin JVM plugin and the JDK [toolchain] pinned in gradle-daemon-jvm.properties. */
private fun gradleProject(toolchain: Int): Path {
    val project = createTempDirectory("katachi-project-")
    project.resolve("settings.gradle.kts").writeText("rootProject.name = \"p\"\n")
    project.resolve("build.gradle.kts").writeText("plugins {\n    kotlin(\"jvm\") version \"2.4.10\" apply false\n}\n")
    project.resolve("gradle").createDirectories().resolve("gradle-daemon-jvm.properties").writeText("toolchainVersion=$toolchain\n")
    return project
}

/** A PATH holding only a `git`, so that doctor's check for it does not depend on the machine. */
private fun pathWithGit(): Path {
    val bin = createTempDirectory("katachi-bin-")
    bin.resolve("git").writeText("#!/bin/sh\nexit 0\n")
    bin.resolve("git").setPosixFilePermissions(PosixFilePermissions.fromString("rwxr-xr-x"))
    return bin
}

private fun javaHome(): Map<String, String> = mapOf("JAVA_HOME" to System.getProperty("java.home"))
