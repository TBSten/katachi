package me.tbsten.katachi.test.dsl

import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.shouldBe
import me.tbsten.katachi.dsl.InternalKatachiApi

/**
 * `/` is sugar for nesting, so every pair below writes the same layout twice and asks for
 * one flattened result. Only the declaration sites differ, which is why the comparison goes
 * through `shape()`.
 */
@OptIn(InternalKatachiApi::class)
class LayoutPathChainingSpec : FreeSpec({
    "スラッシュ連結は入れ子ブロックの糖衣" - {
        "ファイルで終わる連結が同じ入れ子と一致する" {
            val chained = layoutOf {
                "src" / "main" / "kotlin" / "*.kt".file()
            }
            val nested = layoutOf {
                "src" {
                    "main" {
                        "kotlin" {
                            "*.kt".file()
                        }
                    }
                }
            }

            chained.shape() shouldBe nested.shape()
            chained.shape() shouldBe listOf(
                "src [Directory]",
                "src/main [Directory]",
                "src/main/kotlin [Directory]",
                "src/main/kotlin/*.kt [File]",
            )
        }

        "ディレクトリだけの連結が同じ入れ子と一致する" {
            val chained = layoutOf { "app" / "ios" }
            val nested = layoutOf { "app" { "ios" { } } }

            chained.shape() shouldBe nested.shape()
            chained.shape() shouldBe listOf("app [Directory]", "app/ios [Directory]")
        }

        "連結の末尾にブロックを開ける" {
            val chained = layoutOf {
                "src" / "main" / "res" { ignore() }
            }
            val nested = layoutOf {
                "src" { "main" { "res" { ignore() } } }
            }

            chained.shape() shouldBe nested.shape()
            chained.shape() shouldBe listOf(
                "src [Directory]",
                "src/main [Directory]",
                "src/main/res [Ignore]",
            )
        }

        "連結の要素が複数階層のキーでもよい" {
            val chained = layoutOf {
                "app" / "ios/support" / "Info.plist".file()
            }
            val nested = layoutOf {
                "app" { "ios" { "support" { "Info.plist".file() } } }
            }

            chained.shape() shouldBe nested.shape()
            chained.shape() shouldBe listOf(
                "app [Directory]",
                "app/ios [Directory]",
                "app/ios/support [Directory]",
                "app/ios/support/Info.plist [File] required",
            )
        }

        "連結と入れ子を混ぜても同じになる" {
            val mixed = layoutOf {
                "src" {
                    "main" / "kotlin" / "di" {
                        "*Module".ktFile()
                    }
                }
            }
            val nested = layoutOf {
                "src" { "main" { "kotlin" { "di" { "*Module".ktFile() } } } }
            }

            mixed.shape() shouldBe nested.shape()
        }

        "連結の末尾に optional を付けられる" {
            val chained = layoutOf {
                "gradle" / "libs.versions.toml".file().optional()
            }
            val nested = layoutOf {
                "gradle" { "libs.versions.toml".file().optional() }
            }

            chained.shape() shouldBe nested.shape()
            chained.shape() shouldBe listOf(
                "gradle [Directory]",
                "gradle/libs.versions.toml [File]",
            )
        }

        "連結の途中で宣言したディレクトリが親の直下に残らない" {
            layoutOf {
                "a" / "b" / "c.txt".file()
            }.map { it.path } shouldBe listOf("a", "a/b", "a/b/c.txt")
        }

        "同じブロックに連結と単独の宣言を並べても取り違えない" {
            layoutOf {
                ".gitignore".file()
                "gradle" / "libs.versions.toml".file()
            }.shape() shouldBe listOf(
                ".gitignore [File] required",
                "gradle [Directory]",
                "gradle/libs.versions.toml [File] required",
            )
        }
    }
})
