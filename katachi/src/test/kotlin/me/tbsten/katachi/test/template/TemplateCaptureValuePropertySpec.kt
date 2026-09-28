package me.tbsten.katachi.test.template

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.assertions.withClue
import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.shouldBe
import me.tbsten.katachi.dsl.template
import me.tbsten.katachi.template.KatachiInvalidTemplateCaptureValueException
import me.tbsten.katachi.template.KatachiInvalidTemplateCaptureValueException.Problem
import me.tbsten.katachi.test.check.architectureOf

/**
 * The value a capture is given is checked twice: on its own, and once filled into the whole
 * segment its pattern names -- design draft section 4, "値の検査". A value that is fine alone
 * (`"a"`) can still make a bad segment once the pattern's own literal text joins it (`"a."` from
 * `"${capture("x")}."`), which [Problem] and [KatachiInvalidTemplateCaptureValueException.segment]
 * tell apart.
 */
class TemplateCaptureValuePropertySpec : FreeSpec({
    "値そのものの検査（capture が1セグメント丸ごとのとき）" - {
        val arch = architectureOf {
            "feature".group {
                "ViewModel" {
                    layout {
                        "feature" / capture("feature") / "ViewModel.kt".file().template {
                            captureValue("feature")
                        }
                    }
                }
            }
        }

        val cases = listOf(
            "" to Problem.Empty,
            " " to Problem.Blank,
            "." to Problem.DotSegment,
            ".." to Problem.DotSegment,
            "a/b" to Problem.Separator,
            "a*b" to Problem.UncreatableCharacter,
            " a" to Problem.SurroundingWhitespace,
            "a " to Problem.SurroundingWhitespace,
            "a." to Problem.TrailingDot,
            "CON" to Problem.ReservedName,
            "con.txt" to Problem.ReservedName,
            "LPT1" to Problem.ReservedName,
        )

        for ((value, problem) in cases) {
            "\"$value\" は $problem で落ちる" {
                val thrown = shouldThrow<KatachiInvalidTemplateCaptureValueException> {
                    arch.generated("feature.ViewModel", mapOf("feature" to value))
                }
                withClue(thrown.message.orEmpty()) { thrown.problem shouldBe problem }
                thrown.segment shouldBe null
            }
        }

        "ふつうの名前は通る" {
            arch.generated("feature.ViewModel", mapOf("feature" to "home")).values.single() shouldBe "home"
        }
    }

    "埋めた後のセグメント全体の検査（部分一致のとき）" - {
        "値そのものは問題無くても、埋めた結果が . で終わると TrailingDot（segment 付き）" {
            val arch = architectureOf {
                "domain".group {
                    "UseCase" {
                        layout {
                            "useCase" / "${capture("name")}.".file().template { "" }
                        }
                    }
                }
            }

            val thrown = shouldThrow<KatachiInvalidTemplateCaptureValueException> {
                arch.generated("UseCase", mapOf("name" to "a"))
            }
            thrown.problem shouldBe Problem.TrailingDot
            thrown.segment shouldBe "a."
            thrown.value shouldBe "a"
        }

        "値そのものは予約デバイス名でなくても、埋めた結果がそうなれば検査される（拡張子付き）" {
            val arch = architectureOf {
                "domain".group {
                    "UseCase" {
                        layout {
                            // 値 "CO" 自体は予約名ではないが、リテラルの "N.txt" と組み合わさると
                            // "CON.txt" になる。
                            "useCase" / "${capture("name")}N.txt".file().template { "" }
                        }
                    }
                }
            }

            val thrown = shouldThrow<KatachiInvalidTemplateCaptureValueException> {
                arch.generated("UseCase", mapOf("name" to "CO"))
            }
            thrown.problem shouldBe Problem.ReservedName
            thrown.segment shouldBe "CON.txt"
        }

        "埋めた結果が問題無ければ通る" {
            val arch = architectureOf {
                "domain".group {
                    "UseCase" {
                        layout {
                            "useCase" / "${capture("name")}UseCase.kt".file().template { "" }
                        }
                    }
                }
            }

            arch.generated("UseCase", mapOf("name" to "GetUser")).keys.single() shouldBe "useCase/GetUserUseCase.kt"
        }
    }
})
