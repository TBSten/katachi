package me.tbsten.katachi.test.template

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.maps.shouldContainExactly
import io.kotest.matchers.shouldBe
import me.tbsten.katachi.dsl.gradle.module
import me.tbsten.katachi.dsl.gradle.wildcard
import me.tbsten.katachi.dsl.template
import me.tbsten.katachi.template.KatachiMissingTemplateCaptureException
import me.tbsten.katachi.template.KatachiTemplateModuleNotFoundException
import me.tbsten.katachi.test.check.architectureOf
import me.tbsten.katachi.test.dsl.moduleIndexOf

/**
 * A module capture -- `":feature:${capture("...")}".module { }` -- is a *kind* of module until a
 * run's values pick one that exists; generation re-flattens against the real project only then.
 */
class TemplateModuleCapturePlacementSpec : FreeSpec({
    val features = { moduleIndexOf("feature/home", "feature/settings") }

    "実在するモジュールの capture を値で選べる" {
        val arch = architectureOf {
            "feature".group {
                "Screen" {
                    layout {
                        ":feature:${capture("feature")}".module {
                            "Screen.kt".file().template {
                                "package feature.${captureValue("feature")}"
                            }
                        }
                    }
                }
            }
        }

        arch.generated("feature.Screen", mapOf("feature" to "home"), features) shouldContainExactly
            mapOf("feature/home/Screen.kt" to "package feature.home")
    }

    "実在しないモジュールを選ぶと KatachiTemplateModuleNotFoundException になり、実在する値を並べる" {
        val arch = architectureOf {
            "feature".group {
                "Screen" {
                    layout {
                        ":feature:${capture("feature")}".module {
                            "Screen.kt".file().template { "// screen" }
                        }
                    }
                }
            }
        }

        val thrown = shouldThrow<KatachiTemplateModuleNotFoundException> {
            arch.generated("feature.Screen", mapOf("feature" to "hoem"), features)
        }
        thrown.existing shouldContainExactly listOf(":feature:home", ":feature:settings")
        thrown.captureNames shouldContainExactly listOf("feature")
    }

    "モジュール capture の値が無ければ KatachiMissingTemplateCaptureException（プロジェクトは読まない）" {
        val arch = architectureOf {
            "feature".group {
                "Screen" {
                    layout {
                        ":feature:${capture("feature")}".module {
                            "Screen.kt".file().template { "// screen" }
                        }
                    }
                }
            }
        }

        // ForbiddenFileSystem のまま（modules 引数を渡さない既定の generated）: 値が無い段階で
        // 落ちるので、モジュール一覧を読みに行くことさえない。
        val thrown = shouldThrow<KatachiMissingTemplateCaptureException> { arch.generated("feature.Screen") }
        thrown.names shouldContainExactly listOf("feature")
    }

    "同じ役割が複数モジュールに展開されても、値で1つに絞られる" {
        val arch = architectureOf {
            "feature".group {
                "BuildFile" {
                    layout {
                        ":feature:${capture("feature")}".module {
                            "module.txt".file().template(id = "module") {
                                captureValue("feature")
                            }
                        }
                    }
                }
            }
        }

        arch.generated("feature.BuildFile.module", mapOf("feature" to "settings"), features) shouldContainExactly
            mapOf("feature/settings/module.txt" to "settings")
    }

    "ファイル名に wildcard(name) を使うテンプレートは、選んだモジュールの値でパスが埋まる（'<feature>' のまま書かれない）" {
        // 最終検証で見つかった blocker の再現: 生成先を決めるとき、モジュールを実在のものに束ねて
        // 平坦化し直した entry と、宣言の一覧（モジュールを解決しない平坦化）のテンプレートを
        // LayoutTemplate.equals で突き合わせていたため、2回の平坦化で別物と判定されて
        // 未解決の entry に戻り、wildcard("feature") のプレースホルダ '<feature>' がパスに残っていた。
        val arch = architectureOf {
            "feature".group {
                "Component" {
                    layout {
                        ":feature:${capture("feature")}".module {
                            "component" / "${wildcard("feature")}${capture("name")}.kt".file().template {
                                "package feature.${captureValue("feature")}"
                            }
                        }
                    }
                }
            }
        }

        arch.generated("feature.Component", mapOf("feature" to "home", "name" to "Card"), features) shouldContainExactly
            mapOf("feature/home/component/homeCard.kt" to "package feature.home")
    }

    "ループで id を変えた .template をモジュールのワイルドカードの中に書いても、選んだ id の宣言に絞られる" {
        // 突き合わせが宣言の場所（ソース行）だけだと、同じ行から id を変えて作った2つの宣言の
        // どちらを指すかが決まらない。id まで見て、指定した方のファイル名で書かれることを確かめる。
        val arch = architectureOf {
            "feature".group {
                "Part" {
                    layout {
                        ":feature:${capture("feature")}".module {
                            listOf("Screen", "Route").forEach { kind ->
                                "${wildcard("feature")}${capture("name")}$kind.kt".file().template(id = kind.lowercase()) {
                                    "// $kind of ${captureValue("feature")}"
                                }
                            }
                        }
                    }
                }
            }
        }

        arch.generated(
            listOf("feature.Part.screen", "feature.Part.route"),
            mapOf("feature" to "settings", "name" to "Main"),
            features,
        ) shouldContainExactly mapOf(
            "feature/settings/settingsMainScreen.kt" to "// Screen of settings",
            "feature/settings/settingsMainRoute.kt" to "// Route of settings",
        )
    }
})
