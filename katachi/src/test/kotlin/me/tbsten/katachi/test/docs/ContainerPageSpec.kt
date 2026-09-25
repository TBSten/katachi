package me.tbsten.katachi.test.docs

import io.kotest.assertions.withClue
import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import me.tbsten.katachi.dsl.architecture

/**
 * What a group's `README.md` says. The root's is [RootPageSpec].
 *
 * NOTE: このファイルのパッケージを `me.tbsten.katachi.docs` にしてはいけない。
 * captureDeclarationSite() がライブラリ自身のフレームとして読み飛ばしてしまい、
 * 宣言位置が kotest 内部を指すようになる。
 */
class ContainerPageSpec : FreeSpec({
    "group の README" - {
        "見出しは title、リンク先は識別子になる" {
            val arch = architecture {
                "domain".group {
                    title = "ドメイン"
                    "UseCase" {
                        title = "ユースケース"
                        summary = "各画面で発生するアプリ固有の1つの振る舞い"
                    }
                }
            }

            arch.page("domain/README.md") shouldBe
                """
                [アーキテクチャ](../README.md)

                # ドメイン

                | 役割 | 概要 |
                |---|---|
                | [ユースケース](./UseCase.md) | 各画面で発生するアプリ固有の1つの振る舞い |
                """.trimIndent() + "\n"
        }

        "title が無ければ識別子がそのまま見出しになる" {
            val arch = architecture { "domain".group { "UseCase" { } } }

            arch.page("domain/README.md") shouldContain "# domain"
        }

        "summary が無い役割の概要欄は空になる" {
            val arch = architecture { "domain".group { "UseCase" { } } }

            arch.page("domain/README.md") shouldContain "| [UseCase](./UseCase.md) |  |"
        }

        "ネストした group は自分の README へのリンクとして並ぶ" {
            val arch = architecture {
                "domain".group {
                    "UseCase" { }
                    "model".group { title = "モデル" }
                }
            }

            arch.page("domain/README.md") shouldBe
                """
                [アーキテクチャ](../README.md)

                # domain

                | 役割 | 概要 |
                |---|---|
                | [UseCase](./UseCase.md) |  |

                ## グループ

                - [モデル](./model/README.md)
                """.trimIndent() + "\n"
        }

        "表を壊す文字は書いたとおりに出さず、エスケープして出す" {
            val arch = architecture {
                "domain".group {
                    "UseCase" {
                        title = "a | b"
                        summary = "1行目\n2行目"
                    }
                }
            }

            arch.page("domain/README.md") shouldContain "| [a \\| b](./UseCase.md) | 1行目 2行目 |"
        }

        "documented = false の役割は一覧からも消える" {
            val arch = architecture {
                "domain".group {
                    "UseCase" { }
                    "Internal" { documented = false }
                }
            }

            arch.page("domain/README.md") shouldNotContain "Internal"
        }
    }

    "パンくず" - {
        "ルートの README には出ない" {
            val arch = architecture { "domain".group { } }

            withClue("親がいないので、戻る先が無い") {
                arch.page("README.md") shouldBe
                    """
                    # アーキテクチャ ドキュメント

                    ## Document map

                    ### [domain](./domain/README.md)

                    ## domain
                    """.trimIndent() + "\n"
            }
        }

        "深さ1の group にはルートへの1段だけが出る" {
            val arch = architecture { "domain".group { title = "ドメイン" } }

            arch.page("domain/README.md") shouldBe
                """
                [アーキテクチャ](../README.md)

                # ドメイン
                """.trimIndent() + "\n"
        }

        "ネストした group にはルートから親までが順に出る" {
            val arch = architecture {
                "ui".group {
                    title = "UI"
                    "screen".group { title = "画面" }
                }
            }

            arch.page("ui/screen/README.md") shouldBe
                """
                [アーキテクチャ](../../README.md) / [UI](../README.md)

                # 画面
                """.trimIndent() + "\n"
        }

        "深さ3でも段数だけが増える" {
            val arch = architecture {
                "a".group {
                    "b".group {
                        "c".group { }
                    }
                }
            }

            arch.page("a/b/c/README.md") shouldBe
                """
                [アーキテクチャ](../../../README.md) / [a](../../README.md) / [b](../README.md)

                # c
                """.trimIndent() + "\n"
        }

        "リンクテキストは title、リンク先は識別子から決まる" {
            val arch = architecture {
                "ui".group {
                    title = "ユーザーインターフェース"
                    "screen".group { }
                }
            }

            withClue("title を変えてもパスは ui/ のまま") {
                arch.page("ui/screen/README.md") shouldContain
                    "[ユーザーインターフェース](../README.md)"
            }
        }

        "リンクテキストを壊す文字はエスケープされる" {
            val arch = architecture {
                "ui".group {
                    title = "a] b"
                    "screen".group { }
                }
            }

            arch.page("ui/screen/README.md") shouldContain "[a\\] b](../README.md)"
        }

        "パンくずもリンク切れ検査に掛かる" {
            val arch = architecture {
                "ui".group {
                    "screen".group { "Screen" { } }
                }
            }

            withClue("解決しないパンくずがあれば documents() が例外で落ちる") {
                arch.documents().keys.toList() shouldBe listOf(
                    "README.md",
                    "ui/README.md",
                    "ui/screen/README.md",
                    "ui/screen/Screen.md",
                )
            }
        }
    }
})
