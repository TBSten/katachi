package me.tbsten.katachi.test.konsist.declarations

import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.shouldBe
import me.tbsten.katachi.konsist.declarations.KtFileContentScope
import me.tbsten.katachi.konsist.declarations.internal.renderAnythingElse
import me.tbsten.katachi.konsist.declarations.internal.renderImport
import me.tbsten.katachi.konsist.declarations.internal.renderPackage
import me.tbsten.katachi.konsist.declarations.internal.renderRule

/** Rules written back the way Kotlin writes the declaration they describe. */

/** Every declaration line [block] writes directly in the file, rendered. */
private fun renderedLines(fileName: String = "Sample", block: KtFileContentScope.() -> Unit): List<String> =
    rulesOf(fileName = fileName, block = block).declarations?.lines.orEmpty().map(::renderRule)

class RuleRenderingSpec : FreeSpec({
    "宣言の行" - {
        "見本の 5 つ" {
            renderedLines(fileName = "HomeViewModel") {
                required(annotated("HiltViewModel"), public, `class`, fileName).superclass("ViewModel")
                allow(public, `fun`, "on*")().returns("Unit")
                required(public, `val`, "uiState").type("StateFlow<HomeUiState>")
                required(`fun`, "load")
                not.allow(`class`, "*Impl").except("DefaultImpl")
            } shouldBe listOf(
                "@HiltViewModel public class HomeViewModel : ViewModel",
                "public fun on*(): Unit",
                "public val uiState: StateFlow<HomeUiState>",
                "fun load(...)",
                "not class *Impl except DefaultImpl",
            )
        }

        "修飾子は書いた順ではなく Kotlin の順に並ぶ" {
            val lines = rulesOf {
                required(data, public, `class`, "A")
                required(const, private, `val`, "D")
                required(`class`, "H") { required(operator, override, suspend, `fun`, "b") }
            }.declarations?.lines.orEmpty()

            lines.take(2).map(::renderRule) shouldBe listOf("public data class A", "private const val D")
            lines[2].members?.lines?.map(::renderRule) shouldBe listOf("override suspend operator fun b(...)")
        }

        "前置きの 4 通り" {
            renderedLines {
                required(`class` or `interface`, "HomeUiState")
                required(sealed, type, "Action")
                allow(internal or private, data, `object`, "*")
                required(public or data, `class` or `interface`, "X")
                required(internal or private, `class` or `interface`, "Y")
                allow(annotated("Preview") or annotated("PreviewLightDark"), private, `fun`, "*Preview")
                required(public, public or data, `object`, "Z")
            } shouldBe listOf(
                "(class or interface) HomeUiState",
                "sealed (class or interface) Action",
                "(internal or private) data object *",
                "(public class or public interface or data class) X",
                "(internal or private) (class or interface) Y",
                "(@Preview or @PreviewLightDark) private fun *Preview(...)",
                "(public or public data) object Z",
            )
        }

        "後ろにつなぐもの" {
            renderedLines {
                required(`class`, "A").superclass("B", "C<D>")
                required(`fun`, "e")("name: String", "*: Int")
                required(`fun`, "f")()
                required(`fun`, "g")(anyArgs())
                required(`fun`, "h").returns("Unit")
                required(`var`, "i").type("Int?")
                required(`typealias`, "J")
            } shouldBe listOf(
                "class A : B, C<D>",
                "fun e(name: String, *: Int)",
                "fun f()",
                "fun g(...)",
                "fun h(...): Unit",
                "var i: Int?",
                "typealias J",
            )
        }

        "禁止の行は not を前に、except を後ろに" {
            renderedLines {
                not.allow(public, `var`, "*").except("isEnabled", "isVisible")
                not.allow(annotated("Deprecated"), `fun`, "*")
            } shouldBe listOf(
                "not public var * except isEnabled, isVisible",
                "not @Deprecated fun *(...)",
            )
        }
    }

    "anythingElse" - {
        "修飾子なし・1 つ・or" {
            val rules = rulesOf {
                allow(anythingElse)
                allow(private, anythingElse)
                allow(internal or private, anythingElse)
                allow(annotated("Preview"), private, anythingElse)
            }

            rules.declarations?.anythingElse.orEmpty().map(::renderAnythingElse) shouldBe listOf(
                "(anything)",
                "private (anything)",
                "(internal or private) (anything)",
                "@Preview private (anything)",
            )
        }
    }

    "import と package" - {
        "import は書いたとおりのパス" {
            rulesOf {
                `import`("kotlin.**")
                not.`import`("android.**").except("android.os.**")
            }.imports.map(::renderImport) shouldBe listOf(
                "import kotlin.**",
                "not import android.** except android.os.**",
            )
        }

        "package はパスか、ファイルのディレクトリ" {
            val packages = rulesOf {
                `package`("com.example.**")
                `package`(matchingDirectory)
                not.`package`("com.example.legacy.**")
            }.packages

            packages.map { renderPackage(it) } shouldBe listOf(
                "package com.example.**",
                "package (the file's directory)",
                "not package com.example.legacy.**",
            )
            renderPackage(packages[1], directoryPackage = "com.example.feature.home") shouldBe
                "package com.example.feature.home  (the file's directory)"
        }
    }
})
