package com.example

import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.shouldBe

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
