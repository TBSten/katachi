package me.tbsten.katachi.test.docs

import io.kotest.assertions.withClue
import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
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
 * The `## 配置場所` table: which declarations get a row, and what lands in each column.
 *
 * NOTE: このファイルのパッケージを `me.tbsten.katachi.docs` にしてはいけない。
 * captureDeclarationSite() がライブラリ自身のフレームとして読み飛ばしてしまい、
 * 宣言位置が kotest 内部を指すようになる。
 */
class PlacementSpec : FreeSpec({
    val modulePackage: ModulePackage = capitalizedModuleNamePackage("com.example")

    "モジュールとパスの列" - {
        "module { } の中の宣言は、モジュール名とモジュール内の相対パスに分かれる" {
            val arch = architecture {
                "domain".group {
                    "UseCase" {
                        layout {
                            ":core:domain".module {
                                mainSourceSet / kotlin / "useCase" / "*UseCase".ktFile()
                            }
                        }
                    }
                }
            }

            arch.placementRows("domain/UseCase.md") shouldContainExactly listOf(
                "| `:core:domain` | `src/main/kotlin/useCase/*UseCase.kt` |  |",
            )
        }

        "modulePackage は展開せず ** のまま出る" {
            val arch = architecture {
                "domain".group {
                    "UseCase" {
                        layout {
                            ":core:domain".module {
                                mainSourceSet / kotlin / modulePackage / "useCase" / "*UseCase".ktFile()
                            }
                        }
                    }
                }
            }

            withClue("modulePackage はモジュールごとに違うディレクトリを指す戦略で、1つの値ではない") {
                arch.placementRows("domain/UseCase.md") shouldContainExactly listOf(
                    "| `:core:domain` | `src/main/kotlin/**/useCase/*UseCase.kt` |  |",
                )
            }
        }

        "ワイルドカードのモジュールキーは書かれたまま出る" {
            val arch = architecture {
                "feature".group {
                    "Screen" {
                        layout {
                            ":feature:*".module {
                                mainSourceSet / kotlin / "*Screen".ktFile()
                            }
                        }
                    }
                }
            }

            arch.placementRows("feature/Screen.md") shouldContainExactly listOf(
                "| `:feature:*` | `src/main/kotlin/*Screen.kt` |  |",
            )
        }

        "module { } の外で書いた宣言は、モジュール列が空でフルパスになる" {
            val arch = architecture {
                "tool".group {
                    "Script" {
                        layout { "scripts" / "*.sh".file() }
                    }
                }
            }

            arch.placementRows("tool/Script.md") shouldContainExactly listOf(
                "|  | `scripts/*.sh` |  |",
            )
        }

        "ルートプロジェクトの module { } はモジュール列に : が出る" {
            val arch = architecture {
                "build".group {
                    "GradleRoot" {
                        layout {
                            ":".module { "settings.gradle".ktsFile() }
                        }
                    }
                }
            }

            arch.placementRows("build/GradleRoot.md") shouldContainExactly listOf(
                "| `:` | `settings.gradle.kts` |  |",
            )
        }
    }

    "ignore() と anyFile()" - {
        "どちらもモジュール列を空にしてフルパスを出す" {
            val arch = architecture {
                "app".group {
                    "Resource" {
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

            arch.placementRows("app/Resource.md") shouldContainExactly listOf(
                "|  | `app/src/main/res` |  |",
                "|  | `app/src/main/assets` |  |",
            )
        }
    }

    "使い分けの列" - {
        "module { } に書いた description が、その下の宣言の行に出る" {
            val arch = architecture {
                "domain".group {
                    "UseCase" {
                        layout {
                            ":core:domain".module {
                                description = "複数 feature から使われるもの"
                                mainSourceSet / kotlin / "useCase" / "*UseCase".ktFile()
                            }
                            ":feature:*".module {
                                description = "その feature 専用のもの"
                                mainSourceSet / kotlin / "useCase" / "*UseCase".ktFile()
                            }
                        }
                    }
                }
            }

            withClue("同じ役割の配置が2つ以上あれば、表に全部並ぶ") {
                arch.placementRows("domain/UseCase.md") shouldContainExactly listOf(
                    "| `:core:domain` | `src/main/kotlin/useCase/*UseCase.kt` | 複数 feature から使われるもの |",
                    "| `:feature:*` | `src/main/kotlin/useCase/*UseCase.kt` | その feature 専用のもの |",
                )
            }
        }

        "表を壊す文字はエスケープして出す" {
            val arch = architecture {
                "domain".group {
                    "UseCase" {
                        layout {
                            ":core:domain".module {
                                description = "a | b"
                                mainSourceSet / kotlin / "*UseCase".ktFile()
                            }
                        }
                    }
                }
            }

            arch.placementRows("domain/UseCase.md") shouldContainExactly listOf(
                "| `:core:domain` | `src/main/kotlin/*UseCase.kt` | a \\| b |",
            )
        }

        "近いところに書いた description が勝つ" {
            val arch = architecture {
                "domain".group {
                    "UseCase" {
                        layout {
                            ":core:domain".module {
                                description = "モジュールの説明"
                                mainSourceSet / kotlin / "useCase" {
                                    description = "ディレクトリの説明"
                                    "*UseCase".ktFile()
                                }
                            }
                        }
                    }
                }
            }

            arch.placementRows("domain/UseCase.md") shouldContainExactly listOf(
                "| `:core:domain` | `src/main/kotlin/useCase/*UseCase.kt` | ディレクトリの説明 |",
            )
        }
    }

    "表に出さないもの" - {
        "module { } が注入する build/ と build.gradle.kts は出ない" {
            val arch = architecture {
                "domain".group {
                    "UseCase" {
                        layout {
                            ":core:domain".module { mainSourceSet / kotlin / "*UseCase".ktFile() }
                        }
                    }
                }
            }

            val page = arch.page("domain/UseCase.md")

            withClue("katachi が書いた2行なので、モジュールを名乗るすべての役割のページに同じ行が並んでしまう") {
                page shouldNotContain "build.gradle.kts"
            }
            page shouldNotContain "| `:core:domain` | `build` |"
        }

        "宣言の途中にあるだけのディレクトリは行にならない" {
            val arch = architecture {
                "domain".group {
                    "UseCase" {
                        layout {
                            ":core:domain".module { mainSourceSet / kotlin / "useCase" / "*UseCase".ktFile() }
                        }
                    }
                }
            }

            arch.placementRows("domain/UseCase.md").size shouldBe 1
        }

        "layout { } が空なら配置場所の節ごと出ない" {
            val arch = architecture { "domain".group { "UseCase" { } } }

            arch.page("domain/UseCase.md") shouldNotContain "## 配置場所"
        }
    }
})

/** The body rows of the page's placement table, without its header. */
private fun Architecture.placementRows(path: String): List<String> =
    page(path)
        .lines()
        .dropWhile { it != "|---|---|---|" }
        .drop(1)
        .takeWhile { it.startsWith("|") }
