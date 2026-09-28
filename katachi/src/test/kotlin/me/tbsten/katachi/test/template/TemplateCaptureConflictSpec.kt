package me.tbsten.katachi.test.template

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.shouldBe
import me.tbsten.katachi.dsl.KatachiTemplateParameterConflictException
import me.tbsten.katachi.dsl.template
import me.tbsten.katachi.test.check.architectureOf

/**
 * A capture name that answers to another input of the same template too: its own parameter, or
 * `GenerateCodeFromTemplate.Args`' own `template` / `onExisting`.
 */
class TemplateCaptureConflictSpec : FreeSpec({
    "capture と同名のパラメータは KatachiTemplateParameterConflictException" {
        val arch = architectureOf {
            "feature".group {
                "ViewModel" {
                    layout {
                        "feature" / "${capture("feature")}ViewModel.kt".file().template {
                            val feature by stringParameter()
                            "package $feature"
                        }
                    }
                }
            }
        }

        val thrown = shouldThrow<KatachiTemplateParameterConflictException> {
            arch.generated("feature.ViewModel", mapOf("feature" to "home"))
        }
        thrown.name shouldBe "feature"
        thrown.role shouldBe "feature.ViewModel"
    }

    "if の中だけで宣言されたパラメータとの衝突も、その分岐を取らない run で見つかる" {
        // 衝突は「実際に取った分岐」だけでなく「取りうる全ての分岐」を見て判定する
        // （parameterOriginsOnEveryBranch）。
        val arch = architectureOf {
            "feature".group {
                "ViewModel" {
                    layout {
                        "feature" / "${capture("feature")}ViewModel.kt".file().template {
                            val withPreview by booleanParameter(default = false)
                            if (withPreview) {
                                val feature by stringParameter()
                                "package $feature"
                            } else {
                                "// no preview"
                            }
                        }
                    }
                }
            }
        }

        // withPreview を渡さない（false のまま）run でも、withPreview=true の分岐にある
        // `feature` パラメータとの衝突が見つかる。
        shouldThrow<KatachiTemplateParameterConflictException> {
            arch.generated("feature.ViewModel", mapOf("feature" to "home"))
        }
    }

    "capture が template（GenerateCodeFromTemplate.Args の予約名）と同じなら、処理系の引数として名指しされる" {
        val arch = architectureOf {
            "domain".group {
                "UseCase" {
                    layout { "useCase" / "${capture("template")}UseCase.kt".file().template { "" } }
                }
            }
        }

        val thrown = shouldThrow<KatachiTemplateParameterConflictException> {
            arch.generated("UseCase", mapOf("template" to "GetUser"))
        }
        thrown.name shouldBe "template"
        thrown.conflictsWith shouldBe "GenerateCodeFromTemplate.Args.template"
        thrown.parameterDeclaredAt shouldBe null
    }
})
