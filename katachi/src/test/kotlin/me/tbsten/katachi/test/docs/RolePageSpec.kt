package me.tbsten.katachi.test.docs

import io.kotest.assertions.withClue
import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import me.tbsten.katachi.dsl.architecture
import me.tbsten.katachi.dsl.kotlin.ktFile

/**
 * What a role's own page says, section by section.
 *
 * The placement table has a spec of its own, [PlacementSpec], because what goes in its two
 * left-hand columns is a question about the layout rather than about the page.
 *
 * NOTE: このファイルのパッケージを `me.tbsten.katachi.docs` にしてはいけない。
 * captureDeclarationSite() がライブラリ自身のフレームとして読み飛ばしてしまい、
 * 宣言位置が kotest 内部を指すようになる。
 */
class RolePageSpec : FreeSpec({
    "見出しと summary" - {
        "見出しは title、その下に summary が1行で出る" {
            val arch = architecture {
                "domain".group {
                    "UseCase" {
                        title = "ユースケース"
                        summary = "各画面で発生するアプリ固有の1つの振る舞いを表す。"
                    }
                }
            }

            arch.page("domain/UseCase.md") shouldBe
                """
                # ユースケース

                各画面で発生するアプリ固有の1つの振る舞いを表す。
                """.trimIndent() + "\n"
        }

        "title が無ければ識別子が見出しになる" {
            val arch = architecture { "domain".group { "UseCase" { } } }

            arch.page("domain/UseCase.md") shouldBe "# UseCase\n"
        }
    }

    "description" - {
        "見出しと summary の直後に、改行ごとそのまま出る" {
            val arch = architecture {
                "domain".group {
                    "UseCase" {
                        title = "ユースケース"
                        summary = "1つの振る舞い"
                        description = """
                            UI はユースケースだけを呼ぶ。

                            ### やっていいこと
                            - 複数のリポジトリにまたがること
                        """.trimIndent()
                    }
                }
            }

            withClue("見出しレベルを katachi が動かすと、そのまま保持するという約束が崩れる") {
                arch.page("domain/UseCase.md") shouldBe
                    """
                    # ユースケース

                    1つの振る舞い

                    UI はユースケースだけを呼ぶ。

                    ### やっていいこと
                    - 複数のリポジトリにまたがること
                    """.trimIndent() + "\n"
            }
        }

        "前後の空行は落として差し込む" {
            val arch = architecture {
                "domain".group {
                    "UseCase" { description = "\n\n本文。\n\n" }
                }
            }

            arch.page("domain/UseCase.md") shouldBe
                """
                # UseCase

                本文。
                """.trimIndent() + "\n"
        }

        "summary が無くても見出しの直後に出る" {
            val arch = architecture {
                "domain".group {
                    "UseCase" { description = "本文だけを書いた役割。" }
                }
            }

            arch.page("domain/UseCase.md") shouldBe
                """
                # UseCase

                本文だけを書いた役割。
                """.trimIndent() + "\n"
        }
    }

    "## 制約" - {
        "名前を付けた制約だけが並ぶ" {
            val arch = architecture {
                "domain".group {
                    "UseCase" {
                        constraint("invoke を持つこと") { emptyList() }
                        constraint { emptyList() }
                        layout { "useCase" / "*UseCase".ktFile() }
                    }
                }
            }

            withClue("名前を書けばドキュメントに載る、というのが名前を書く動機になっている") {
                arch.page("domain/UseCase.md") shouldContain
                    """
                    ## 制約

                    - invoke を持つこと
                    """.trimIndent()
            }
        }

        "layout の中で名前を付けた制約も出る" {
            val arch = architecture {
                "domain".group {
                    "UseCase" {
                        layout {
                            "useCase" {
                                constraint("public であること") { emptyList() }
                                "*UseCase".ktFile()
                            }
                        }
                    }
                }
            }

            arch.page("domain/UseCase.md") shouldContain "- public であること"
        }

        "役割の制約が先、layout の中の制約が後に並ぶ" {
            val arch = architecture {
                "domain".group {
                    "UseCase" {
                        constraint("役割ぜんたいの制約") { emptyList() }
                        layout {
                            "useCase" {
                                constraint("この置き場所だけの制約") { emptyList() }
                                "*UseCase".ktFile()
                            }
                        }
                    }
                }
            }

            arch.page("domain/UseCase.md") shouldContain
                """
                - 役割ぜんたいの制約
                - この置き場所だけの制約
                """.trimIndent()
        }

        "名前付きの制約が1つも無ければ節ごと出ない" {
            val arch = architecture {
                "domain".group {
                    "UseCase" {
                        constraint { emptyList() }
                        layout { "useCase" / "*UseCase".ktFile() }
                    }
                }
            }

            arch.page("domain/UseCase.md") shouldNotContain "## 制約"
        }

        "他の役割の制約は混ざらない" {
            val arch = architecture {
                "domain".group {
                    "UseCase" {
                        constraint("ユースケースの制約") { emptyList() }
                        layout { "useCase" / "*UseCase".ktFile() }
                    }
                    "Repository" {
                        constraint("リポジトリの制約") { emptyList() }
                        layout { "repository" / "*Repository".ktFile() }
                    }
                }
            }

            arch.page("domain/UseCase.md") shouldNotContain "リポジトリの制約"
        }
    }

    "## 例" - {
        "example が 名前 ... 説明 の形で並ぶ" {
            val arch = architecture {
                "domain".group {
                    "UseCase" {
                        example("GetRecommendedProductListUseCase", "おすすめの商品リストを取得する")
                        example("ToggleProductFavorite", "商品のいいね状態を切り替える")
                    }
                }
            }

            arch.page("domain/UseCase.md") shouldContain
                """
                ## 例

                - `GetRecommendedProductListUseCase` ... おすすめの商品リストを取得する
                - `ToggleProductFavorite` ... 商品のいいね状態を切り替える
                """.trimIndent()
        }

        "example が1つも無ければ節ごと出ない" {
            val arch = architecture { "domain".group { "UseCase" { } } }

            arch.page("domain/UseCase.md") shouldNotContain "## 例"
        }
    }

    "節の並び" {
        val arch = architecture {
            "domain".group {
                "UseCase" {
                    summary = "1つの振る舞い"
                    description = "本文。"
                    example("GetUserUseCase", "ユーザーを取得する")
                    constraint("public であること") { emptyList() }
                    layout { "useCase" / "*UseCase".ktFile() }
                }
            }
        }

        arch.page("domain/UseCase.md") shouldBe
            """
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
            """.trimIndent() + "\n"
    }
})
