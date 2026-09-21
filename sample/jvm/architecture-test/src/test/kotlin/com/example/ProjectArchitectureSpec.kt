package com.example

import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.collections.shouldContainExactly
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
 *
 * Since the definition was split into `ArchitectureScope` extension functions, it also
 * proves the harder half: declaration sites still point at the file the user wrote, even
 * though `ProjectArchitecture.kt` no longer contains a single `group` call.
 */
class ProjectArchitectureSpec : FreeSpec({
    "宣言した group がすべてモデルに含まれる" {
        projectArchitecture.allGroups.map { it.qualifiedName }.toSet() shouldBe setOf(
            "api",
            "domain",
            "data",
            "app",
            "testing",
            "build",
            "tool",
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
            "testing/Test",
            "testing/ArchitectureDefinition",
            "build/Gradle",
            "tool/Git",
        )
    }

    "役割の宣言位置として、それを書いた拡張関数のファイルの行番号が取れる" {
        // Guards the stack-trace based capture in a real user build: if the frame filter
        // ever starts skipping user code, this reports the test runner's file instead.
        val controller = projectArchitecture.allRoles.single { it.qualifiedName == "api/Controller" }
        controller.declaredAt.fileName shouldBe "ApiRoles.kt"
        (controller.declaredAt.lineNumber > 0) shouldBe true
    }

    "group の宣言位置も、それを書いた拡張関数のファイルである" {
        val api = projectArchitecture.allGroups.single { it.qualifiedName == "api" }
        api.declaredAt.fileName shouldBe "ApiRoles.kt"
    }

    "宣言位置が ProjectArchitecture.kt ではなく、複数のファイルにまたがる" {
        // The point of splitting the definition: `ProjectArchitecture.kt` only calls the
        // extension functions, so nothing may be attributed to it. If katachi captured the
        // frame one level out, every declaration would collapse onto this one file.
        val files = (projectArchitecture.allGroups.map { it.declaredAt } + projectArchitecture.allRoles.map { it.declaredAt })
            .map { it.fileName }
            .distinct()
            .sorted()

        files shouldContainExactly listOf(
            "ApiRoles.kt",
            "AppRoles.kt",
            "DataRoles.kt",
            "DomainRoles.kt",
            "GradleRoles.kt",
            "TestingRoles.kt",
            "ToolRoles.kt",
        )
    }

    "group ごとに、期待したファイルで宣言されている" {
        projectArchitecture.allGroups.forEach { group ->
            group.declaredAt.fileName shouldBe DECLARING_FILES.getValue(group.qualifiedName)
        }
    }

    "役割は、それが属する group と同じファイルで宣言されている" {
        projectArchitecture.allRoles.forEach { role ->
            val groupName = role.qualifiedName.substringBeforeLast('/')
            role.declaredAt.fileName shouldBe DECLARING_FILES.getValue(groupName)
        }
    }

    "捕捉した行番号の行に、その宣言が実際に書かれている" {
        // The tests above only prove the file name and that the line is positive. This one
        // reads the source back, so a one-frame shift lands on `architecture {` or on the
        // `apiRoles()` call in ProjectArchitecture.kt and fails. No line number is
        // hard-coded, so editing the definition does not break it.
        projectArchitecture.allGroups.forEach { group ->
            val source = sourceLinesOf(group.declaredAt.fileName)
            source[group.declaredAt.lineNumber - 1] shouldContain "\"${group.name}\""
        }
        projectArchitecture.allRoles.forEach { role ->
            val source = sourceLinesOf(role.declaredAt.fileName)
            source[role.declaredAt.lineNumber - 1] shouldContain "\"${role.name}\""
        }
    }

    "documented = false を付けた group だけが documented = false になる" {
        projectArchitecture.allGroups
            .filterNot { it.documented }
            .map { it.qualifiedName } shouldBe listOf("build", "tool")
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

/** Which file each group - and therefore each of its roles - is declared in. */
private val DECLARING_FILES: Map<String, String> = mapOf(
    "api" to "ApiRoles.kt",
    "domain" to "DomainRoles.kt",
    "data" to "DataRoles.kt",
    "app" to "AppRoles.kt",
    "testing" to "TestingRoles.kt",
    "build" to "GradleRoles.kt",
    "tool" to "ToolRoles.kt",
)

/** Cached so that reading a source file once per declaration does not hit the disk again. */
private val sourceLineCache = mutableMapOf<String, List<String>>()

/**
 * The lines of one file of the architecture definition, so a captured line number can be
 * compared against what is actually written there.
 *
 * A test task's working directory is its module directory - here
 * `sample/jvm/architecture-test` - but that is a default a build file can change, so the
 * source root is looked up by walking up from wherever the tests run, and the file itself
 * by name, so moving a role into another package needs no change here.
 */
private fun sourceLinesOf(fileName: String): List<String> = sourceLineCache.getOrPut(fileName) {
    // `getProperty` is a platform type; the JVM always defines `user.dir`.
    val workingDir = File(requireNotNull(System.getProperty("user.dir"))).absoluteFile
    val sourceRoot = generateSequence(workingDir) { it.parentFile }
        .map { File(it, "src/test/kotlin") }
        .firstOrNull { it.isDirectory }
    requireNotNull(sourceRoot) { "src/test/kotlin が $workingDir とその親に見つからない" }

    val source = sourceRoot.walkTopDown().firstOrNull { it.isFile && it.name == fileName }
    requireNotNull(source) { "$fileName が $sourceRoot 以下に見つからない" }.readLines()
}
