package me.tbsten.katachi.test.template

import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.maps.shouldContainExactly
import io.kotest.matchers.shouldBe
import me.tbsten.katachi.dsl.template
import me.tbsten.katachi.test.check.architectureOf

/**
 * Where a template's file lands: [me.tbsten.katachi.dsl.LayoutEntry.path], with every capture
 * filled in by the run's values -- design draft section 4. There is no candidate search: the
 * declaration `.template { }` sits on already names the one place its file goes.
 */
class TemplatePlacementSpec : FreeSpec({
    "宣言どおりのパスに、値なしでも書ける（capture が無い）" {
        val arch = architectureOf {
            "domain".group {
                "UseCase" {
                    layout { "useCase" / "GetUserUseCase.kt".file().template { "class GetUserUseCase" } }
                }
            }
        }
        arch.generated("UseCase") shouldContainExactly mapOf("useCase/GetUserUseCase.kt" to "class GetUserUseCase")
    }

    "ディレクトリの capture を値で埋める" {
        val arch = architectureOf {
            "feature".group {
                "ViewModel" {
                    layout {
                        "feature" / capture("feature") / "ViewModel.kt".file().template {
                            "class ${captureValue("feature")}ViewModel"
                        }
                    }
                }
            }
        }
        arch.generated("feature.ViewModel", mapOf("feature" to "home")) shouldContainExactly
            mapOf("feature/home/ViewModel.kt" to "class homeViewModel")
    }

    "ファイル名の部分一致で capture を埋める" {
        val arch = architectureOf {
            "domain".group {
                "UseCase" {
                    layout {
                        "useCase" / "${capture("name")}UseCase.kt".file().template {
                            "class ${captureValue("name")}UseCase"
                        }
                    }
                }
            }
        }
        arch.generated("UseCase", mapOf("name" to "GetUser")) shouldContainExactly
            mapOf("useCase/GetUserUseCase.kt" to "class GetUserUseCase")
    }

    "ディレクトリとファイル名、両方に capture があっても埋まる" {
        val arch = architectureOf {
            "feature".group {
                "Screen" {
                    layout {
                        "feature" / capture("feature") / "component" /
                            "${capture("fileName")}Screen.kt".file().template {
                                "package feature.${captureValue("feature")}.component"
                            }
                    }
                }
            }
        }
        arch.generated("feature.Screen", mapOf("feature" to "home", "fileName" to "UserCard")) shouldContainExactly
            mapOf("feature/home/component/UserCardScreen.kt" to "package feature.home.component")
    }

    "名前の無い * の宣言と合流しても、宣言の順によらず .template 側の capture で埋まる" {
        // レビューで見つかったバグの再現: 名前の無い宣言が先に書かれていると、合流したエントリの
        // captureVariants は [NONE, {name}] の順になり、.captureVariants.firstOrNull() で NONE を
        // 拾って生成先のパスに * が残ったまま書かれていた（LayoutEntry.templateCaptures 追加前）。
        val unnamedFirst = architectureOf {
            "domain".group {
                "UseCase" {
                    layout {
                        "dir" / "*.kt".file()
                        "dir" / "${capture("name")}.kt".file().template { "// ${captureValue("name")}" }
                    }
                }
            }
        }
        unnamedFirst.generated("UseCase", mapOf("name" to "GetUser")) shouldContainExactly
            mapOf("dir/GetUser.kt" to "// GetUser")

        val namedFirst = architectureOf {
            "domain".group {
                "UseCase" {
                    layout {
                        "dir" / "${capture("name")}.kt".file().template { "// ${captureValue("name")}" }
                        "dir" / "*.kt".file()
                    }
                }
            }
        }
        namedFirst.generated("UseCase", mapOf("name" to "GetUser")) shouldContainExactly
            mapOf("dir/GetUser.kt" to "// GetUser")
    }

    "戻り値には末尾の改行を足さない" {
        val arch = architectureOf {
            "domain".group {
                "UseCase" {
                    layout { "useCase" / "GetUserUseCase.kt".file().template { "class GetUserUseCase" } }
                }
            }
        }
        arch.generated("UseCase").getValue("useCase/GetUserUseCase.kt") shouldBe "class GetUserUseCase"
    }
})
