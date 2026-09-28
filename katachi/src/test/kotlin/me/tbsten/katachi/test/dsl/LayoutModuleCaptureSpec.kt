package me.tbsten.katachi.test.dsl

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.assertions.withClue
import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import me.tbsten.katachi.dsl.DeclarationKind
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
                ":feature:${capture("feature")}".module { mainSourceSet / kotlin / "*Screen".ktFile() }
            }.shape() shouldBe layoutOf {
                ":feature:*".module { mainSourceSet / kotlin / "*Screen".ktFile() }
            }.shape()
        }

        "名前付き module は名前無し module と同じ shape になる（実プロジェクトの index）" {
            layoutOf(features) {
                ":feature:${capture("feature")}".module { mainSourceSet / kotlin / "*Screen".ktFile() }
            }.shape() shouldBe layoutOf(features) {
                ":feature:*".module { mainSourceSet / kotlin / "*Screen".ktFile() }
            }.shape()
        }

        "* が2つのキーに順に名前を渡せる" {
            val captured = mutableListOf<Pair<String, String>>()

            layoutOf(moduleIndexOf("core/data/remoteApi")) {
                ":core:${capture("layer")}:${capture("part")}".module { captured += wildcard("layer") to wildcard("part") }
            }

            captured shouldBe listOf("data" to "remoteApi")
        }

        "* と末尾の ** があるキーは名前を付けた * の分だけ名前を渡せ、** は wildcards から位置で読む" {
            val captured = mutableListOf<Pair<String, List<String>>>()

            layoutOf(moduleIndexOf("feature/home/ui/list")) {
                ":feature:${capture("feature")}:**".module { captured += wildcard("feature") to wildcards }
            }

            captured shouldBe listOf("home" to listOf("home", "ui", "list"))
        }

        "モジュールの capture は module が開いたディレクトリの下のエントリに運ばれる" {
            val entries = layoutOf {
                ":feature:${capture("feature")}".module { capture("pkg") / "*Screen".ktFile() }
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
        "1つの module key で同じ名前を2回使うと KatachiDuplicateCaptureException" {
            val failure = shouldThrow<KatachiDuplicateCaptureException> {
                layoutOf { ":core:${capture("x")}:${capture("x")}".module { } }
            }

            failure.name shouldBe "x"
            failure.path shouldBe ":core:*:*"
            failure.role shouldBe null
            withClue("同じ位置を2回言わない") { failure.message!! shouldNotContain "(first at" }
        }

        "名前が識別子でないと KatachiInvalidIdentifierException" {
            val failure = shouldThrow<KatachiInvalidIdentifierException> {
                layoutOf { ":feature:${capture("1feature")}".module { } }
            }

            failure.kind shouldBe DeclarationKind.Capture
        }

        "一部の * だけ名前を付けるのも通り、名前の無い * は wildcards から位置で読める" {
            val captured = mutableListOf<Pair<String, List<String>>>()

            layoutOf(moduleIndexOf("core/data/remoteApi")) {
                ":core:${capture("layer")}:*".module { captured += wildcard("layer") to wildcards }
            }

            captured shouldBe listOf("data" to listOf("data", "remoteApi"))
        }
    }

    "wildcard(name)" - {
        "wildcard(name) で名前付きの値を読める" {
            val captured = mutableListOf<Pair<String, String>>()

            layoutOf(features) {
                ":feature:${capture("feature")}".module { captured += wildcard("feature") to wildcards[0] }
            }

            captured shouldBe listOf("home" to "home", "settings" to "settings")
        }

        "wildcard(name) は プロジェクトを見ていないインデックスでは capture の名前のプレースホルダを返す" {
            val captured = mutableListOf<Pair<String, String>>()

            layoutOf {
                ":feature:${capture("feature")}".module { captured += wildcard("feature") to wildcards[0] }
            }

            captured shouldBe listOf("<feature>" to "<feature>")
        }

        "名前の無い module の中で wildcard(name) を呼ぶと KatachiUnknownCaptureException" {
            val failure = shouldThrow<KatachiUnknownCaptureException> {
                layoutOf(features) { ":feature:*".module { wildcard("feature") } }
            }

            failure.name shouldBe "feature"
            failure.knownNames shouldBe emptyList()
            failure.modulePath shouldBe ":feature:home"
        }

        "知らない名前で wildcard(name) を呼ぶと KatachiUnknownCaptureException" {
            val failure = shouldThrow<KatachiUnknownCaptureException> {
                layoutOf(features) { ":feature:${capture("feature")}".module { wildcard("layer") } }
            }

            failure.knownNames shouldBe listOf("feature")
            failure.message!! shouldContain "\"feature\""
        }

        "module の外で wildcard(name) を呼ぶと、名前と宣言位置と書く場所を示す KatachiWildcardsOutsideModuleException" {
            val failure = shouldThrow<KatachiWildcardsOutsideModuleException> {
                layoutOf { wildcard("feature") }
            }

            failure.name shouldBe "feature"
            failure.declaredAt?.fileName shouldBe "LayoutModuleCaptureSpec.kt"
            failure.message!! shouldContain "`wildcard(\"feature\")` at LayoutModuleCaptureSpec.kt:"
            failure.message!! shouldContain "\":feature:\${capture(\"feature\")}\".module { }"
            failure.message!! shouldNotContain "`wildcards` can only"
        }

        "名前付き module でも wildcards はそのまま読める" {
            val captured = mutableListOf<List<String>>()

            layoutOf(features) { ":feature:${capture("feature")}".module { captured += wildcards } }

            captured shouldBe listOf(listOf("home"), listOf("settings"))
        }

        "wildcard(name) から組んだファイル名が wildcards から組んだものと同じ shape になる" {
            listOf(features, null).forEach { index ->
                val byName: me.tbsten.katachi.dsl.LayoutScope.() -> Unit = {
                    ":feature:${capture("feature")}".module { "${wildcard("feature").pascalCase}Screen".ktFile() }
                }
                val byPosition: me.tbsten.katachi.dsl.LayoutScope.() -> Unit = {
                    ":feature:*".module { "${wildcards[0].pascalCase}Screen".ktFile() }
                }
                val named = if (index == null) layoutOf(byName) else layoutOf(index, byName)
                val positional = if (index == null) layoutOf(byPosition) else layoutOf(index, byPosition)

                // 見ていないインデックスでは、名前付きの * は <feature>、名前の無い * は <name> と読む。
                named.shape() shouldBe positional.shape().map { it.replace("<name>", "<feature>") }
            }
        }
    }
})
