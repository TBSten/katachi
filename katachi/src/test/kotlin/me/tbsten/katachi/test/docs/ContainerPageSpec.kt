package me.tbsten.katachi.test.docs

import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import me.tbsten.katachi.dsl.architecture

/**
 * What a `README.md` says: the root's, and a group's.
 *
 * NOTE: このファイルのパッケージを `me.tbsten.katachi.docs` にしてはいけない。
 * captureDeclarationSite() がライブラリ自身のフレームとして読み飛ばしてしまい、
 * 宣言位置が kotest 内部を指すようになる。
 */
class ContainerPageSpec : FreeSpec({
    "ルートの README" - {
        "group が一覧としてリンクで並ぶ" {
            val arch = architecture {
                "domain".group { title = "ドメイン" }
                "data".group { title = "データ" }
            }

            arch.page("README.md") shouldBe
                """
                # アーキテクチャ

                ## グループ

                - [ドメイン](./domain/README.md)
                - [データ](./data/README.md)
                """.trimIndent() + "\n"
        }

        "ルート直下の役割は概要つきの表になる" {
            val arch = architecture {
                "Readme" {
                    title = "リードミー"
                    summary = "リポジトリの入口"
                }
                "domain".group { }
            }

            arch.page("README.md") shouldBe
                """
                # アーキテクチャ

                | 役割 | 概要 |
                |---|---|
                | [リードミー](./Readme.md) | リポジトリの入口 |

                ## グループ

                - [domain](./domain/README.md)
                """.trimIndent() + "\n"
        }

        "group が1つも無ければグループの節ごと出ない" {
            val arch = architecture { "Readme" { } }

            arch.page("README.md") shouldNotContain "## グループ"
        }

        "何も宣言していなければ見出しだけになる" {
            val arch = architecture { }

            arch.page("README.md") shouldBe "# アーキテクチャ\n"
        }

        "documented = false のルート直下の役割は一覧からもページからも消える" {
            val arch = architecture {
                "Readme" { }
                "Scratch" { documented = false }
            }

            arch.page("README.md") shouldNotContain "Scratch"
            arch.documents().keys.toList() shouldBe listOf("README.md", "Readme.md")
        }
    }

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
})
