package me.tbsten.katachi.test.template

import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.maps.shouldContainExactly
import me.tbsten.katachi.dsl.template
import me.tbsten.katachi.test.check.architectureOf

/**
 * A template's own typed parameters (`stringParameter()` and its siblings), read as ordinary
 * `--arg` entries during a real run -- distinct from a capture, which the layout's own path names.
 */
class TemplateCaptureArgsSpec : FreeSpec({
    "stringParameter は --arg の値をそのまま読む" {
        val arch = architectureOf {
            "domain".group {
                "UseCase" {
                    layout {
                        "useCase" / "GetUserUseCase.kt".file().template {
                            val body by stringParameter()
                            "class GetUserUseCase { $body }"
                        }
                    }
                }
            }
        }
        arch.generated("UseCase", mapOf("body" to "fun run() {}")) shouldContainExactly
            mapOf("useCase/GetUserUseCase.kt" to "class GetUserUseCase { fun run() {} }")
    }

    "既定値は --arg を渡さなければ使われる" {
        val arch = architectureOf {
            "domain".group {
                "UseCase" {
                    layout {
                        "useCase" / "GetUserUseCase.kt".file().template {
                            val body by stringParameter(default = "TODO()")
                            "class GetUserUseCase { $body }"
                        }
                    }
                }
            }
        }
        arch.generated("UseCase") shouldContainExactly mapOf("useCase/GetUserUseCase.kt" to "class GetUserUseCase { TODO() }")
    }

    "intParameter / booleanParameter / enumParameter を1つのテンプレートで使える" {
        val arch = architectureOf {
            "data".group {
                "Repository" {
                    layout {
                        "repository" / "Repository.kt".file().template {
                            val pageSize by intParameter(default = 20)
                            val suspending by booleanParameter(default = true)
                            val visibility by enumParameter(Visibility.Public)
                            val modifier = if (suspending) "suspend " else ""
                            "${visibility.name.lowercase()} interface Repository { ${modifier}fun all(): List<Int> get() = $pageSize }"
                        }
                    }
                }
            }
        }

        arch.generated("Repository", mapOf("pageSize" to "50", "suspending" to "false", "visibility" to "Internal")) shouldContainExactly
            mapOf("repository/Repository.kt" to "internal interface Repository { fun all(): List<Int> get() = 50 }")
    }

    "capture とパラメータは名前空間が別（同じ文字列でも読み口が違う）" {
        val arch = architectureOf {
            "feature".group {
                "ViewModel" {
                    layout {
                        "feature" / capture("feature") / "ViewModel.kt".file().template {
                            val label by stringParameter()
                            "package feature.${captureValue("feature")}\n// $label"
                        }
                    }
                }
            }
        }
        arch.generated("feature.ViewModel", mapOf("feature" to "home", "label" to "note")) shouldContainExactly
            mapOf("feature/home/ViewModel.kt" to "package feature.home\n// note")
    }
})

private enum class Visibility { Public, Internal }
