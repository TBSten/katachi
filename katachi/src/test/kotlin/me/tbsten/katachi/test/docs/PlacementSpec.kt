package me.tbsten.katachi.test.docs

import io.kotest.assertions.withClue
import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import me.tbsten.katachi.dsl.Architecture
import me.tbsten.katachi.dsl.architecture
import me.tbsten.katachi.dsl.gradle.div
import me.tbsten.katachi.dsl.gradle.kotlin
import me.tbsten.katachi.dsl.gradle.mainSourceSet
import me.tbsten.katachi.dsl.kotlin.ktFile
import me.tbsten.katachi.dsl.kotlin.ktsFile

/**
 * The `## Placement` table: which declarations get a row, and what lands in each column.
 *
 * NOTE: このファイルのパッケージを `me.tbsten.katachi.docs` にしてはいけない。
 * captureDeclarationSite() がライブラリ自身のフレームとして読み飛ばしてしまい、
 * 宣言位置が kotest 内部を指すようになる。
 */
class PlacementSpec : FreeSpec({
    "パスの列" - {
        "宣言はフルパスで出る" {
            val arch = architecture {
                "tool".group {
                    "Script".role {
                        layout { "scripts" / "*.sh".file() }
                    }
                }
            }

            arch.placementRows("tool/Script.md") shouldContainExactly listOf(
                "| `scripts/*.sh` |  |",
            )
        }
    }

    "Module 列" - {
        "Module 列は出ない" {
            val arch = architecture {
                "tool".group {
                    "Script".role {
                        layout { "scripts" / "*.sh".file() }
                    }
                }
            }

            val page = arch.page("tool/Script.md")
            page shouldContain "| Path | When to use |"
            page shouldNotContain "Module"
        }
    }

    "ignore() と anyFile()" - {
        "どちらもフルパスを出す" {
            val arch = architecture {
                "app".group {
                    "Resource".role {
                        layout {
                            "app" {
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
                "| `app/src/main/res` |  |",
                "| `app/src/main/assets` |  |",
            )
        }
    }

    "When to use の列" - {
        "ディレクトリのブロックに書いた description が、その下の宣言の行に出る" {
            val arch = architecture {
                "domain".group {
                    "UseCase".role {
                        layout {
                            "core/domain" {
                                description = "複数 feature から使われるもの"
                                mainSourceSet / kotlin / "useCase" / "*UseCase".ktFile()
                            }
                            "feature/*" {
                                description = "その feature 専用のもの"
                                mainSourceSet / kotlin / "useCase" / "*UseCase".ktFile()
                            }
                        }
                    }
                }
            }

            withClue("同じ役割の配置が2つ以上あれば、表に全部並ぶ") {
                arch.placementRows("domain/UseCase.md") shouldContainExactly listOf(
                    "| `core/domain/src/main/kotlin/useCase/*UseCase.kt` | 複数 feature から使われるもの |",
                    "| `feature/*/src/main/kotlin/useCase/*UseCase.kt` | その feature 専用のもの |",
                )
            }
        }

        "表を壊す文字はエスケープして出す" {
            val arch = architecture {
                "domain".group {
                    "UseCase".role {
                        layout {
                            "core/domain" {
                                description = "a | b"
                                mainSourceSet / kotlin / "*UseCase".ktFile()
                            }
                        }
                    }
                }
            }

            arch.placementRows("domain/UseCase.md") shouldContainExactly listOf(
                "| `core/domain/src/main/kotlin/*UseCase.kt` | a \\| b |",
            )
        }

        "近いところに書いた description が勝つ" {
            val arch = architecture {
                "domain".group {
                    "UseCase".role {
                        layout {
                            "core/domain" {
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
                "| `core/domain/src/main/kotlin/useCase/*UseCase.kt` | ディレクトリの説明 |",
            )
        }
    }

    "表に出さないもの" - {

        "宣言の途中にあるだけのディレクトリは行にならない" {
            val arch = architecture {
                "domain".group {
                    "UseCase".role {
                        layout {
                            "core/domain" { mainSourceSet / kotlin / "useCase" / "*UseCase".ktFile() }
                        }
                    }
                }
            }

            arch.placementRows("domain/UseCase.md").size shouldBe 1
        }

        "layout { } が空なら## Placement の節ごと出ない" {
            val arch = architecture { "domain".group { "UseCase".role { } } }

            arch.page("domain/UseCase.md") shouldNotContain "## Placement"
        }
    }
})

/** The body rows of the page's placement table, without its header. */
private fun Architecture.placementRows(path: String): List<String> =
    page(path)
        .lines()
        .dropWhile { it != "|---|---|---|" && it != "|---|---|" }
        .drop(1)
        .takeWhile { it.startsWith("|") }
