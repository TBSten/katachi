package me.tbsten.katachi.test.template

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.types.shouldBeInstanceOf
import me.tbsten.katachi.dsl.Architecture
import me.tbsten.katachi.dsl.architecture
import me.tbsten.katachi.processor.internal.FakeArchitectureProcessContext
import me.tbsten.katachi.template.DescribeTemplates
import me.tbsten.katachi.template.TemplateDetail
import me.tbsten.katachi.test.dsl.files.ForbiddenFileSystem

/** A role whose template reports which of the two ways it was replayed. */
private fun echoArchitecture(): Architecture = architecture {
    "Echo" {
        layout { "echo" / "*Echo.kt".file() }
        template {
            val name by stringParameter()
            file("${name}Echo.kt") { if (isPreview) "preview" else "real" }
        }
    }
}

/**
 * Reproduces the sample's `ControllerRole`: a captured value that must be alphanumeric on a real
 * run, but reads as the placeholder `${resource}` while previewing -- `isPreview` is what lets a
 * check like this one pass a preview without ever accepting a bad value from a real run.
 */
private fun resourceCheckArchitecture(): Architecture = architecture {
    "Controller" {
        layout { "controller" / capture("resource") / "*Controller.kt".file() }
        template {
            val name by stringParameter()
            val resource = captureValue("resource")
            require(isPreview || resource.all { it.isLetterOrDigit() }) {
                "--arg resource=$resource: use letters and digits only"
            }
            file("${name}Controller.kt") { "package com.example.controller.$resource" }
        }
    }
}

private fun Architecture.detail(roleName: String): TemplateDetail {
    val context = FakeArchitectureProcessContext(
        architecture = this,
        args = DescribeTemplates.Args(roleName = roleName),
        fileSystem = ForbiddenFileSystem,
    )
    return DescribeTemplates.process(context).getOrThrow().shouldBeInstanceOf<TemplateDetail>()
}

/**
 * `TemplateScope.isPreview`: true while `DescribeTemplates` previews a template (`detail` here),
 * false while an actual run renders it (`generated`, which is what
 * [me.tbsten.katachi.template.GenerateCodeFromTemplate] renders through too).
 *
 * NOTE: このファイルのパッケージを `me.tbsten.katachi.template` にしてはいけない。
 * captureDeclarationSite() がライブラリ自身のフレームとして読み飛ばしてしまい、
 * 宣言位置が kotest 内部を指すようになる。
 */
class TemplateIsPreviewSpec : FreeSpec({
    "プレビューでは isPreview が true になる" {
        echoArchitecture().detail("Echo").files.single().content shouldBe "preview"
    }

    "実際の生成では isPreview が false になる" {
        val files = echoArchitecture().generated("Echo", mapOf("name" to "Ping"))
        files.getValue("echo/PingEcho.kt") shouldBe "real\n"
    }

    "isPreview で検査を飛ばすテンプレートは、プレビューでは \${resource} の仮の値でも落ちない" {
        val detail = resourceCheckArchitecture().detail("Controller")
        detail.files.single().content shouldBe "package com.example.controller.\${resource}"
    }

    "isPreview で検査を飛ばすテンプレートは、実際の生成では正しい値なら通る" {
        val files = resourceCheckArchitecture().generated(
            "Controller",
            mapOf("name" to "User", "resource" to "user"),
        )
        files.getValue("controller/user/UserController.kt") shouldBe
            "package com.example.controller.user\n"
    }

    "isPreview で検査を飛ばすテンプレートは、実際の生成で不正な値だと分かりやすいメッセージで落ちる" {
        val failure = shouldThrow<IllegalArgumentException> {
            resourceCheckArchitecture().generated(
                "Controller",
                mapOf("name" to "User", "resource" to "user-profile"),
            )
        }
        failure.message shouldContain "--arg resource=user-profile: use letters and digits only"
    }
})
