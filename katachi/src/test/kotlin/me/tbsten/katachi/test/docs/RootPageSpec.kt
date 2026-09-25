package me.tbsten.katachi.test.docs

import io.kotest.assertions.withClue
import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import me.tbsten.katachi.dsl.Title
import me.tbsten.katachi.dsl.architecture

/**
 * What the root `README.md` says: the index of the whole output, and a section per group.
 *
 * Every page here is compared in full rather than searched for a substring. The root page is the
 * one a reader arrives at knowing nothing, so what is *not* on it matters as much as what is, and
 * only a whole-page comparison says both.
 *
 * NOTE: このファイルのパッケージを `me.tbsten.katachi.docs` にしてはいけない。
 * captureDeclarationSite() がライブラリ自身のフレームとして読み飛ばしてしまい、
 * 宣言位置が kotest 内部を指すようになる。
 */
class RootPageSpec : FreeSpec({
    "ページ全体" - {
        "索引が先、group ごとの節が後ろに並ぶ" {
            val arch = architecture {
                title = "myapp"
                description = "このリポジトリの構成。"
                "Changelog" { }
                "api".group {
                    title = "API"
                    "Controller" { summary = "HTTP のリクエストを1つ受け取る" }
                    "KtorPlugin" { summary = "Ktor の Application への横断的な設定" }
                }
                "domain".group {
                    title = "ドメイン"
                    "Service" { summary = "アプリ固有の振る舞い" }
                }
            }

            arch.rootReadme() shouldBe
                """
                # myapp ドキュメント

                このリポジトリの構成。

                ## Document map

                - [Changelog](./Changelog.md)

                ### [API](./api/README.md)

                - [Controller](./api/Controller.md)
                - [KtorPlugin](./api/KtorPlugin.md)

                ### [ドメイン](./domain/README.md)

                - [Service](./domain/Service.md)

                ## ルート直下の役割

                - [Changelog](./Changelog.md)

                ## API

                - [Controller](./api/Controller.md) ... HTTP のリクエストを1つ受け取る
                - [KtorPlugin](./api/KtorPlugin.md) ... Ktor の Application への横断的な設定

                ## ドメイン

                - [Service](./domain/Service.md) ... アプリ固有の振る舞い
                """.trimIndent() + "\n"
        }

        "何も宣言していなければ見出しだけになる" {
            withClue("中身の無い ## Document map は、見出しが無いより読み手に伝えるものが少ない") {
                architecture { }.rootReadme() shouldBe "# アーキテクチャ ドキュメント\n"
            }
        }
    }

    "Document map" - {
        "group に属さない役割が無ければ、冒頭の箇条書きごと出ない" {
            val arch = architecture { "api".group { "Controller" { } } }

            arch.rootReadme() shouldBe
                """
                # アーキテクチャ ドキュメント

                ## Document map

                ### [api](./api/README.md)

                - [Controller](./api/Controller.md)

                ## api

                - [Controller](./api/Controller.md)
                """.trimIndent() + "\n"
        }

        "group が1つも無ければ見出しも group の節も出ない" {
            val arch = architecture { "Changelog" { summary = "リリースごとの変更点" } }

            withClue("索引なので summary は書かない。名前だけを追えることのほうが役に立つ") {
                arch.rootReadme() shouldBe
                    """
                    # アーキテクチャ ドキュメント

                    ## Document map

                    - [Changelog](./Changelog.md)

                    ## ルート直下の役割

                    - [Changelog](./Changelog.md) ... リリースごとの変更点
                    """.trimIndent() + "\n"
            }
        }

        "documented = false の役割も group も載らない" {
            val arch = architecture {
                "Changelog" { }
                "Scratch" { documented = false }
                "build".group { documented = false }
            }

            arch.rootReadme() shouldNotContain "Scratch"
            arch.rootReadme() shouldNotContain "build"
            arch.documents().keys.toList() shouldContainExactly listOf("README.md", "Changelog.md")
        }

        "ネストした group は1段深い見出しになり、group の節の中では ### になる" {
            val arch = architecture {
                "domain".group {
                    title = "ドメイン"
                    "UseCase" { summary = "1つの振る舞い" }
                    "model".group {
                        title = "モデル"
                        "Entity" { }
                    }
                }
            }

            arch.rootReadme() shouldBe
                """
                # アーキテクチャ ドキュメント

                ## Document map

                ### [ドメイン](./domain/README.md)

                - [UseCase](./domain/UseCase.md)

                #### [モデル](./domain/model/README.md)

                - [Entity](./domain/model/Entity.md)

                ## ドメイン

                - [UseCase](./domain/UseCase.md) ... 1つの振る舞い

                ### モデル

                - [Entity](./domain/model/Entity.md)
                """.trimIndent() + "\n"
        }

        "###### を超える深さは見出しを増やさず、箇条書きの入れ子になる" {
            val arch = architecture {
                "a".group { "b".group { "c".group { "d".group { "e".group { "Deep" { } } } } } }
            }

            withClue("####### は見出しではなく、ただのハッシュ7個として出てしまう") {
                arch.rootReadme() shouldBe
                    """
                    # アーキテクチャ ドキュメント

                    ## Document map

                    ### [a](./a/README.md)

                    #### [b](./a/b/README.md)

                    ##### [c](./a/b/c/README.md)

                    ###### [d](./a/b/c/d/README.md)

                    - [e](./a/b/c/d/e/README.md)
                      - [Deep](./a/b/c/d/e/Deep.md)

                    ## a

                    ### b

                    #### c

                    ##### d

                    ###### e

                    - [Deep](./a/b/c/d/e/Deep.md)
                    """.trimIndent() + "\n"
            }
        }
    }

    "group の節" - {
        "見出しの次に group の summary、その次に役割が並ぶ" {
            val arch = architecture {
                "api".group {
                    title = "API"
                    summary = "HTTP に面するものを集めた層"
                    description = "これは group 自身の README にだけ出る。"
                    "Controller" { summary = "HTTP のリクエストを1つ受け取る" }
                }
            }

            arch.rootReadme() shouldBe
                """
                # アーキテクチャ ドキュメント

                ## Document map

                ### [API](./api/README.md)

                HTTP に面するものを集めた層

                - [Controller](./api/Controller.md)

                ## API

                HTTP に面するものを集めた層

                - [Controller](./api/Controller.md) ... HTTP のリクエストを1つ受け取る
                """.trimIndent() + "\n"
        }

        "summary の無い役割には区切りごと付かない" {
            val arch = architecture {
                "api".group {
                    "Controller" { }
                    "KtorPlugin" { summary = "横断的な設定" }
                }
            }

            arch.rootReadme() shouldNotContain "- [Controller](./api/Controller.md) ..."
        }

        "summary が複数行でも箇条書きは1行のまま" {
            val arch = architecture {
                "api".group { "Controller" { summary = "1行目\n2行目" } }
            }

            withClue("改行がそのまま出ると、そこで箇条書きが終わってしまう") {
                arch.rootReadme() shouldContain "- [Controller](./api/Controller.md) ... 1行目 2行目"
            }
        }
    }

    "ルート直下の配置" - {
        "Document map の後ろ、group の節の前に出る" {
            val arch = architecture {
                "Changelog" {
                    title = "変更履歴"
                    layout { "CHANGELOG.md".file() }
                }
                "domain".group {
                    title = "ドメイン"
                    "UseCase" { }
                }
            }

            withClue("ツリーは配置の情報を持っていて、Document map はそれを持っていない") {
                arch.rootReadme() shouldBe
                    """
                    # アーキテクチャ ドキュメント

                    ## Document map

                    - [変更履歴](./Changelog.md)

                    ### [ドメイン](./domain/README.md)

                    - [UseCase](./domain/UseCase.md)

                    ## ルート直下の役割

                    - [変更履歴](./Changelog.md)

                    ## ルート直下の配置

                    ```
                    CHANGELOG.md  変更履歴
                    ```

                    ## ドメイン

                    - [UseCase](./domain/UseCase.md)
                    """.trimIndent() + "\n"
            }
        }
    }

    "title と description" - {
        "書かなければ既定の見出しになり、本文は出ない" {
            val arch = architecture { "api".group { } }

            arch.rootReadme() shouldBe
                """
                # アーキテクチャ ドキュメント

                ## Document map

                ### [api](./api/README.md)

                ## api
                """.trimIndent() + "\n"
        }

        "architecture { } に書けば見出しも本文も変わる" {
            val arch = architecture {
                title = "myapp"
                description = "このリポジトリの構成。"
            }

            arch.rootReadme() shouldBe
                """
                # myapp ドキュメント

                このリポジトリの構成。
                """.trimIndent() + "\n"
        }

        "空文字は「書かなかった」と同じ扱いになる" {
            val arch = architecture {
                title = "  "
                description = ""
            }

            withClue("組み立てた結果たまたま空になったとき、見出しの無いページを求めてはいない") {
                arch.rootReadme() shouldBe "# アーキテクチャ ドキュメント\n"
            }
        }

        "ルートの title は group や役割のものと同じ語" {
            val arch = architecture {
                title = "myapp"
                "api".group { title = "API" }
            }

            withClue("ルートだけ別の書き方だと、定義を読む人が覚えることが1つ増える") {
                arch[Title] shouldBe "myapp"
                arch.groups.single()[Title] shouldBe "API"
            }
        }
    }

    "リンク切れ検査を1件も鳴らさない" {
        val arch = architecture {
            "Changelog" { }
            "a".group { "b".group { "c".group { "d".group { "e".group { "Deep" { } } } } } }
            "domain".group {
                title = "ドメイン"
                "UseCase" { }
                "model".group { "Entity" { } }
            }
        }

        withClue("ルートが書くリンクが1つでも実在しないページを指していれば documents() が落ちる") {
            arch.documents().keys.toList() shouldContainExactly listOf(
                "README.md",
                "Changelog.md",
                "a/README.md",
                "a/b/README.md",
                "a/b/c/README.md",
                "a/b/c/d/README.md",
                "a/b/c/d/e/README.md",
                "a/b/c/d/e/Deep.md",
                "domain/README.md",
                "domain/UseCase.md",
                "domain/model/README.md",
                "domain/model/Entity.md",
            )
        }
    }
})
