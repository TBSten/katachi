package me.tbsten.katachi.test.dsl

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import me.tbsten.katachi.dsl.gradle.HyphenFolding
import me.tbsten.katachi.dsl.InternalKatachiApi
import me.tbsten.katachi.dsl.gradle.ModulePackage
import me.tbsten.katachi.dsl.gradle.ModulePackageException
import me.tbsten.katachi.dsl.architecture
import me.tbsten.katachi.dsl.gradle.capitalizedModuleNamePackage
import me.tbsten.katachi.dsl.gradle.*
import me.tbsten.katachi.dsl.ktFile
import me.tbsten.katachi.dsl.gradle.moduleNamePackage

/** Declared the way a user declares it: one top level `val`, next to the architecture. */
private val modulePackage = capitalizedModuleNamePackage("com.example")

@OptIn(InternalKatachiApi::class)
class LayoutModulePackageSpec : FreeSpec({
    "layout の中での modulePackage" - {
        "スラッシュ連結の途中に書ける" {
            layoutOf {
                ":core:domain".module {
                    mainSourceSet / kotlin / modulePackage / "useCase" / "*UseCase".ktFile()
                }
            }.map { it.path } shouldBe listOf(
                "core",
                "core/domain",
                "core/domain/build",
                "core/domain/build.gradle.kts",
                "core/domain/src",
                "core/domain/src/main",
                "core/domain/src/main/kotlin",
                "core/domain/src/main/kotlin/com",
                "core/domain/src/main/kotlin/com/example",
                "core/domain/src/main/kotlin/com/example/core",
                "core/domain/src/main/kotlin/com/example/core/domain",
                "core/domain/src/main/kotlin/com/example/core/domain/useCase",
                "core/domain/src/main/kotlin/com/example/core/domain/useCase/*UseCase.kt",
            )
        }

        "ブロックとしても書けて、連結と同じ結果になる" {
            val chained = layoutOf {
                ":core:domain".module { mainSourceSet / kotlin / modulePackage / "*UseCase".ktFile() }
            }
            val block = layoutOf {
                ":core:domain".module { mainSourceSet { kotlin { modulePackage { "*UseCase".ktFile() } } } }
            }

            block.shape() shouldBe chained.shape()
        }

        "連結の先頭にも置ける" {
            layoutOf {
                ":core:domain".module { modulePackage / "*UseCase".ktFile() }
            }.map { it.path }.last() shouldBe "core/domain/com/example/core/domain/*UseCase.kt"
        }

        "同じ val がモジュールごとに別の package に解決される" {
            layoutOf(moduleIndexOf("core/domain", "feature/home")) {
                ":core:domain".module { kotlin / modulePackage / "*".ktFile() }
                ":feature:*".module { kotlin / modulePackage / "*".ktFile() }
            }.map { it.path }.filter { it.endsWith("/*.kt") } shouldBe listOf(
                "core/domain/kotlin/com/example/core/domain/*.kt",
                "feature/home/kotlin/com/example/feature/home/*.kt",
            )
        }

        "ハイフンを含むモジュール名が既定で畳まれる" {
            layoutOf {
                ":hoge:fuga-piyo".module { modulePackage / "*".ktFile() }
            }.map { it.path }.last() shouldBe "hoge/fuga-piyo/com/example/hoge/fugaPiyo/*.kt"
        }

        "小文字連結オプションを選べる" {
            val joined = moduleNamePackage(hyphens = HyphenFolding.Concatenate)

            layoutOf {
                ":hoge:fuga-piyo".module { joined / "*".ktFile() }
            }.map { it.path }.last() shouldBe "hoge/fuga-piyo/hoge/fugapiyo/*.kt"
        }

        "自前の導出戦略を使える" {
            val flat = ModulePackage { path -> "com/example/${path.substringAfterLast(':')}" }

            layoutOf {
                ":core:data".module { flat / "*".ktFile() }
            }.map { it.path }.last() shouldBe "core/data/com/example/data/*.kt"
        }
    }

    "解決のタイミング" - {
        "architecture の構築時には導出が走らない" {
            var derived = 0
            val counting = ModulePackage { path ->
                derived++
                path.split(':').filter { it.isNotEmpty() }.joinToString("/")
            }

            val declared = architecture {
                "group".group {
                    "Role" { layout { ":core:data".module { counting / "*".ktFile() } } }
                }
            }

            derived shouldBe 0
            layoutOf { ":core:data".module { counting / "*".ktFile() } }
            derived shouldBe 1
            declared.allRoles.size shouldBe 1
        }
    }

    "モジュールの外での使用" - {
        "layout 直下で使うとエラーになる" {
            val failure = shouldThrow<ModulePackageException> {
                layoutOf { modulePackage / "*".ktFile() }
            }

            failure.message!! shouldContain "module"
        }

        "ディレクトリブロックの中で使ってもエラーになる" {
            shouldThrow<ModulePackageException> {
                layoutOf { "app" { modulePackage / "*".ktFile() } }
            }
        }

        "ベース package の無いルートプロジェクトはディレクトリにならずエラーになる" {
            val withoutBase = capitalizedModuleNamePackage()

            shouldThrow<ModulePackageException> {
                layoutOf { ":".module { withoutBase / "*".ktFile() } }
            }
        }
    }
})
