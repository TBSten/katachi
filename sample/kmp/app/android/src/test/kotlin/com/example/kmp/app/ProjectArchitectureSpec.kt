package com.example.kmp.app

import io.kotest.core.spec.style.FreeSpec
import io.kotest.inspectors.forAll
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.string.shouldContain
import java.io.File

/**
 * Checks the definition in `ProjectArchitecture.kt` against the model katachi builds from
 * it. Step 1 has no `assert()` yet, so the model is inspected directly.
 */
class ProjectArchitectureSpec : FreeSpec({
    "宣言した group がすべてモデルに含まれる" {
        projectArchitecture.allGroups.map { it.qualifiedName }.toSet() shouldBe
            setOf("ui", "data", "testing", "app", "build")
    }

    "宣言した役割がすべてモデルに含まれる" {
        projectArchitecture.allRoles.map { it.qualifiedName }.toSet() shouldBe
            setOf(
                "ui/Screen",
                "ui/ViewModel",
                "ui/Route",
                "ui/Component",
                "ui/Theme",
                "ui/UiCore",
                "ui/Navigation",
                "data/Repository",
                "data/PlatformImplementation",
                "testing/Fake",
                "testing/Test",
                "app/Entrypoint",
                "app/AndroidResource",
                "app/XcodeProject",
                "build/GradleModule",
                "build/GradleRoot",
                "build/Git",
            )
    }

    "group は宣言した順に並ぶ" {
        projectArchitecture.groups.map { it.name } shouldContainExactly
            listOf("ui", "data", "testing", "app", "build")
    }

    "宣言元のファイル名と行番号が取れる" {
        // The real point of this test: the declaration site is read off the stack trace, so
        // it is the kind of thing that breaks only outside katachi's own test setup.
        val screen = projectArchitecture.allRoles.single { it.qualifiedName == "ui/Screen" }
        screen.declaredAt.fileName shouldBe "ProjectArchitecture.kt"
        (screen.declaredAt.lineNumber > 0) shouldBe true

        // Groups and layouts carry one too.
        projectArchitecture.allGroups.single { it.name == "data" }
            .declaredAt.fileName shouldBe "ProjectArchitecture.kt"
        screen.layouts.single().declaredAt.fileName shouldBe "ProjectArchitecture.kt"
    }

    "捕捉した行番号の行に、その宣言が実際に書かれている" {
        // The test above only proves the file is the user's and the line is positive: every
        // declaration lives in the same file, so it stays green even if the frame filter
        // picks the frame one level out. This one reads the source back, so a one-frame
        // shift lands on `"ui".group {` or on the `uiRoles()` call and fails. No line
        // number is hard-coded, so editing ProjectArchitecture.kt does not break it.
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

    "ビルド関連の group と役割は documented = false" {
        projectArchitecture.allGroups.single { it.name == "build" }.documented shouldBe false
        projectArchitecture.allRoles
            .filter { it.groupPath == listOf("build") }
            .forAll { it.documented shouldBe false }
    }

    "documented を省略した group と役割は true" {
        projectArchitecture.allGroups.single { it.name == "ui" }.documented shouldBe true
        projectArchitecture.allRoles
            .single { it.qualifiedName == "data/Repository" }
            .documented shouldBe true
    }

    "title を書いた役割・group は表示名がそれになる" {
        projectArchitecture.allRoles.single { it.qualifiedName == "ui/Screen" }
            .title shouldBe "画面"
        // The group is named `ui` but titled `UI`, so this fails if the declared title is
        // dropped in favour of the default.
        projectArchitecture.allGroups.single { it.name == "ui" }.title shouldBe "UI"
    }

    "title を省略した役割は役割名がそのまま表示名になる" {
        // `build/Git` is the only declaration in this sample without a `title`.
        projectArchitecture.allRoles.single { it.qualifiedName == "build/Git" }
            .title shouldBe "Git"
    }

    "example は呼んだ順にすべて保持される" {
        projectArchitecture.allRoles.single { it.qualifiedName == "data/Repository" }
            .examples.map { it.name } shouldContainExactly
            listOf("UserRepository", "UserRepositoryImpl")
    }

    "KMP 特有の役割が宣言されている" {
        // These three are what makes this sample different from sample/android.
        val roles = projectArchitecture.allRoles.associateBy { it.qualifiedName }
        roles["data/PlatformImplementation"] shouldNotBe null
        roles["app/XcodeProject"] shouldNotBe null
        roles["testing/Test"]?.summary?.contains("commonTest") shouldBe true
    }

    "すべての役割が layout を1つ以上持つ" {
        projectArchitecture.allRoles.forAll { it.layouts.isNotEmpty() shouldBe true }
    }
})

/**
 * The lines of `ProjectArchitecture.kt`, so a captured line number can be compared against
 * what is actually written there.
 *
 * A test task's working directory is its module directory — here `app/android`, two levels
 * below the sample root — but that is a default a build file can change, so the file is
 * looked up by walking up from wherever the tests run.
 */
private fun projectArchitectureSourceLines(): List<String> {
    val relativePath = "src/test/kotlin/com/example/kmp/app/ProjectArchitecture.kt"
    // `getProperty` is a platform type, and AGP compiles unit tests in strict mode; the JVM
    // always defines `user.dir`.
    val workingDir = File(requireNotNull(System.getProperty("user.dir"))).absoluteFile
    val source = generateSequence(workingDir) { it.parentFile }
        .map { File(it, relativePath) }
        .firstOrNull { it.isFile }
    return requireNotNull(source) { "$relativePath が $workingDir とその親に見つからない" }
        .readLines()
}
