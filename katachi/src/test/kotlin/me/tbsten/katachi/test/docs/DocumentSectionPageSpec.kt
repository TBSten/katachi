package me.tbsten.katachi.test.docs

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.assertions.withClue
import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import me.tbsten.katachi.docs.KatachiBrokenDocumentLinkException
import me.tbsten.katachi.dsl.MetadataScope
import me.tbsten.katachi.dsl.architecture
import me.tbsten.katachi.dsl.documentSection
import me.tbsten.katachi.dsl.kotlin.ktFile

/** The sections this spec's definitions write, declared the way a user declares them. */
private val TestPolicy = documentSection("テスト方針")
private var MetadataScope.testPolicy by TestPolicy

private val Ownership = documentSection("持ち主")
private var MetadataScope.ownership by Ownership

/**
 * Where a user's own sections land on a generated page, and what decides their order.
 *
 * What nesting adds is in [DocumentSectionNestingSpec].
 *
 * NOTE: このファイルのパッケージを `me.tbsten.katachi.docs` にしてはいけない。
 * captureDeclarationSite() がライブラリ自身のフレームとして読み飛ばしてしまい、
 * 宣言位置が kotest 内部を指すようになる。
 */
class DocumentSectionPageSpec : FreeSpec({
    "役割ページ" - {
        "組み込みの節を全部出したあと、書いた順に並ぶ" {
            val arch = architecture {
                "domain".group {
                    "UseCase" {
                        summary = "1つの振る舞い"
                        description = "本文。"
                        example("GetUserUseCase", "ユーザーを取得する")
                        fileConstraint("public であること") { emptyList() }
                        layout { "useCase" / "*UseCase".ktFile() }
                        testPolicy = "- 戻り値"
                        ownership = "platform"
                    }
                }
            }

            arch.page("domain/UseCase.md") shouldBe
                """
                [アーキテクチャ](../README.md) / [domain](README.md)

                # UseCase

                1つの振る舞い

                本文。

                ## 配置場所

                | モジュール | パス | 使い分け |
                |---|---|---|
                |  | `useCase/*UseCase.kt` |  |

                ## 制約

                - public であること

                ## 例

                - `GetUserUseCase` ... ユーザーを取得する

                ## テスト方針

                - 戻り値

                ## 持ち主

                platform
                """.trimIndent() + "\n"
        }

        "書く順を入れ替えると、出る順も入れ替わる" {
            val arch = architecture {
                "domain".group {
                    "UseCase" {
                        ownership = "platform"
                        testPolicy = "- 戻り値"
                    }
                }
            }

            arch.page("domain/UseCase.md") shouldBe
                """
                [アーキテクチャ](../README.md) / [domain](README.md)

                # UseCase

                ## 持ち主

                platform

                ## テスト方針

                - 戻り値
                """.trimIndent() + "\n"
        }

        "設定していない節は出ない" {
            val arch = architecture {
                "domain".group {
                    "UseCase" { ownership = "platform" }
                }
            }

            arch.page("domain/UseCase.md") shouldNotContain "テスト方針"
        }

        "本文はそのまま差し込まれ、前後の空行だけ落ちる" {
            val arch = architecture {
                "domain".group {
                    "UseCase" { testPolicy = "\n\n以下をテストする。\n\n- ステータスコード\n\n" }
                }
            }

            withClue("description と同じ約束。見出しレベルも段落も katachi は動かさない") {
                arch.page("domain/UseCase.md") shouldBe
                    """
                    [アーキテクチャ](../README.md) / [domain](README.md)

                    # UseCase

                    ## テスト方針

                    以下をテストする。

                    - ステータスコード
                    """.trimIndent() + "\n"
            }
        }

        "本文が空でも、書いた以上は見出しが出る" {
            val arch = architecture {
                "domain".group {
                    "UseCase" { testPolicy = "" }
                }
            }

            withClue("書かないことは null で言う。空文字は「書いたが中身がまだ無い」") {
                arch.page("domain/UseCase.md") shouldBe
                    """
                    [アーキテクチャ](../README.md) / [domain](README.md)

                    # UseCase

                    ## テスト方針
                    """.trimIndent() + "\n"
            }
        }

        "同じ節に2回書くと、最後の本文が最初に書いた位置に出る" {
            val arch = architecture {
                "domain".group {
                    "UseCase" {
                        testPolicy = "はじめに書いた本文。"
                        ownership = "platform"
                        testPolicy = "あとから書いた本文。"
                    }
                }
            }

            arch.page("domain/UseCase.md") shouldBe
                """
                [アーキテクチャ](../README.md) / [domain](README.md)

                # UseCase

                ## テスト方針

                あとから書いた本文。

                ## 持ち主

                platform
                """.trimIndent() + "\n"
        }
    }

    "group と役割の両方に書ける" {
        val arch = architecture {
            "api".group {
                testPolicy = "グループ全体の方針。"
                "Controller" { testPolicy = "この役割だけの方針。" }
            }
        }

        arch.page("api/README.md") shouldContain "グループ全体の方針。"
        arch.page("api/Controller.md") shouldContain "この役割だけの方針。"
        withClue("group に書いた節が配下の役割のページに混ざらない") {
            arch.page("api/Controller.md") shouldNotContain "グループ全体の方針。"
        }
    }

    "ルートの README にも、katachi が組み立てた節すべての後ろに出る" {
        val arch = architecture {
            testPolicy = "リポジトリ全体の方針。"
            "api".group { }
        }

        arch.page("README.md") shouldBe
            """
            # アーキテクチャ ドキュメント

            ## Document map

            ### [api](./api/README.md)

            ## api

            ## テスト方針

            リポジトリ全体の方針。
            """.trimIndent() + "\n"
    }

    "節の本文もリンク切れ検査に掛かる" - {
        "解決しない相対リンクを書くと落ちる" {
            val arch = architecture {
                "api".group {
                    testPolicy = "[存在しないページ](./Nope.md) を見ること。"
                    "Controller" { }
                }
            }

            val thrown = shouldThrow<KatachiBrokenDocumentLinkException> { arch.documents() }

            thrown.message.orEmpty() shouldContain "./Nope.md"
        }

        "実在するページを指す相対リンクは通る" {
            val arch = architecture {
                "api".group {
                    testPolicy = "[Controller](./Controller.md) を見ること。"
                    "Controller" { }
                }
            }

            arch.page("api/README.md") shouldContain "[Controller](./Controller.md)"
        }
    }
})
