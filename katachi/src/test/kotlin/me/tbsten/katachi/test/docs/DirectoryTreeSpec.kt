package me.tbsten.katachi.test.docs

import io.kotest.assertions.withClue
import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import me.tbsten.katachi.dsl.Architecture
import me.tbsten.katachi.dsl.architecture
import me.tbsten.katachi.dsl.gradle.ModulePackage
import me.tbsten.katachi.dsl.gradle.capitalizedModuleNamePackage
import me.tbsten.katachi.dsl.gradle.div
import me.tbsten.katachi.dsl.gradle.kotlin
import me.tbsten.katachi.dsl.gradle.mainSourceSet
import me.tbsten.katachi.dsl.gradle.module
import me.tbsten.katachi.dsl.kotlin.ktFile
import me.tbsten.katachi.dsl.kotlin.ktsFile

/**
 * The `## Placement in this group` tree: the group's `layout` read along the other axis.
 *
 * NOTE: このファイルのパッケージを `me.tbsten.katachi.docs` にしてはいけない。
 * captureDeclarationSite() がライブラリ自身のフレームとして読み飛ばしてしまい、
 * 宣言位置が kotest 内部を指すようになる。
 */
class DirectoryTreeSpec : FreeSpec({
    val modulePackage: ModulePackage = capitalizedModuleNamePackage("com.example")

    "ツリーの見た目" - {
        "ネストしたディレクトリが木になり、役割一覧表とグループ一覧の間に入る" {
            val arch = architecture {
                "domain".group {
                    "UseCase" {
                        title = "ユースケース"
                        layout {
                            ":core:domain".module {
                                mainSourceSet / kotlin / modulePackage / "useCase" / "*UseCase".ktFile()
                            }
                        }
                    }
                    "Repository" {
                        title = "リポジトリ"
                        layout {
                            ":core:domain".module {
                                mainSourceSet / kotlin / modulePackage / "repository" / "*Repository".ktFile()
                            }
                        }
                    }
                    "model".group { title = "モデル" }
                }
            }

            arch.page("domain/README.md") shouldBe
                """
                [Architecture](../README.md)

                # domain

                | Role | Summary |
                |---|---|
                | [ユースケース](./UseCase.md) |  |
                | [リポジトリ](./Repository.md) |  |

                ## Placement in this group

                ```
                :core:domain
                  src/main/kotlin/**/
                    useCase/*UseCase.kt        ユースケース
                    repository/*Repository.kt  リポジトリ
                ```

                ## Groups

                - [モデル](./model/README.md)
                """.trimIndent() + "\n"
        }

        "役割名はリンクにしない" {
            val arch = architecture {
                "domain".group {
                    "UseCase" {
                        title = "ユースケース"
                        layout { ":core:domain".module { mainSourceSet / kotlin / "*UseCase".ktFile() } }
                    }
                }
            }

            withClue("コードブロックの中ではリンクが効かない。役割へのリンクはすぐ上の一覧表が持っている") {
                arch.tree("domain/README.md") shouldNotContain "]("
            }
            arch.page("domain/README.md") shouldContain "[ユースケース](./UseCase.md)"
        }
    }

    "モジュール" - {
        "複数のモジュールがそれぞれの見出しの下に並ぶ" {
            val arch = architecture {
                "domain".group {
                    "UseCase" {
                        title = "ユースケース"
                        layout {
                            ":core:domain".module { mainSourceSet / kotlin / "useCase" / "*UseCase".ktFile() }
                            ":feature:*".module { mainSourceSet / kotlin / "useCase" / "*UseCase".ktFile() }
                        }
                    }
                }
            }

            withClue("並び順は DSL の出現順。定義を読んだ順にドキュメントが並ぶほうが対応を追いやすい") {
                arch.tree("domain/README.md") shouldBe
                    """
                    :core:domain
                      src/main/kotlin/useCase/*UseCase.kt  ユースケース

                    :feature:*
                      src/main/kotlin/useCase/*UseCase.kt  ユースケース
                    """.trimIndent()
            }
        }

        "ワイルドカードのモジュールキーは展開されずパターンのまま出る" {
            val arch = architecture {
                "feature".group {
                    "Screen" {
                        title = "画面"
                        layout { ":feature:*".module { mainSourceSet / kotlin / "*Screen".ktFile() } }
                    }
                }
            }

            arch.tree("feature/README.md") shouldBe
                """
                :feature:*
                  src/main/kotlin/*Screen.kt  画面
                """.trimIndent()
        }

        "modulePackage は展開せず ** のまま出る" {
            val arch = architecture {
                "domain".group {
                    "UseCase" {
                        title = "ユースケース"
                        layout {
                            ":core:domain".module {
                                mainSourceSet / kotlin / modulePackage / "*UseCase".ktFile()
                            }
                        }
                    }
                }
            }

            withClue("modulePackage はモジュールごとに違うディレクトリを指す戦略で、1つの値ではない") {
                arch.tree("domain/README.md") shouldContain "**/"
            }
        }

        "ルートプロジェクトの module { } は : の見出しで出る" {
            val arch = architecture {
                "build".group {
                    "GradleRoot" {
                        title = "ルートの Gradle ファイル"
                        layout { ":".module { "settings.gradle".ktsFile() } }
                    }
                }
            }

            arch.tree("build/README.md") shouldBe
                """
                :
                  settings.gradle.kts  ルートの Gradle ファイル
                """.trimIndent()
        }

        "module { } の外で書いた宣言は、モジュールの見出しを持たずルートからのパスで出る" {
            val arch = architecture {
                "tool".group {
                    "Script" {
                        title = "スクリプト"
                        layout { "scripts" / "*.sh".file() }
                    }
                }
            }

            arch.tree("tool/README.md") shouldBe
                """
                scripts/*.sh  スクリプト
                """.trimIndent()
        }

        "ignore() と anyFile() も、役割ページの表と同じくフルパスで出る" {
            val arch = architecture {
                "app".group {
                    "Resource" {
                        title = "リソース"
                        layout {
                            ":app".module {
                                mainSourceSet {
                                    "res".ignore()
                                    "assets" { anyFile() }
                                }
                            }
                        }
                    }
                }
            }

            arch.tree("app/README.md") shouldBe
                """
                app/src/main/
                  res/     リソース
                  assets/  リソース
                """.trimIndent()
        }
    }

    "何を載せて何を載せないか" - {
        "同じパスに複数の役割がマッチすれば、どちらも同じ行に並ぶ" {
            val arch = architecture {
                "domain".group {
                    "UseCase" {
                        title = "ユースケース"
                        layout { ":core:domain".module { mainSourceSet / kotlin / "*.kt".file() } }
                    }
                    "Repository" {
                        title = "リポジトリ"
                        layout { ":core:domain".module { mainSourceSet / kotlin / "*.kt".file() } }
                    }
                }
            }

            withClue("衝突はエラーにしない。同じ場所に複数の役割が置けるのは設定として正当であり得る") {
                arch.tree("domain/README.md") shouldBe
                    """
                    :core:domain
                      src/main/kotlin/*.kt  ユースケース, リポジトリ
                    """.trimIndent()
            }
        }

        "同じディレクトリに複数のパターンを書いても、役割名は行ごとに1つ" {
            val arch = architecture {
                "domain".group {
                    "UseCase" {
                        title = "ユースケース"
                        layout {
                            ":core:domain".module {
                                mainSourceSet / kotlin / "useCase" {
                                    "*UseCase".ktFile()
                                    "*UseCaseImpl".ktFile()
                                }
                            }
                        }
                    }
                }
            }

            arch.tree("domain/README.md") shouldBe
                """
                :core:domain
                  src/main/kotlin/useCase/
                    *UseCase.kt      ユースケース
                    *UseCaseImpl.kt  ユースケース
                """.trimIndent()
        }

        "optional() の宣言も他と同じように出る" {
            val arch = architecture {
                "build".group {
                    "VersionCatalog" {
                        title = "バージョンカタログ"
                        layout { "gradle" / "libs.versions.toml".file().optional() }
                    }
                }
            }

            withClue("optional() をツリーでどう見せるかは今回も決めていない。区別の記号を足さない") {
                arch.tree("build/README.md") shouldBe
                    """
                    gradle/libs.versions.toml  バージョンカタログ
                    """.trimIndent()
            }
        }

        "documented = false の役割はツリーからも消える" {
            val arch = architecture {
                "domain".group {
                    "UseCase" {
                        title = "ユースケース"
                        layout { ":core:domain".module { mainSourceSet / kotlin / "useCase" / "*UseCase".ktFile() } }
                    }
                    "Internal" {
                        documented = false
                        layout { ":core:domain".module { mainSourceSet / kotlin / "internal" / "*.kt".file() } }
                    }
                }
            }

            withClue("ドキュメントに出さないと宣言したものをツリーにだけ出すのは矛盾する") {
                arch.tree("domain/README.md") shouldNotContain "internal"
            }
        }

        "ネストした group の役割は、親のツリーではなく自分のツリーに出る" {
            val arch = architecture {
                "domain".group {
                    "UseCase" {
                        title = "ユースケース"
                        layout { ":core:domain".module { mainSourceSet / kotlin / "*UseCase".ktFile() } }
                    }
                    "model".group {
                        "Entity" {
                            title = "エンティティ"
                            layout { ":core:model".module { mainSourceSet / kotlin / "*Entity".ktFile() } }
                        }
                    }
                }
            }

            withClue("group の境界を跨ぐツリーは「どの group の話か」が消える") {
                arch.tree("domain/README.md") shouldNotContain ":core:model"
            }
            arch.tree("domain/model/README.md") shouldContain ":core:model"
        }

        "module { } が注入する build/ と build.gradle.kts は出ない" {
            val arch = architecture {
                "domain".group {
                    "UseCase" {
                        title = "ユースケース"
                        layout { ":core:domain".module { mainSourceSet / kotlin / "*UseCase".ktFile() } }
                    }
                }
            }

            withClue("katachi が書いた2行なので、モジュールを名乗るすべての group に同じ行が並んでしまう") {
                arch.tree("domain/README.md") shouldNotContain "build"
            }
        }

        "layout { } が空なら節ごと出ない" {
            val arch = architecture { "domain".group { "UseCase" { } } }

            arch.page("domain/README.md") shouldNotContain TREE_HEADING
        }

        "ルートの README には architecture { } 直下の役割の配置が出る" {
            val arch = architecture {
                "Changelog" {
                    title = "変更履歴"
                    layout { "CHANGELOG.md".file() }
                }
                "domain".group { title = "ドメイン" }
            }

            withClue("group に属さない役割の配置は、ここに出さなければどのツリーにも出ない") {
                arch.page("README.md") shouldBe
                    """
                    # Architecture documentation

                    ## Document map

                    - [変更履歴](./Changelog.md)

                    ### [ドメイン](./domain/README.md)

                    ## Roles at the root

                    - [変更履歴](./Changelog.md)

                    ## Placement at the root

                    ```
                    CHANGELOG.md  変更履歴
                    ```

                    ## ドメイン
                    """.trimIndent() + "\n"
            }
        }

        "ルートのツリーには group の中の役割の配置が出ない" {
            val arch = architecture {
                "Changelog" { layout { "CHANGELOG.md".file() } }
                "domain".group {
                    "UseCase" {
                        layout { ":core:domain".module { mainSourceSet / kotlin / "*UseCase".ktFile() } }
                    }
                }
            }

            withClue("group を横断するツリーは、どの group の話なのか読み手に分からない") {
                arch.tree("README.md", ROOT_TREE_HEADING) shouldBe "CHANGELOG.md  Changelog"
            }
        }

        "ルート直下に役割が無ければ節ごと出ない" {
            val arch = architecture {
                "domain".group {
                    "UseCase" {
                        layout { ":core:domain".module { mainSourceSet / kotlin / "*UseCase".ktFile() } }
                    }
                }
            }

            arch.page("README.md") shouldNotContain ROOT_TREE_HEADING
        }
    }

    "ツリーの組み立てはファイルシステムを1度も読まない" {
        val arch = architecture {
            "feature".group {
                "Screen" {
                    title = "画面"
                    layout { ":feature:*".module { mainSourceSet / kotlin / "*Screen".ktFile() } }
                }
            }
        }

        withClue("読んだら ForbiddenFileSystem が投げる。実在するモジュールを数えに行っていない証拠でもある") {
            arch.tree("feature/README.md") shouldContain ":feature:*"
        }
    }
})

/** The heading the tree is written under, which is also how a spec asks whether it is there. */
private const val TREE_HEADING: String = "## Placement in this group"

/** The same, on the root's own README, which is not a group and does not say it is one. */
private const val ROOT_TREE_HEADING: String = "## Placement at the root"

/** The tree of one page, without its heading and fence — which is what the specs above compare. */
private fun Architecture.tree(path: String, heading: String = TREE_HEADING): String {
    val page = page(path)
    val opening = "$heading\n\n```\n"
    withClue("配置のツリーが無い:\n$page") { page shouldContain opening }
    return page.substringAfter(opening).substringBefore("\n```")
}
