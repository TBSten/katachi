package me.tbsten.katachi.test.docs

import io.kotest.assertions.withClue
import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.shouldBe
import me.tbsten.katachi.dsl.DeclarationContainerScope
import me.tbsten.katachi.dsl.Description
import me.tbsten.katachi.dsl.architecture

/** The root has no `description` property of its own, so a test writes one the way a processor would. */
private var DeclarationContainerScope.rootDescription by Description

/**
 * What a container says about itself, above the list of what it holds.
 *
 * `summary` and `description` mean on a group exactly what they mean on a role, so they are
 * placed where a role page places them: the one line straight under the heading, the free-form
 * body below it, and everything katachi assembles after both.
 *
 * NOTE: このファイルのパッケージを `me.tbsten.katachi.docs` にしてはいけない。
 * captureDeclarationSite() がライブラリ自身のフレームとして読み飛ばしてしまい、
 * 宣言位置が kotest 内部を指すようになる。
 */
class ContainerBodySpec : FreeSpec({
    "group の description" - {
        "見出しの直後に、改行ごとそのまま出る" {
            val arch = architecture {
                "api".group {
                    title = "API"
                    description = """
                        HTTP に面するものを集めた層。

                        ### 置かないもの
                        - ドメインの判断
                    """.trimIndent()
                    "Controller" { }
                }
            }

            withClue("役割ページの description と同じ位置・同じ扱い") {
                arch.page("api/README.md") shouldBe
                    """
                    [アーキテクチャ](../README.md)

                    # API

                    HTTP に面するものを集めた層。

                    ### 置かないもの
                    - ドメインの判断

                    | 役割 | 概要 |
                    |---|---|
                    | [Controller](./Controller.md) |  |
                    """.trimIndent() + "\n"
            }
        }

        "summary が先、description が後に並ぶ" {
            val arch = architecture {
                "api".group {
                    title = "API"
                    summary = "HTTP に面するもの"
                    description = "本文。"
                }
            }

            arch.page("api/README.md") shouldBe
                """
                [アーキテクチャ](../README.md)

                # API

                HTTP に面するもの

                本文。
                """.trimIndent() + "\n"
        }

        "前後の空行は落として差し込む" {
            val arch = architecture {
                "api".group { description = "\n\n本文。\n\n" }
            }

            arch.page("api/README.md") shouldBe
                """
                [アーキテクチャ](../README.md)

                # api

                本文。
                """.trimIndent() + "\n"
        }

        "どちらも書かなければ、見出しの下には何も足さない" {
            val arch = architecture { "api".group { } }

            arch.page("api/README.md") shouldBe "[アーキテクチャ](../README.md)\n\n# api\n"
        }
    }

    "ルートの README にも同じ位置で出る" {
        val arch = architecture {
            rootDescription = "このリポジトリの全体像。"
            "api".group { }
        }

        arch.page("README.md") shouldBe
            """
            # アーキテクチャ

            このリポジトリの全体像。

            ## グループ

            - [api](./api/README.md)
            """.trimIndent() + "\n"
    }
})
