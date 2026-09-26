package me.tbsten.katachi.test.docs

import io.kotest.assertions.throwables.shouldNotThrowAny
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.assertions.withClue
import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import me.tbsten.katachi.docs.KatachiBrokenDocumentLinkException
import me.tbsten.katachi.docs.KatachiDocumentPathCaseCollisionException
import me.tbsten.katachi.docs.internal.checkDocumentLinks
import me.tbsten.katachi.dsl.architecture
import me.tbsten.katachi.dsl.gradle.div
import me.tbsten.katachi.dsl.gradle.kotlin
import me.tbsten.katachi.dsl.gradle.mainSourceSet
import me.tbsten.katachi.dsl.gradle.module
import me.tbsten.katachi.dsl.kotlin.ktFile

/**
 * The last step of generating documentation: every relative link lands on a page that exists.
 *
 * Half of the specs below hand [checkDocumentLinks] a map built by hand rather than by a
 * definition. That is deliberate — the resolution has to work for any two positions, and a
 * definition can only produce the positions katachi happens to write links between today.
 *
 * NOTE: このファイルのパッケージを `me.tbsten.katachi.docs` にしてはいけない。
 * captureDeclarationSite() がライブラリ自身のフレームとして読み飛ばしてしまい、
 * 宣言位置が kotest 内部を指すようになる。
 */
class LinkCheckSpec : FreeSpec({
    "相対パスの解決" - {
        "同じディレクトリのページへのリンクが解決できる" {
            shouldNotThrowAny {
                checkDocumentLinks(
                    mapOf(
                        "domain/README.md" to "[ユースケース](./UseCase.md)",
                        "domain/UseCase.md" to "",
                    ),
                )
            }
        }

        "子の group へ降りるリンクが解決できる" {
            shouldNotThrowAny {
                checkDocumentLinks(
                    mapOf(
                        "domain/README.md" to "[モデル](./model/README.md)",
                        "domain/model/README.md" to "",
                    ),
                )
            }
        }

        "ネストした group から親へ登るリンクが解決できる" {
            shouldNotThrowAny {
                checkDocumentLinks(
                    mapOf(
                        "domain/model/README.md" to "[ドメイン](../README.md)",
                        "domain/README.md" to "",
                    ),
                )
            }
        }

        "ネストした group から兄弟の group へのリンクが解決できる" {
            withClue("深さ2から深さ1へ登って降りるので、固定の ./ でも ../ でも書けない") {
                shouldNotThrowAny {
                    checkDocumentLinks(
                        mapOf(
                            "domain/model/README.md" to "[データ](../../data/README.md)",
                            "data/README.md" to "",
                        ),
                    )
                }
            }
        }

        "深さが違えば同じページを指すリンクの書き方も変わる" {
            withClue("どちらも README.md を指しているのに、段数が違う") {
                shouldNotThrowAny {
                    checkDocumentLinks(
                        mapOf(
                            "README.md" to "",
                            "domain/README.md" to "[ルート](../README.md)",
                            "domain/model/README.md" to "[ルート](../../README.md)",
                        ),
                    )
                }
            }
        }

        "./ が無い相対パスも解決する" {
            shouldNotThrowAny {
                checkDocumentLinks(
                    mapOf(
                        "domain/README.md" to "[ユースケース](UseCase.md)",
                        "domain/UseCase.md" to "",
                    ),
                )
            }
        }

        "アンカーは落としてからページを探す" {
            shouldNotThrowAny {
                checkDocumentLinks(
                    mapOf(
                        "domain/README.md" to "[Placement](./UseCase.md#placement)",
                        "domain/UseCase.md" to "",
                    ),
                )
            }
        }

        "出力ルートより上に出るリンクは解決できない" {
            val exception = shouldThrow<KatachiBrokenDocumentLinkException> {
                checkDocumentLinks(mapOf("README.md" to "[外](../outside.md)"))
            }

            exception.links.single().resolved shouldBe null
            exception.message.orEmpty() shouldContain "-> outside the output root"
        }
    }

    "検査しないもの" - {
        "外部 URL は見ない" {
            shouldNotThrowAny {
                checkDocumentLinks(
                    mapOf(
                        "README.md" to "[katachi](https://github.com/TBSten/katachi) " +
                            "[問い合わせ](mailto:someone@example.com) " +
                            "[CDN](//example.com/style.css)",
                    ),
                )
            }
        }

        "ページ内アンカーは見ない" {
            withClue("見出しは利用者の title から出るので、katachi が約束できるものではない") {
                shouldNotThrowAny { checkDocumentLinks(mapOf("README.md" to "[Placement](#placement)")) }
            }
        }

        "サイトルートからの絶対パスは見ない" {
            shouldNotThrowAny { checkDocumentLinks(mapOf("README.md" to "[規約](/docs/CONTRIBUTING.md)")) }
        }

        "コードフェンスの中は走査しない" {
            withClue("配置ツリーはフェンスの中にあり、パスはリンクの材料と同じ文字でできている") {
                shouldNotThrowAny {
                    checkDocumentLinks(
                        mapOf(
                            "README.md" to "```\n[これはリンクではない](./missing.md)\n```",
                        ),
                    )
                }
            }
        }

        "フェンスが閉じたあとのリンクは走査する" {
            val exception = shouldThrow<KatachiBrokenDocumentLinkException> {
                checkDocumentLinks(mapOf("README.md" to "```\ntree\n```\n\n[外](./missing.md)"))
            }

            exception.links.single().target shouldBe "./missing.md"
        }

        "インラインコードの中は走査しない" {
            shouldNotThrowAny {
                checkDocumentLinks(mapOf("README.md" to "書き方は `[名前](./Role.md)` のようになる"))
            }
        }
    }

    "リンク切れの報告" - {
        "リンク元・リンク先・件数がメッセージに出る" {
            val exception = shouldThrow<KatachiBrokenDocumentLinkException> {
                checkDocumentLinks(mapOf("domain/README.md" to "[ユースケース](./UseCase.md)"))
            }

            exception.message.orEmpty() shouldContain
                "1 documentation link points at a page katachi did not generate."
            exception.message.orEmpty() shouldContain
                "domain/README.md: [ユースケース](./UseCase.md) -> domain/UseCase.md"
        }

        "複数のリンク切れが1つの例外にまとまる" {
            val exception = shouldThrow<KatachiBrokenDocumentLinkException> {
                checkDocumentLinks(
                    mapOf(
                        "README.md" to "[あ](./a.md) と [い](./b.md)",
                        "domain/README.md" to "[う](./c.md)",
                    ),
                )
            }

            withClue("1件ずつ落ちると、直すのに生成を3回やり直すことになる") {
                exception.links.map { it.resolved } shouldContainExactly
                    listOf("a.md", "b.md", "domain/c.md")
            }
            exception.message.orEmpty() shouldContain
                "3 documentation links point at pages katachi did not generate."
        }

        "リンク元とリンクテキストがそのまま読める" {
            val exception = shouldThrow<KatachiBrokenDocumentLinkException> {
                checkDocumentLinks(mapOf("domain/model/README.md" to "[ドメイン](../README.md)"))
            }

            val link = exception.links.single()
            link.source shouldBe "domain/model/README.md"
            link.text shouldBe "ドメイン"
            link.target shouldBe "../README.md"
            link.resolved shouldBe "domain/README.md"
        }
    }

    "生成したドキュメント" - {
        "ネストした group を含む定義でも1件も検出されない" {
            val arch = architecture {
                "Changelog" { title = "変更履歴" }
                "domain".group {
                    title = "ドメイン"
                    "UseCase" { title = "ユースケース" }
                    "model".group {
                        title = "モデル"
                        "Entity" { title = "エンティティ" }
                    }
                }
                "data".group { "Repository" { } }
            }

            withClue("正常な定義で1件でも出るなら、それは検査のほうが間違っている") {
                shouldNotThrowAny { arch.documents() }
            }
        }

        "documented = false の group へのリンクは生成されない" {
            val arch = architecture {
                "build".group {
                    documented = false
                    "Convention" { }
                }
                "domain".group { }
            }

            withClue("ページの無い group を一覧に出したら、その瞬間にリンク切れになる") {
                shouldNotThrowAny { arch.documents() }
            }
        }

        "description に書いた相対リンクもリンク切れとして報告される" {
            val arch = architecture {
                "domain".group {
                    "UseCase" { description = "詳しくは [規約](../CONTRIBUTING.md) を読む。" }
                }
            }

            val exception = shouldThrow<KatachiBrokenDocumentLinkException> { arch.documents() }

            exception.links.single().source shouldBe "domain/UseCase.md"
            exception.message.orEmpty() shouldContain "written by hand in a role's `description`"
        }

        "ツリーに出る役割は、すぐ上の表にもリンクとして出る" {
            val arch = architecture {
                "domain".group {
                    "UseCase" {
                        title = "ユースケース"
                        layout { ":core:domain".module { mainSourceSet / kotlin / "*UseCase".ktFile() } }
                    }
                    "Repository" {
                        title = "リポジトリ"
                        layout { ":core:domain".module { mainSourceSet / kotlin / "*Repository".ktFile() } }
                    }
                }
            }

            val page = arch.page("domain/README.md")
            val namesInTree = page.substringAfter("```\n").substringBefore("\n```")
                .lines()
                .mapNotNull { it.trim().split(COLUMN_GAP).getOrNull(1) }

            namesInTree shouldContainExactly listOf("ユースケース", "リポジトリ")
            for (name in namesInTree) {
                withClue("ツリーの役割名はリンクではないので、辿る手段は上の表のリンクしかない") {
                    page shouldContain "[$name](./"
                }
            }
        }

        "検査はファイルシステムを1度も読まない" {
            val arch = architecture {
                "domain".group {
                    "UseCase" {
                        layout { ":core:domain".module { mainSourceSet / kotlin / "*UseCase".ktFile() } }
                    }
                }
            }

            withClue("読んだら ForbiddenFileSystem が投げるので、通ること自体が証拠になる") {
                arch.documents().keys.size shouldBe 3
            }
        }
    }

    "大文字小文字だけが違うページ" - {
        "識別子の大文字小文字だけが違う役割が検出される" {
            val arch = architecture {
                "domain".group {
                    "UseCase" { }
                    "usecase" { }
                }
            }

            val exception = shouldThrow<KatachiDocumentPathCaseCollisionException> { arch.documents() }

            exception.collisions shouldContainExactly
                listOf(listOf("domain/UseCase.md", "domain/usecase.md"))
        }

        "ルート直下の Readme はルートの README.md と衝突する" {
            val arch = architecture { "Readme" { } }

            val exception = shouldThrow<KatachiDocumentPathCaseCollisionException> { arch.documents() }

            exception.collisions shouldContainExactly listOf(listOf("README.md", "Readme.md"))
        }

        "例外は両方のページと、それを出した宣言を名指しする" {
            val arch = architecture { "Readme" { } }

            val exception = shouldThrow<KatachiDocumentPathCaseCollisionException> { arch.documents() }

            exception.message.orEmpty() shouldContain "\"README.md\" -- The documentation root"
            exception.message.orEmpty() shouldContain "\"Readme.md\" -- Role \"Readme\" at LinkCheckSpec.kt:"
        }

        "複数の衝突が1つの例外にまとまる" {
            val arch = architecture {
                "Readme" { }
                "domain".group {
                    "UseCase" { }
                    "usecase" { }
                }
            }

            val exception = shouldThrow<KatachiDocumentPathCaseCollisionException> { arch.documents() }

            exception.collisions.size shouldBe 2
            exception.message.orEmpty() shouldContain
                "4 documentation pages differ only in case"
        }
    }
})

/** What separates a path from the role names on the same line of the placement tree. */
private val COLUMN_GAP = Regex(" {2,}")
