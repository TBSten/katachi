package me.tbsten.katachi.test.docs

import io.kotest.assertions.withClue
import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldNotContain
import me.tbsten.katachi.dsl.MetadataScope
import me.tbsten.katachi.dsl.architecture
import me.tbsten.katachi.dsl.documentSection

/** Three sections, one of them below the other two, declared the way a user declares them. */
private val TestPolicy = documentSection("テスト方針")
private var MetadataScope.testPolicy by TestPolicy

private val UnitTest = TestPolicy.documentSection("ユニットテスト")
private var MetadataScope.unitTest by UnitTest

private val UiTest = TestPolicy.documentSection("UI テスト")
private var MetadataScope.uiTest by UiTest

private val Ownership = documentSection("持ち主")
private var MetadataScope.ownership by Ownership

/** A section below another one: what level it is written at, and where its parent ends up. */
class DocumentSectionNestingSpec : FreeSpec({
    "深さで見出しレベルが決まる" {
        val arch = architecture {
            "api".group {
                testPolicy = "以下をテストする。"
                unitTest = "変換だけを見る。"
                uiTest = "画面ごと1本。"
            }
        }

        arch.page("api/README.md") shouldBe
            """
            [アーキテクチャ](../README.md)

            # api

            ## テスト方針

            以下をテストする。

            ### ユニットテスト

            変換だけを見る。

            ### UI テスト

            画面ごと1本。
            """.trimIndent() + "\n"
    }

    "親に本文が無くても、子に本文があれば親の見出しは出る" {
        val arch = architecture {
            "api".group { unitTest = "変換だけを見る。" }
        }

        withClue("親の見出しが無いと、子が直前の節の下にぶら下がって読める") {
            arch.page("api/README.md") shouldBe
                """
                [アーキテクチャ](../README.md)

                # api

                ## テスト方針

                ### ユニットテスト

                変換だけを見る。
                """.trimIndent() + "\n"
        }
    }

    "親も子も本文が無ければ、どちらも出ない" {
        val arch = architecture { "api".group { ownership = "platform" } }

        arch.page("api/README.md") shouldNotContain "テスト方針"
    }

    "子を先に書いても、親は子が最初に現れた位置に出る" {
        val arch = architecture {
            "api".group {
                ownership = "platform"
                unitTest = "子から書いた。"
                testPolicy = "あとから親。"
            }
        }

        arch.page("api/README.md") shouldBe
            """
            [アーキテクチャ](../README.md)

            # api

            ## 持ち主

            platform

            ## テスト方針

            あとから親。

            ### ユニットテスト

            子から書いた。
            """.trimIndent() + "\n"
    }

    "5段目までは書ける" {
        val arch = architecture {
            "api".group { deepest = "いちばん下。" }
        }

        withClue("h6 が Markdown の最後の見出し。これより深い節は宣言時に弾かれる") {
            arch.page("api/README.md") shouldBe
                """
                [アーキテクチャ](../README.md)

                # api

                ## 1

                ### 2

                #### 3

                ##### 4

                ###### 5

                いちばん下。
                """.trimIndent() + "\n"
        }
    }
})

/** The deepest section katachi accepts: `##` plus four steps down is `######`. */
private val Deepest = documentSection("1")
    .documentSection("2")
    .documentSection("3")
    .documentSection("4")
    .documentSection("5")

private var MetadataScope.deepest by Deepest
