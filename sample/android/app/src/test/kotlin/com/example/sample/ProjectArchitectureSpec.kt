package com.example.sample

import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe

/**
 * Checks that [projectArchitecture] builds into the model we meant to declare.
 *
 * This is an integration test of the DSL, run from a real Android unit test task rather
 * than from katachi's own JVM tests. What it really guards is that nothing in the AGP
 * toolchain (its bundled Kotlin compiler, its unit test runtime) quietly breaks the DSL —
 * above all the declaration site capture, which reads the JVM stack trace.
 */
class ProjectArchitectureSpec : FreeSpec({
    "宣言した group がすべて宣言順にモデルに含まれる" {
        projectArchitecture.allGroups.map { it.qualifiedName } shouldContainExactly
            listOf("ui", "data", "testing", "app", "build")
    }

    "宣言した役割が group ごと正しくモデルに含まれる" {
        projectArchitecture.allRoles.map { it.qualifiedName }.toSet() shouldBe setOf(
            "ui/Screen",
            "ui/ViewModel",
            "ui/Route",
            "ui/Component",
            "ui/Theme",
            "ui/UiCore",
            "ui/Navigation",
            "data/Repository",
            "testing/Fake",
            "testing/Test",
            "app/Entrypoint",
            "app/AndroidResource",
            "build/GradleModule",
            "build/GradleRoot",
            "build/Git",
        )
    }

    "役割の宣言位置として ProjectArchitecture.kt の行番号が取れる" {
        val screen = projectArchitecture.allRoles.single { it.qualifiedName == "ui/Screen" }
        screen.declaredAt.fileName shouldBe "ProjectArchitecture.kt"
        // The exact line churns on every edit; that it is a real line is the point.
        (screen.declaredAt.lineNumber > 0) shouldBe true
    }

    "group の宣言位置も ProjectArchitecture.kt になる" {
        val ui = projectArchitecture.allGroups.single { it.qualifiedName == "ui" }
        ui.declaredAt.fileName shouldBe "ProjectArchitecture.kt"
    }

    "layout の宣言位置も ProjectArchitecture.kt になる" {
        val screen = projectArchitecture.allRoles.single { it.qualifiedName == "ui/Screen" }
        screen.layouts.single().declaredAt.fileName shouldBe "ProjectArchitecture.kt"
    }

    "build group だけが documented = false になっている" {
        val documentedByGroup = projectArchitecture.allGroups.associate { it.name to it.documented }
        documentedByGroup shouldBe mapOf(
            "ui" to true,
            "data" to true,
            "testing" to true,
            "app" to true,
            "build" to false,
        )
    }

    "build group の役割だけが documented = false になっている" {
        val (buildRoles, otherRoles) =
            projectArchitecture.allRoles.partition { it.groupPath == listOf("build") }
        buildRoles.map { it.documented }.toSet() shouldBe setOf(false)
        otherRoles.map { it.documented }.toSet() shouldBe setOf(true)
    }

    "title を省略した役割は役割名がそのまま表示名になる" {
        val git = projectArchitecture.allRoles.single { it.qualifiedName == "build/Git" }
        git.title shouldBe "Git"
    }

    "title を書いた役割はその表示名になる" {
        val screen = projectArchitecture.allRoles.single { it.qualifiedName == "ui/Screen" }
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
