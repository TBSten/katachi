package com.example

import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import java.io.File

/**
 * Checks that [projectArchitecture] builds into the model we expect.
 *
 * This is the sample's half of the step 1 acceptance criteria: katachi's own unit tests
 * prove the DSL works in isolation, and this proves it still works when the definition is
 * written by a user, in a user's build, against a katachi resolved through a composite
 * build.
 */
class ProjectArchitectureSpec : FreeSpec({
    "宣言した group がすべてモデルに含まれる" {
        projectArchitecture.allGroups.map { it.qualifiedName }.toSet() shouldBe setOf(
            "api",
            "domain",
            "data",
            "app",
            "build",
            "testing",
        )
    }

    "宣言した役割がすべて group のパス付きでモデルに含まれる" {
        projectArchitecture.allRoles.map { it.qualifiedName }.toSet() shouldBe setOf(
            "api/Controller",
            "api/KtorPlugin",
            "domain/Service",
            "domain/Model",
            "data/Repository",
            "app/Entrypoint",
            "app/ServerConfig",
            "build/Gradle",
            "build/Git",
            "testing/Test",
        )
    }

    "役割の宣言位置として ProjectArchitecture.kt の行番号が取れる" {
        // Guards the stack-trace based capture in a real user build: if the frame filter
        // ever starts skipping user code, this reports the test runner's file instead.
        val controller = projectArchitecture.allRoles.single { it.qualifiedName == "api/Controller" }
        controller.declaredAt.fileName shouldBe "ProjectArchitecture.kt"
        (controller.declaredAt.lineNumber > 0) shouldBe true
    }

    "group の宣言位置も ProjectArchitecture.kt である" {
        val api = projectArchitecture.allGroups.single { it.qualifiedName == "api" }
        api.declaredAt.fileName shouldBe "ProjectArchitecture.kt"
    }

    "捕捉した行番号の行に、その宣言が実際に書かれている" {
        // The two tests above only prove the file is the user's and the line is positive:
        // they stay green even if the frame filter picks the frame one level out, because
        // every declaration lives in the same file. This one reads the source back, so a
        // one-frame shift lands on `"api".group {` or on `architecture {` and fails. No
        // line number is hard-coded, so editing ProjectArchitecture.kt does not break it.
        val source = projectArchitectureSourceLines()

        projectArchitecture.allGroups.forEach { group ->
            group.declaredAt.fileName shouldBe "ProjectArchitecture.kt"
            source[group.declaredAt.lineNumber - 1] shouldContain "\"${group.name}\""
        }
        projectArchitecture.allRoles.forEach { role ->
            role.declaredAt.fileName shouldBe "ProjectArchitecture.kt"
            source[role.declaredAt.lineNumber - 1] shouldContain "\"${role.name}\""
        }
    }

    "documented = false を付けた build group だけが documented = false になる" {
        projectArchitecture.allGroups
            .filterNot { it.documented }
            .map { it.qualifiedName } shouldBe listOf("build")
    }

    "documented を省略した役割はすべて documented = true になる" {
        projectArchitecture.allRoles.filterNot { it.documented } shouldBe emptyList()
    }

    "title を省略しなかった役割は指定した表示名を持つ" {
        val model = projectArchitecture.allRoles.single { it.qualifiedName == "domain/Model" }
        model.title shouldBe "モデル"
        model.examples.map { it.name } shouldBe listOf("Health")
    }

    "すべての役割が layout を1つ持つ。ステップ2 で中身を埋める場所になる" {
        projectArchitecture.allRoles.filter { it.layouts.size != 1 } shouldBe emptyList()
    }
})

/**
 * The lines of `ProjectArchitecture.kt`, so a captured line number can be compared against
 * what is actually written there.
 *
 * A test task's working directory is its module directory, but that is a default a build
 * file can change, so the file is looked up by walking up from wherever the tests run.
 */
private fun projectArchitectureSourceLines(): List<String> {
    val relativePath = "src/test/kotlin/com/example/ProjectArchitecture.kt"
    // `getProperty` is a platform type; the JVM always defines `user.dir`.
    val workingDir = File(requireNotNull(System.getProperty("user.dir"))).absoluteFile
    val source = generateSequence(workingDir) { it.parentFile }
        .map { File(it, relativePath) }
        .firstOrNull { it.isFile }
    return requireNotNull(source) { "$relativePath が $workingDir とその親に見つからない" }
        .readLines()
}
