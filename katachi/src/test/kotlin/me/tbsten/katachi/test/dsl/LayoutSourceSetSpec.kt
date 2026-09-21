package me.tbsten.katachi.test.dsl

import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.shouldBe
import me.tbsten.katachi.dsl.InternalKatachiApi
import me.tbsten.katachi.dsl.gradle.*
import me.tbsten.katachi.dsl.ktFile

@OptIn(InternalKatachiApi::class)
class LayoutSourceSetSpec : FreeSpec({
    "sourceSet は src/<name> というディレクトリ" - {
        "mainSourceSet は src/main になる" {
            layoutOf { mainSourceSet / "Foo".ktFile() }.map { it.path } shouldBe
                listOf("src", "src/main", "src/main/Foo.kt")
        }

        "testSourceSet は src/test になる" {
            layoutOf { testSourceSet / "FooTest".ktFile() }.map { it.path } shouldBe
                listOf("src", "src/test", "src/test/FooTest.kt")
        }

        "任意の名前の sourceSet を書ける" {
            layoutOf { "commonMain".sourceSet / "Foo".ktFile() }.map { it.path } shouldBe
                listOf("src", "src/commonMain", "src/commonMain/Foo.kt")
        }

        "プロジェクト種別による出し分けはしない" {
            val main = layoutOf { mainSourceSet / "Foo".ktFile() }
            val commonMain = layoutOf { "commonMain".sourceSet / "Foo".ktFile() }

            main.map { it.path } shouldBe listOf("src", "src/main", "src/main/Foo.kt")
            commonMain.map { it.path } shouldBe
                listOf("src", "src/commonMain", "src/commonMain/Foo.kt")
        }

        "sourceSet はブロックとしても書ける" {
            val chained = layoutOf { mainSourceSet / "Foo".ktFile() }
            val block = layoutOf { mainSourceSet { "Foo".ktFile() } }

            block.shape() shouldBe chained.shape()
        }

        "sourceSet ブロックには kotlin 以外のものも書ける" {
            layoutOf {
                mainSourceSet {
                    "res" { ignore() }
                    "AndroidManifest.xml".file()
                }
            }.shape() shouldBe listOf(
                "src [Directory]",
                "src/main [Directory]",
                "src/main/res [Ignore]",
                "src/main/AndroidManifest.xml [File] required",
            )
        }
    }

    "kotlin は明示して掘る" - {
        "kotlin は暗黙に含まれない" {
            layoutOf { mainSourceSet / "Foo".ktFile() }.map { it.path } shouldBe
                listOf("src", "src/main", "src/main/Foo.kt")
        }

        "連結の途中の値としてもブロックとしても書ける" {
            val chained = layoutOf { mainSourceSet / kotlin / "Foo".ktFile() }
            val block = layoutOf { mainSourceSet { kotlin { "Foo".ktFile() } } }
            val written = layoutOf { "src" { "main" { "kotlin" { "Foo".ktFile() } } } }

            chained.shape() shouldBe block.shape()
            chained.shape() shouldBe written.shape()
            chained.shape() shouldBe listOf(
                "src [Directory]",
                "src/main [Directory]",
                "src/main/kotlin [Directory]",
                "src/main/kotlin/Foo.kt [File] required",
            )
        }

        "sourceSet とディレクトリキーの混在も同じ結果になる" {
            val mixed = layoutOf { "commonMain".sourceSet { kotlin { "di" { "*Module".ktFile() } } } }
            val written = layoutOf { "src/commonMain/kotlin/di" { "*Module".ktFile() } }

            mixed.shape() shouldBe written.shape()
        }
    }
})
