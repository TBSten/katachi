package me.tbsten.katachi.test.dsl

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.assertions.withClue
import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import me.tbsten.katachi.dsl.DeclarationContainerScope
import me.tbsten.katachi.dsl.KatachiDocumentSectionTooDeepException
import me.tbsten.katachi.dsl.KatachiUnnamedDocumentSectionException
import me.tbsten.katachi.dsl.MetadataScope
import me.tbsten.katachi.dsl.architecture
import me.tbsten.katachi.dsl.documentSection

/**
 * The two lines a user writes to add a section of their own, spelled exactly as they would
 * write them: the declaration carries no type and no opt-in annotation.
 */
private val TestPolicy = documentSection("テスト方針")
private var DeclarationContainerScope.testPolicy by TestPolicy

/** A section below another one. `MetadataScope` so that a role can carry it too. */
private val UnitTest = TestPolicy.documentSection("ユニットテスト")
private var MetadataScope.unitTest by UnitTest

/** A second section with the very same heading, to show that a heading does not identify one. */
private val OtherTestPolicy = documentSection("テスト方針")
private var MetadataScope.otherTestPolicy by OtherTestPolicy

class DocumentSectionSpec : FreeSpec({
    "書いた節を読み戻せる" - {
        "group に書いた節" {
            val arch = architecture {
                "api".group { testPolicy = "- ステータスコード" }
            }

            arch.groups.single()[TestPolicy] shouldBe "- ステータスコード"
        }

        "役割に書いた節" {
            val arch = architecture {
                "api".group {
                    "Controller" { unitTest = "リクエストの変換だけを見る。" }
                }
            }

            arch.allRoles.single()[UnitTest] shouldBe "リクエストの変換だけを見る。"
        }

        "architecture { } 直下に書いた節" {
            val arch = architecture { testPolicy = "リポジトリ全体の方針。" }

            withClue("var DeclarationContainerScope.testPolicy はルートにも届く") {
                arch[TestPolicy] shouldBe "リポジトリ全体の方針。"
            }
        }

        "書いていない節は null になる" {
            val arch = architecture { "api".group { "Controller" { } } }

            arch.groups.single()[TestPolicy] shouldBe null
            arch.allRoles.single()[UnitTest] shouldBe null
        }

        "null を書くと、書かなかったのと同じになる" {
            val arch = architecture {
                "api".group {
                    testPolicy = "いちど書く。"
                    testPolicy = null
                }
            }

            arch.groups.single()[TestPolicy] shouldBe null
        }

        "見出しが同じでも、別の節は別の値を持つ" {
            val arch = architecture {
                "api".group {
                    testPolicy = "こちら。"
                    otherTestPolicy = "あちら。"
                }
            }

            val group = arch.groups.single()
            group[TestPolicy] shouldBe "こちら。"
            group[OtherTestPolicy] shouldBe "あちら。"
        }

        "group に書いた節は配下の役割に継承されない" {
            val arch = architecture {
                "api".group {
                    testPolicy = "グループの方針。"
                    "Controller" { }
                }
            }

            arch.groups.single()[TestPolicy] shouldBe "グループの方針。"
            arch.allRoles.single()[TestPolicy] shouldBe null
        }
    }

    "入れ子" - {
        "親は節そのものであって、名前ではない" {
            UnitTest.parent shouldBe TestPolicy
            TestPolicy.parent shouldBe null
        }

        "見出しは宣言したまま持っている" {
            UnitTest.heading shouldBe "ユニットテスト"
        }

        "6段目の入れ子は宣言した時点で落ちる" {
            var section = documentSection("h2")
            repeat(4) { section = section.documentSection("さらに下") }

            val thrown = shouldThrow<KatachiDocumentSectionTooDeepException> {
                section.documentSection("深すぎる")
            }

            withClue("Markdown の見出しは h6 までで、それより下は見出しとして描画されない") {
                thrown.depth shouldBe 5
                thrown.message.orEmpty() shouldContain "深すぎる"
            }
        }
    }

    "見出しの無い節は宣言した時点で落ちる" {
        val thrown = shouldThrow<KatachiUnnamedDocumentSectionException> { documentSection("  ") }

        thrown.message.orEmpty() shouldContain "DocumentSectionSpec.kt"
    }
})
