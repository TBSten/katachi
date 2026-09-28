package me.tbsten.katachi.test.dsl

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import me.tbsten.katachi.dsl.DeclarationKind
import me.tbsten.katachi.dsl.KatachiCaptureCountMismatchException
import me.tbsten.katachi.dsl.KatachiDuplicateCaptureException
import me.tbsten.katachi.dsl.KatachiInvalidIdentifierException
import me.tbsten.katachi.dsl.gradle.*
import me.tbsten.katachi.dsl.internal.LayoutCaptures
import me.tbsten.katachi.dsl.internal.ModuleCapture
import me.tbsten.katachi.dsl.internal.PathCapture
import me.tbsten.katachi.dsl.kotlin.ktFile
import me.tbsten.katachi.dsl.pascalCase

class LayoutModuleCaptureSpec : FreeSpec({
    val features = moduleIndexOf("feature/home", "feature/settings")

    "検査の上では名前無しの module と同じ" - {
        "名前付き module は名前無し module と同じ shape になる（プロジェクトを見ていないインデックス）" {
            layoutOf {
                ":feature:*".module(capture = "feature") { mainSourceSet / kotlin / "*Screen".ktFile() }
            }.shape() shouldBe layoutOf {
                ":feature:*".module { mainSourceSet / kotlin / "*Screen".ktFile() }
            }.shape()
        }

        "名前付き module は名前無し module と同じ shape になる（実プロジェクトの index）" {
            layoutOf(features) {
                ":feature:*".module(capture = "feature") { mainSourceSet / kotlin / "*Screen".ktFile() }
            }.shape() shouldBe layoutOf(features) {
                ":feature:*".module { mainSourceSet / kotlin / "*Screen".ktFile() }
            }.shape()
        }

        "* が2つのキーに順に名前を渡せる" {
            val captured = mutableListOf<Pair<String, String>>()

            layoutOf(moduleIndexOf("core/data/remoteApi")) {
                ":core:*:*".module("layer", "part") { captured += wildcard("layer") to wildcard("part") }
            }

            captured shouldBe listOf("data" to "remoteApi")
        }

        "* と末尾の ** があるキーは * の数だけ名前を渡せる" {
            val captured = mutableListOf<String>()

            layoutOf(moduleIndexOf("feature/home/ui/list")) {
                ":feature:*:**".module("feature") { captured += wildcard("feature") }
            }

            captured shouldBe listOf("home")
        }

        "モジュールの capture は module が開いたディレクトリの下のエントリに運ばれる" {
            val entries = layoutOf {
                ":feature:*".module(capture = "feature") { capture("pkg") / "*Screen".ktFile() }
            }

            entries.single { it.path == "feature/*/*/*Screen.kt" }.captureVariants shouldBe listOf(
                LayoutCaptures(
                    pathCaptures = listOf(PathCapture(2, "pkg")),
                    moduleCapture = ModuleCapture(":feature:*", listOf("feature")),
                ),
            )
            // module の外にあるディレクトリには運ばれない。
            entries.single { it.path == "feature" }.captureVariants shouldBe emptyList()
        }
    }

    "宣言時の検査" - {
        "名前の数が * の数より少ないと KatachiCaptureCountMismatchException" {
            val failure = shouldThrow<KatachiCaptureCountMismatchException> {
                layoutOf { ":core:*:*".module("layer") { } }
            }

            failure.modulePath shouldBe ":core:*:*"
            failure.captures shouldBe listOf("layer")
            failure.wildcardCount shouldBe 2
        }

        "名前の数が * の数より多いと KatachiCaptureCountMismatchException" {
            shouldThrow<KatachiCaptureCountMismatchException> {
                layoutOf { ":feature:*".module("feature", "layer") { } }
            }.wildcardCount shouldBe 1
        }

        "** に名前を付けると KatachiCaptureCountMismatchException" {
            val failure = shouldThrow<KatachiCaptureCountMismatchException> {
                layoutOf { ":feature:**".module(capture = "x") { } }
            }

            failure.wildcardCount shouldBe 0
            failure.message!! shouldContain "`**` cannot be named"
        }

        "名前の数の検査はプロジェクトにマッチするモジュールが無くても行われる" {
            shouldThrow<KatachiCaptureCountMismatchException> {
                layoutOf(moduleIndexOf("core/data")) { ":feature:*".module("a", "b") { } }
            }
        }

        "1つの module で同じ名前を2回渡すと KatachiDuplicateCaptureException" {
            val failure = shouldThrow<KatachiDuplicateCaptureException> {
                layoutOf { ":core:*:*".module("x", "x") { } }
            }

            failure.name shouldBe "x"
            failure.path shouldBe ":core:*:*"
            failure.role shouldBe null
        }

        "名前が識別子でないと KatachiInvalidIdentifierException" {
            val failure = shouldThrow<KatachiInvalidIdentifierException> {
                layoutOf { ":feature:*".module(capture = "1feature") { } }
            }

            failure.kind shouldBe DeclarationKind.Capture
        }
    }

    "wildcard(name)" - {
        "wildcard(name) で名前付きの値を読める" {
            val captured = mutableListOf<Pair<String, String>>()

            layoutOf(features) {
                ":feature:*".module(capture = "feature") { captured += wildcard("feature") to wildcards[0] }
            }

            captured shouldBe listOf("home" to "home", "settings" to "settings")
        }

        "wildcard(name) は プロジェクトを見ていないインデックスではプレースホルダを返す" {
            val captured = mutableListOf<Pair<String, String>>()

            layoutOf {
                ":feature:*".module(capture = "feature") { captured += wildcard("feature") to wildcards[0] }
            }

            captured shouldBe listOf("<name>" to "<name>")
        }

        "名前の無い module の中で wildcard(name) を呼ぶと KatachiUnknownCaptureException" {
            val failure = shouldThrow<KatachiUnknownCaptureException> {
                layoutOf(features) { ":feature:*".module { wildcard("feature") } }
            }

            failure.name shouldBe "feature"
            failure.knownNames shouldBe emptyList()
            failure.modulePath shouldBe ":feature:home"
            failure.message!! shouldContain ".module(capture = \"feature\")"
        }

        "知らない名前で wildcard(name) を呼ぶと KatachiUnknownCaptureException" {
            val failure = shouldThrow<KatachiUnknownCaptureException> {
                layoutOf(features) { ":feature:*".module(capture = "feature") { wildcard("layer") } }
            }

            failure.knownNames shouldBe listOf("feature")
            failure.message!! shouldContain "\"feature\""
        }

        "module の外で wildcard(name) を呼ぶと KatachiWildcardsOutsideModuleException" {
            shouldThrow<KatachiWildcardsOutsideModuleException> {
                layoutOf { wildcard("feature") }
            }
        }

        "名前付き module でも wildcards はそのまま読める" {
            val captured = mutableListOf<List<String>>()

            layoutOf(features) { ":feature:*".module(capture = "feature") { captured += wildcards } }

            captured shouldBe listOf(listOf("home"), listOf("settings"))
        }

        "wildcard(name) から組んだファイル名が wildcards から組んだものと同じ shape になる" {
            listOf(features, null).forEach { index ->
                val byName: me.tbsten.katachi.dsl.LayoutScope.() -> Unit = {
                    ":feature:*".module(capture = "feature") { "${wildcard("feature").pascalCase}Screen".ktFile() }
                }
                val byPosition: me.tbsten.katachi.dsl.LayoutScope.() -> Unit = {
                    ":feature:*".module { "${wildcards[0].pascalCase}Screen".ktFile() }
                }
                val named = if (index == null) layoutOf(byName) else layoutOf(index, byName)
                val positional = if (index == null) layoutOf(byPosition) else layoutOf(index, byPosition)

                named.shape() shouldBe positional.shape()
            }
        }
    }
})
