package com.example.kmp.app

import io.kotest.core.spec.style.FreeSpec
import io.kotest.inspectors.forAll
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe

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

    "title を書いた役割は表示名がそれになり、書かなければ役割名がそのまま使われる" {
        projectArchitecture.allRoles.single { it.qualifiedName == "ui/Screen" }
            .title shouldBe "画面"
        // `"ViewModel"` sets `title` to the same text as its name, which is also what the
        // default would be; `Navigation` proves the default is the name itself.
        projectArchitecture.allGroups.single { it.name == "ui" }.title shouldBe "UI"
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
