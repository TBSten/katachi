package com.example.sample

import com.example.sample.application.appRoles
import com.example.sample.application.dataRoles
import com.example.sample.application.featureRoles
import com.example.sample.application.uiRoles
import com.example.sample.gradle.gradleRoles
import com.example.sample.testing.testingRoles
import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import java.io.File
import me.tbsten.katachi.check.validate
import me.tbsten.katachi.dsl.Architecture
import me.tbsten.katachi.dsl.Documented
import me.tbsten.katachi.dsl.Examples
import me.tbsten.katachi.dsl.ExperimentalKatachiApi
import me.tbsten.katachi.dsl.InternalKatachiApi
import me.tbsten.katachi.dsl.Title
import me.tbsten.katachi.dsl.architecture

/**
 * Checks that [projectArchitecture] builds into the model we meant to declare, and that the
 * check it drives really looks at this repository.
 *
 * **katachi's own integration test. A project adopting katachi does not write this** — it
 * writes [ProjectArchitectureTest] and nothing else. This one is run from a real Gradle test
 * task of a sample project rather than from katachi's own JVM tests, and what it guards is
 * that nothing in the surrounding toolchain quietly breaks the DSL — above all the
 * declaration site capture, which reads the JVM stack trace.
 *
 * Since the definition is split across `ArchitectureScope` extension functions in sibling
 * packages, the declaration site tests below are also the proof that the split is free:
 * a declaration written in `FeatureRoles.kt` must report `FeatureRoles.kt`, not the
 * `ProjectArchitecture.kt` that called into it.
 *
 * The last test is the one that keeps the rest honest: `assert()` passing proves nothing on
 * its own, because a traversal that reached no file at all would also pass. Removing one
 * group from an otherwise identical definition has to turn exactly that group's files into
 * violations.
 */
@OptIn(InternalKatachiApi::class, ExperimentalKatachiApi::class)
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
            "tool/Documentation",
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
        // 書かなかった宣言には Documented が入らない。省略を true と読むのはここ（読む側）。
        val documentedByGroup =
            projectArchitecture.allGroups.associate { it.name to (it[Documented] ?: true) }
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
        undocumentedRoles.map { it[Documented] ?: true }.toSet() shouldBe setOf(false)
        otherRoles.map { it[Documented] ?: true }.toSet() shouldBe setOf(true)
    }

    "title を省略した役割は Title を持たず、役割名がそのまま表示名になる" {
        val git = projectArchitecture.allRoles.single { it.qualifiedName == "tool/Git" }
        git[Title] shouldBe null
        (git[Title] ?: git.name) shouldBe "Git"
    }

    "title を書いた役割はその表示名になる" {
        val screen = projectArchitecture.allRoles.single { it.qualifiedName == "feature/Screen" }
        screen[Title] shouldBe "画面"
    }

    "1つの役割が複数の置き場所を layout として持てる" {
        val repository = projectArchitecture.allRoles.single { it.qualifiedName == "data/Repository" }
        repository.layouts.size shouldBe 2
    }

    "example は呼んだ順に保持される" {
        val repository = projectArchitecture.allRoles.single { it.qualifiedName == "data/Repository" }
        repository[Examples].orEmpty().map { it.name } shouldContainExactly
            listOf("UserRepository", "UserRepositoryImpl")
    }

    "役割を1つの group ぶん欠いた定義では、その group が覆っていたファイルだけが違反になる" {
        // The counterpart of ProjectArchitectureTest: that one proves the definition
        // accepts the repository, this one proves the traversal actually reached it. An
        // empty result here would mean the check walked nothing and passed for free.
        //
        // `tool` is the group to drop because it owns exactly two files, both at the root,
        // so the expectation can be written out in full rather than as a count. The order
        // is katachi's: violations are grouped by kind, and within a kind the traversal
        // order survives — the root listed by name, where `.gitignore` precedes `README.md`.
        val violations = architectureWithoutToolRoles.validate()

        violations.map { "[${it.label}] ${it.path}" } shouldContainExactly listOf(
            "[UnexpectedFile] .gitignore",
            "[UnexpectedFile] README.md",
        )
    }
})

/**
 * [projectArchitecture] with `toolRoles()` left out, used by the last test above.
 *
 * Deliberately broken, and deliberately kept out of `ProjectArchitecture.kt`: that file is
 * what a user reads as the worked example of a definition, and a definition that is meant to
 * fail has no place in it.
 */
private val architectureWithoutToolRoles: Architecture = architecture {
    featureRoles()
    uiRoles()
    dataRoles()
    appRoles()
    testingRoles()
    gradleRoles()
    // toolRoles() — omitted on purpose. `.gitignore` and `README.md` lose their role.
}

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
