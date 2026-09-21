package com.example.sample

import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import java.io.File

/**
 * Checks that [projectArchitecture] builds into the model we meant to declare.
 *
 * This is an integration test of the DSL, run from a real Android unit test task rather
 * than from katachi's own JVM tests. What it really guards is that nothing in the AGP
 * toolchain (its bundled Kotlin compiler, its unit test runtime) quietly breaks the DSL —
 * above all the declaration site capture, which reads the JVM stack trace.
 *
 * Since the definition is split across `ArchitectureScope` extension functions in sibling
 * packages, the declaration site tests below are also the proof that the split is free:
 * a declaration written in `FeatureRoles.kt` must report `FeatureRoles.kt`, not the
 * `ProjectArchitecture.kt` that called into it.
 */
class ProjectArchitectureSpec : FreeSpec({
    "宣言した group がすべて宣言順にモデルに含まれる" {
        projectArchitecture.allGroups.map { it.qualifiedName } shouldContainExactly
            listOf("feature", "ui", "data", "app", "testing", "build", "tool")
    }

    "宣言した役割が group ごと正しくモデルに含まれる" {
        projectArchitecture.allRoles.map { it.qualifiedName }.toSet() shouldBe setOf(
            "feature/Screen",
            "feature/ViewModel",
            "feature/Route",
            "ui/Component",
            "ui/Theme",
            "ui/UiCore",
            "ui/Preview",
            "ui/PreviewRoot",
            "ui/Navigation",
            "data/Repository",
            "app/Entrypoint",
            "app/AndroidResource",
            "testing/Fake",
            "testing/Test",
            "testing/ArchitectureDefinition",
            "build/GradleModule",
            "build/GradleRoot",
            "tool/Git",
        )
    }

    "役割の宣言位置として、その役割を書いたファイルの行番号が取れる" {
        val screen = projectArchitecture.allRoles.single { it.qualifiedName == "feature/Screen" }
        screen.declaredAt.fileName shouldBe "FeatureRoles.kt"
        // The exact line churns on every edit; that it is a real line is the point.
        (screen.declaredAt.lineNumber > 0) shouldBe true
    }

    "group の宣言位置も、その group を書いたファイルになる" {
        // One expectation per group, so the test states that the definition really is spread
        // over seven files and that each declaration is attributed to its own.
        projectArchitecture.allGroups.associate { it.qualifiedName to it.declaredAt.fileName } shouldBe
            mapOf(
                "feature" to "FeatureRoles.kt",
                "ui" to "UiRoles.kt",
                "data" to "DataRoles.kt",
                "app" to "AppRoles.kt",
                "testing" to "TestingRoles.kt",
                "build" to "GradleRoles.kt",
                "tool" to "ToolRoles.kt",
            )
    }

    "役割の宣言位置は、その役割を含む group と同じファイルになる" {
        val fileNameByGroup = projectArchitecture.allGroups.associate { it.qualifiedName to it.declaredAt.fileName }

        projectArchitecture.allRoles.forEach { role ->
            val groupPath = role.groupPath.joinToString("/")
            role.declaredAt.fileName shouldBe fileNameByGroup.getValue(groupPath)
        }
    }

    "宣言位置は呼び出し元の ProjectArchitecture.kt ではなく、複数のファイルに散らばる" {
        // The point of the split: neither `architecture { }` nor the extension functions are
        // `inline`, so the captured frame is the declaration's own and never the `uiRoles()`
        // call in ProjectArchitecture.kt.
        val fileNames = (
            projectArchitecture.allGroups.map { it.declaredAt.fileName } +
                projectArchitecture.allRoles.map { it.declaredAt.fileName }
            ).toSet()

        fileNames shouldBe setOf(
            "FeatureRoles.kt",
            "UiRoles.kt",
            "DataRoles.kt",
            "AppRoles.kt",
            "TestingRoles.kt",
            "GradleRoles.kt",
            "ToolRoles.kt",
        )
    }

    "layout の宣言位置も、その役割を書いたファイルになる" {
        val screen = projectArchitecture.allRoles.single { it.qualifiedName == "feature/Screen" }
        screen.layouts.single().declaredAt.fileName shouldBe "FeatureRoles.kt"
    }

    "捕捉した行番号の行に、その宣言が実際に書かれている" {
        // The tests above only prove the file name and that the line is positive. This one
        // reads the source back, so a one-frame shift — landing on the `uiRoles()` call, or
        // on the `"ui".group {` that encloses a role — fails here. No line number is
        // hard-coded, so editing the declaration files does not break it.
        val sources = declarationSourceLines()

        projectArchitecture.allGroups.forEach { group ->
            val source = sources.getValue(group.declaredAt.fileName)
            source[group.declaredAt.lineNumber - 1] shouldContain "\"${group.name}\""
        }
        projectArchitecture.allRoles.forEach { role ->
            val source = sources.getValue(role.declaredAt.fileName)
            source[role.declaredAt.lineNumber - 1] shouldContain "\"${role.name}\""
        }
    }

    "build group と tool group だけが documented = false になっている" {
        val documentedByGroup = projectArchitecture.allGroups.associate { it.name to it.documented }
        documentedByGroup shouldBe mapOf(
            "feature" to true,
            "ui" to true,
            "data" to true,
            "app" to true,
            "testing" to true,
            "build" to false,
            "tool" to false,
        )
    }

    "build group と tool group の役割だけが documented = false になっている" {
        val undocumentedGroupPaths = listOf(listOf("build"), listOf("tool"))
        val (undocumentedRoles, otherRoles) =
            projectArchitecture.allRoles.partition { it.groupPath in undocumentedGroupPaths }
        undocumentedRoles.map { it.documented }.toSet() shouldBe setOf(false)
        otherRoles.map { it.documented }.toSet() shouldBe setOf(true)
    }

    "title を省略した役割は役割名がそのまま表示名になる" {
        val git = projectArchitecture.allRoles.single { it.qualifiedName == "tool/Git" }
        git.title shouldBe "Git"
    }

    "title を書いた役割はその表示名になる" {
        val screen = projectArchitecture.allRoles.single { it.qualifiedName == "feature/Screen" }
        screen.title shouldBe "画面"
    }

    "1つの役割が複数の置き場所を layout として持てる" {
        val repository = projectArchitecture.allRoles.single { it.qualifiedName == "data/Repository" }
        repository.layouts.size shouldBe 2
    }

    "example は呼んだ順に保持される" {
        val repository = projectArchitecture.allRoles.single { it.qualifiedName == "data/Repository" }
        repository.examples.map { it.name } shouldContainExactly
            listOf("UserRepository", "UserRepositoryImpl")
    }
})

/**
 * Every Kotlin source of this test source set, keyed by file name, so a captured line
 * number can be compared against what is actually written there.
 *
 * The declarations are spread over sibling packages, so the lookup is by file name rather
 * than by a fixed path: that is all a [me.tbsten.katachi.dsl.DeclarationSite] carries, and
 * it keeps the test from having to know which package a role was moved into.
 *
 * A test task's working directory is its module directory, but that is a default a build
 * file can change, so the source set is looked up by walking up from wherever the tests run.
 */
private fun declarationSourceLines(): Map<String, List<String>> {
    val relativePath = "src/test/kotlin/com/example/sample"
    // `getProperty` is a platform type, and AGP compiles unit tests in strict mode; the JVM
    // always defines `user.dir`.
    val workingDir = File(requireNotNull(System.getProperty("user.dir"))).absoluteFile
    val sourceRoot = generateSequence(workingDir) { it.parentFile }
        .map { File(it, relativePath) }
        .firstOrNull { it.isDirectory }
    requireNotNull(sourceRoot) { "$relativePath が $workingDir とその親に見つからない" }

    return sourceRoot.walkTopDown()
        .filter { it.isFile && it.extension == "kt" }
        .associate { it.name to it.readLines() }
}
