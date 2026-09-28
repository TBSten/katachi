package me.tbsten.katachi.test.template

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.assertions.withClue
import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import me.tbsten.katachi.dsl.Architecture
import me.tbsten.katachi.dsl.KatachiMissingTemplateParameterException
import me.tbsten.katachi.dsl.KatachiUnknownTemplateCaptureException
import me.tbsten.katachi.dsl.architecture
import me.tbsten.katachi.processor.internal.process
import me.tbsten.katachi.template.internal.templateFiles
import me.tbsten.katachi.test.check.repositoryOf
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

            thrown.names shouldBe listOf("feature")
            thrown.missing shouldBe mapOf("feature/<feature>/src/main/kotlin/*ViewModel.kt" to listOf("feature"))
            thrown.captureDeclaredAt.getValue("feature").fileName shouldBe "TemplateCapturePlacementSpec.kt"
            thrown.message.orEmpty() shouldContain "capture(\"feature\") declared at TemplateCapturePlacementSpec.kt:"
            thrown.message.orEmpty() shouldContain "--arg feature=<feature>"
        }

        "capture を渡し忘れると、今あるディレクトリの値を並べる" {
            val tree = repositoryOf {
                "settings.gradle.kts"()
                "feature" {
                    "home" { "src/main/kotlin" { "HomeViewModel.kt"() } }
                    "settings" { "src/main/kotlin" { "SettingsViewModel.kt"() } }
                    "README.md"()
                }
            }

            val thrown = shouldThrow<KatachiMissingTemplateCaptureException> {
                viewModelArchitecture().process(tree) { context -> templateFiles(context, "ViewModel", mapOf("name" to "Home")) }
            }
            thrown.existingValues shouldBe mapOf("feature" to listOf("home", "settings"))
            thrown.message.orEmpty() shouldContain "Values that exist now:\n  feature: home, settings"
            thrown.message.orEmpty() shouldContain "such as --arg feature=home"
        }

        "具体的なパスと capture のパスを両方持つ役割で capture を渡し忘れると、具体的なパスに書かずに落ちる" {
            val arch = architecture {
                "Scenario" {
                    layout { "scenario" / "common" / "*Scn.kt".file() }
                    layout { "scenario" / capture("x") / "*Scn.kt".file() }
                    template { file("FooScn.kt") { "" } }
                }
            }

            val thrown = shouldThrow<KatachiMissingTemplateCaptureException> { arch.generated("Scenario") }
            thrown.names shouldBe listOf("x")
            thrown.fileName shouldBe "FooScn.kt"
        }

        "具体的なパスと capture のパスを両方持つ役割で capture を渡すと、capture のパスに書く" {
            val arch = architecture {
                "Scenario" {
                    layout { "scenario" / "common" / "*Scn.kt".file() }
                    layout { "scenario" / capture("x") / "*Scn.kt".file() }
                    template { file("FooScn.kt") { "" } }
                }
            }

            arch.generatedPaths("Scenario", mapOf("x" to "login")) shouldBe listOf("scenario/login/FooScn.kt")
        }

        "具体的なパスだけの役割の動きは変わらない（capture の無いパス2つはこれまでどおり曖昧）" {
            val arch = architecture {
                "Scenario" {
                    layout { "scenario" / "a" / "*Scn.kt".file() }
                    layout { "scenario" / "b" / "*Scn.kt".file() }
                    template { file("FooScn.kt") { "" } }
                }
            }

            val thrown = shouldThrow<KatachiAmbiguousTemplatePlacementException> { arch.generated("Scenario") }
            thrown.candidates shouldContainExactly listOf("scenario/a/FooScn.kt", "scenario/b/FooScn.kt")
            withClue("違う階層を示し、そこを capture にするよう案内する") {
                thrown.message.orEmpty() shouldContain "They differ only at level 2 (below scenario/): a, b."
                thrown.message.orEmpty() shouldContain "declare it once as capture(\"...\")"
            }
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
            withClue("渡された capture の値を埋めた形で出す") {
                thrown.patterns shouldContainExactly listOf("feature/home/*/*Screen.kt")
            }
        }

        "** が残るパスしか無いと、** の無いパスを別に書くよう案内する" {
            val arch = architecture {
                "Activity" {
                    layout { "app" / capture("recipe") / "**" / "*Activity.kt".file() }
                    template { file("MainActivity.kt") { "" } }
                }
            }

            val thrown = shouldThrow<KatachiWildcardTemplatePlacementException> {
                arch.generated("Activity", mapOf("recipe" to "login"))
            }
            thrown.patterns shouldContainExactly listOf("app/login/**/*Activity.kt")
            thrown.message.orEmpty() shouldContain "A ** stands for any number of levels"
            thrown.message.orEmpty() shouldContain "as a path of its own, without the **"
            thrown.message.orEmpty() shouldNotContain "capture(\""
        }

        "名前の無い * だけの役割は capture を案内する KatachiWildcardTemplatePlacementException" {
            val arch = architecture {
                "Screen" {
                    layout { "feature" / "*" / "*Screen.kt".file() }
                    template { file("HomeScreen.kt") { "" } }
                }
            }

            val thrown = shouldThrow<KatachiWildcardTemplatePlacementException> { arch.generated("Screen") }
            withClue("例の名前は name ではなく、* の1つ上の階層から取る") {
                thrown.message.orEmpty() shouldContain "capture(\"feature\")"
                thrown.message.orEmpty() shouldContain "--arg feature=<feature>"
                thrown.message.orEmpty() shouldNotContain "capture(\"name\")"
            }
        }

        "capture 付きのパスと具体的なパスの両方が候補になっても、値を渡した capture のパスを選ぶ" {
            val arch = architecture {
                "Screen" {
                    layout { "feature" / capture("feature") / "*Screen.kt".file() }
                    layout { "common" / "*Screen.kt".file() }
                    template { file("HomeScreen.kt") { "" } }
                }
            }

            arch.generatedPaths("Screen", mapOf("feature" to "home")) shouldBe listOf("feature/home/HomeScreen.kt")
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
            "ho\u007fme" to KatachiInvalidTemplateCaptureValueException.Problem.UncreatableCharacter,
            "ho\u0085me" to KatachiInvalidTemplateCaptureValueException.Problem.UncreatableCharacter,
            "ho\u2028me" to KatachiInvalidTemplateCaptureValueException.Problem.UncreatableCharacter,
            " " to KatachiInvalidTemplateCaptureValueException.Problem.Blank,
            "   " to KatachiInvalidTemplateCaptureValueException.Problem.Blank,
            "home " to KatachiInvalidTemplateCaptureValueException.Problem.SurroundingWhitespace,
            " home" to KatachiInvalidTemplateCaptureValueException.Problem.SurroundingWhitespace,
            "home." to KatachiInvalidTemplateCaptureValueException.Problem.TrailingDot,
            "..." to KatachiInvalidTemplateCaptureValueException.Problem.TrailingDot,
            "CON" to KatachiInvalidTemplateCaptureValueException.Problem.ReservedName,
            "nul.txt" to KatachiInvalidTemplateCaptureValueException.Problem.ReservedName,
            "Com1" to KatachiInvalidTemplateCaptureValueException.Problem.ReservedName,
        ).forEach { (value, problem) ->
            "\"${value.escaped()}\" は $problem として KatachiInvalidTemplateCaptureValueException" {
                val thrown = shouldThrow<KatachiInvalidTemplateCaptureValueException> {
                    viewModelArchitecture().generated("ViewModel", mapOf("feature" to value, "name" to "Home"))
                }

                thrown.name shouldBe "feature"
                thrown.value shouldBe value
                thrown.problem shouldBe problem
                thrown.message.orEmpty() shouldContain "TemplateCapturePlacementSpec.kt:"
                withClue("「複数の階層は渡せない」の2文目は区切りを含む値にだけ出る") {
                    val spansLevels = thrown.message.orEmpty().contains("several levels cannot be given as one value")
                    spansLevels shouldBe (problem == KatachiInvalidTemplateCaptureValueException.Problem.Separator)
                }
            }
        }

        listOf("home", "home-list", "home_list", "Home2", "console", "con-a", "a.b").forEach { value ->
            "\"$value\" は1つの階層として通る" {
                viewModelArchitecture().generatedPaths("ViewModel", mapOf("feature" to value, "name" to "Home")) shouldBe
                    listOf("feature/$value/src/main/kotlin/HomeViewModel.kt")
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

        "値の無い capture を読むと、配置で足りないときと同じ例外・同じ文面で名指しされる" {
            val arch = architecture {
                "ViewModel" {
                    layout { "feature" / capture("feature") / "*ViewModel.kt".file() }
                    layout { "common" / "*ViewModel.kt".file() }
                    template { file("HomeViewModel.kt") { "// ${captureValue("feature")}" } }
                }
            }

            val read = shouldThrow<KatachiMissingTemplateCaptureException> { arch.generated("ViewModel") }
            read.names shouldBe listOf("feature")
            read.fileName shouldBe null

            val notRead = architecture {
                "ViewModel" {
                    layout { "feature" / capture("feature") / "*ViewModel.kt".file() }
                    layout { "common" / "*ViewModel.kt".file() }
                    template { file("HomeViewModel.kt") { "" } }
                }
            }
            val placed = shouldThrow<KatachiMissingTemplateCaptureException> { notRead.generated("ViewModel") }
            withClue("宣言位置の行（何行目か）以外は同じ文面") {
                placed.message.orEmpty().replace(Regex("Spec\\.kt:\\d+"), "Spec.kt:N") shouldBe
                    read.message.orEmpty().replace(Regex("Spec\\.kt:\\d+"), "Spec.kt:N")
            }
        }

        "パラメータと capture の両方が足りないと、パラメータの例外に capture の渡し忘れも添える" {
            val arch = architecture {
                "ViewModel" {
                    layout { "feature" / capture("feature") / "*ViewModel.kt".file() }
                    template {
                        val name by stringParameter()
                        file("${name}ViewModel.kt") { "// ${captureValue("feature")}" }
                    }
                }
            }

            val thrown = shouldThrow<KatachiMissingTemplateParameterException> { arch.generated("ViewModel") }
            thrown.names shouldBe listOf("name")
            thrown.missingCaptures shouldBe listOf("feature")
            thrown.message.orEmpty() shouldContain "--arg feature=<feature> as well"
        }
    }
})

/** [this] with every character that is not printed as itself written as `\uXXXX`, for a test name. */
private fun String.escaped(): String = buildString {
    for (character in this@escaped) {
        if (character in ' '..'~') append(character) else append("\\u%04x".format(character.code))
    }
}
