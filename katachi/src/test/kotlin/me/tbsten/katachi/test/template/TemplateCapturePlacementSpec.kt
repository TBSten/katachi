package me.tbsten.katachi.test.template

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import me.tbsten.katachi.dsl.Architecture
import me.tbsten.katachi.dsl.KatachiMissingTemplateParameterException
import me.tbsten.katachi.dsl.KatachiUnknownTemplateCaptureException
import me.tbsten.katachi.dsl.architecture
import me.tbsten.katachi.template.KatachiAmbiguousTemplatePlacementException
import me.tbsten.katachi.template.KatachiInvalidTemplateCaptureValueException
import me.tbsten.katachi.template.KatachiMissingTemplateCaptureException
import me.tbsten.katachi.template.KatachiWildcardTemplatePlacementException

/** A ViewModel role whose feature directory is named `feature`, and whose template reads nothing else. */
private fun viewModelArchitecture(): Architecture = architecture {
    "feature".group {
        "ViewModel" {
            layout { "feature" / capture("feature") / "src" / "main" / "kotlin" / "*ViewModel.kt".file() }
            template {
                val name by stringParameter()
                file("${name}ViewModel.kt") { "class ${name}ViewModel" }
            }
        }
    }
}

/**
 * Where a generated file lands when the directory is a `capture("...")` level of the layout.
 *
 * Every spec here runs against a tree that refuses to be read (see [generated]): a directory
 * capture is filled in from `--arg` alone.
 *
 * NOTE: このファイルのパッケージを `me.tbsten.katachi.template` にしてはいけない。
 * captureDeclarationSite() がライブラリ自身のフレームとして読み飛ばしてしまい、
 * 宣言位置が kotest 内部を指すようになる。
 */
class TemplateCapturePlacementSpec : FreeSpec({
    "パスの capture" - {
        "capture の値を --arg で渡すとそのディレクトリに生成される" {
            viewModelArchitecture().generated("ViewModel", mapOf("feature" to "home", "name" to "Home")) shouldBe
                mapOf("feature/home/src/main/kotlin/HomeViewModel.kt" to "class HomeViewModel\n")
        }

        "まだ無いディレクトリの値でもよい（ディスクを見ない）" {
            viewModelArchitecture().generatedPaths("ViewModel", mapOf("feature" to "brandNew", "name" to "Home")) shouldBe
                listOf("feature/brandNew/src/main/kotlin/HomeViewModel.kt")
        }

        "capture を2つ持つパスは両方の値で置き換わる" {
            val arch = architecture {
                "Screen" {
                    layout { "feature" / capture("feature") / capture("layer") / "*Screen.kt".file() }
                    template { file("HomeScreen.kt") { "" } }
                }
            }

            arch.generatedPaths("Screen", mapOf("feature" to "home", "layer" to "ui")) shouldBe
                listOf("feature/home/ui/HomeScreen.kt")
        }

        "capture の値が足りないと KatachiMissingTemplateCaptureException で足りない名前を名指しする" {
            val thrown = shouldThrow<KatachiMissingTemplateCaptureException> {
                viewModelArchitecture().generated("ViewModel", mapOf("name" to "Home"))
            }

            thrown.missing shouldBe mapOf("feature/*/src/main/kotlin/*ViewModel.kt" to listOf("feature"))
            thrown.message.orEmpty() shouldContain "--arg feature=<value>"
        }

        "capture の後ろに名前の無い * が残るパスは候補にならない" {
            val arch = architecture {
                "Screen" {
                    layout { "feature" / capture("feature") / "*" / "*Screen.kt".file() }
                    template { file("HomeScreen.kt") { "" } }
                }
            }

            val thrown = shouldThrow<KatachiWildcardTemplatePlacementException> {
                arch.generated("Screen", mapOf("feature" to "home"))
            }
            thrown.patterns shouldContainExactly listOf("feature/*/*/*Screen.kt")
        }

        "名前の無い * だけの役割は capture を案内する KatachiWildcardTemplatePlacementException" {
            val arch = architecture {
                "Screen" {
                    layout { "feature" / "*" / "*Screen.kt".file() }
                    template { file("HomeScreen.kt") { "" } }
                }
            }

            val thrown = shouldThrow<KatachiWildcardTemplatePlacementException> { arch.generated("Screen") }
            thrown.message.orEmpty() shouldContain "capture(\"name\")"
        }

        "capture 付きのパスと具体的なパスの両方が候補になると KatachiAmbiguousTemplatePlacementException" {
            val arch = architecture {
                "Screen" {
                    layout { "feature" / capture("feature") / "*Screen.kt".file() }
                    layout { "common" / "*Screen.kt".file() }
                    template { file("HomeScreen.kt") { "" } }
                }
            }

            val thrown = shouldThrow<KatachiAmbiguousTemplatePlacementException> {
                arch.generated("Screen", mapOf("feature" to "home"))
            }
            thrown.candidates shouldContainExactly listOf("common/HomeScreen.kt", "feature/home/HomeScreen.kt")
        }

        "capture 付きのパス2つが別のディレクトリに決まると KatachiAmbiguousTemplatePlacementException" {
            val arch = architecture {
                "Screen" {
                    layout { "feature" / capture("feature") / "ui" / "*Screen.kt".file() }
                    layout { "feature" / capture("feature") / "data" / "*Screen.kt".file() }
                    template { file("HomeScreen.kt") { "" } }
                }
            }

            val thrown = shouldThrow<KatachiAmbiguousTemplatePlacementException> {
                arch.generated("Screen", mapOf("feature" to "home"))
            }
            thrown.candidates shouldContainExactly listOf("feature/home/data/HomeScreen.kt", "feature/home/ui/HomeScreen.kt")
        }

        "別パスで同じ capture 名を共有すると1つの --arg で両方が決まり、ファイル名で1つに絞れる" {
            val arch = architecture {
                "Feature" {
                    layout { "feature" / capture("feature") / "*ViewModel.kt".file() }
                    layout { "test" / capture("feature") / "*ViewModelTest.kt".file() }
                    template {
                        file("HomeViewModel.kt") { "" }
                        file("HomeViewModelTest.kt") { "" }
                    }
                }
            }

            arch.generatedPaths("Feature", mapOf("feature" to "home")) shouldBe
                listOf("feature/home/HomeViewModel.kt", "test/home/HomeViewModelTest.kt")
        }

        "同じパスを名前付きと名前なしで宣言した役割は名前付きの方で生成できる" {
            val arch = architecture {
                "Screen" {
                    layout { "feature" / "*" / "*Screen.kt".file() }
                    layout { "feature" / capture("feature") / "*Screen.kt".file() }
                    template { file("HomeScreen.kt") { "" } }
                }
            }

            arch.generatedPaths("Screen", mapOf("feature" to "home")) shouldBe listOf("feature/home/HomeScreen.kt")
        }
    }

    "capture の値の検査" - {
        listOf(
            "" to KatachiInvalidTemplateCaptureValueException.Problem.Empty,
            "." to KatachiInvalidTemplateCaptureValueException.Problem.DotSegment,
            ".." to KatachiInvalidTemplateCaptureValueException.Problem.DotSegment,
            "home/list" to KatachiInvalidTemplateCaptureValueException.Problem.Separator,
            "home\\list" to KatachiInvalidTemplateCaptureValueException.Problem.Separator,
            "ho*me" to KatachiInvalidTemplateCaptureValueException.Problem.UncreatableCharacter,
            "ho:me" to KatachiInvalidTemplateCaptureValueException.Problem.UncreatableCharacter,
            "ho\tme" to KatachiInvalidTemplateCaptureValueException.Problem.UncreatableCharacter,
        ).forEach { (value, problem) ->
            "\"$value\" は $problem として KatachiInvalidTemplateCaptureValueException" {
                val thrown = shouldThrow<KatachiInvalidTemplateCaptureValueException> {
                    viewModelArchitecture().generated("ViewModel", mapOf("feature" to value, "name" to "Home"))
                }

                thrown.name shouldBe "feature"
                thrown.value shouldBe value
                thrown.problem shouldBe problem
                thrown.message.orEmpty() shouldContain "TemplateCapturePlacementSpec.kt:"
            }
        }
    }

    "テンプレートの中で capture の値を読む" - {
        "captureValue で生成先と同じ値を読める" {
            val arch = architecture {
                "ViewModel" {
                    layout { "feature" / capture("feature") / "*ViewModel.kt".file() }
                    template {
                        val feature = captureValue("feature")
                        file("${feature.replaceFirstChar(Char::uppercaseChar)}ViewModel.kt") {
                            "package com.example.$feature"
                        }
                    }
                }
            }

            arch.generated("ViewModel", mapOf("feature" to "home")) shouldBe
                mapOf("feature/home/HomeViewModel.kt" to "package com.example.home\n")
        }

        "layout に無い名前を読むと KatachiUnknownTemplateCaptureException" {
            val arch = architecture {
                "ViewModel" {
                    layout { "feature" / capture("feature") / "*ViewModel.kt".file() }
                    template { file("${captureValue("featur")}ViewModel.kt") { "" } }
                }
            }

            val thrown = shouldThrow<KatachiUnknownTemplateCaptureException> {
                arch.generated("ViewModel", mapOf("feature" to "home"))
            }
            thrown.name shouldBe "featur"
            thrown.knownNames shouldBe listOf("feature")
            thrown.declaredAt.fileName shouldBe "TemplateCapturePlacementSpec.kt"
        }

        "値の無い capture を読むと、渡し忘れとして名指しされる" {
            val arch = architecture {
                "ViewModel" {
                    layout { "feature" / capture("feature") / "*ViewModel.kt".file() }
                    layout { "common" / "*ViewModel.kt".file() }
                    template { file("HomeViewModel.kt") { "// ${captureValue("feature")}" } }
                }
            }

            val thrown = shouldThrow<KatachiMissingTemplateParameterException> { arch.generated("ViewModel") }
            thrown.names shouldBe listOf("feature")
        }
    }
})
