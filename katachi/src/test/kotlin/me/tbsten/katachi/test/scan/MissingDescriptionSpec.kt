package me.tbsten.katachi.test.scan

import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe
import me.tbsten.katachi.dsl.LayoutEntry
import me.tbsten.katachi.dsl.LayoutScope
import me.tbsten.katachi.dsl.architecture
import me.tbsten.katachi.dsl.flattenLayout
import me.tbsten.katachi.dsl.gradle.kotlin
import me.tbsten.katachi.dsl.gradle.mainSourceSet
import me.tbsten.katachi.dsl.gradle.module
import me.tbsten.katachi.dsl.kotlin.ktFile
import me.tbsten.katachi.scan.MissingDescription
import me.tbsten.katachi.scan.missingDescriptionsOf

/**
 * `missingDescriptionsOf`, the declaration-only half of step 5-3: which of a role's places have
 * to say when they are the right one, read off the `layout { }` blocks alone with no walk of a
 * real tree. `MissingDescriptionCheckSpec` covers the same detector through `validate()` /
 * `assert()` and the wording of its report block.
 */
private fun oneRole(block: LayoutScope.() -> Unit): List<LayoutEntry> = architecture {
    "group".group { "Role" { layout(block) } }
}.flattenLayout()

private fun warningsOf(entries: List<LayoutEntry>): List<MissingDescription> =
    missingDescriptionsOf(entries).filterIsInstance<MissingDescription>()

class MissingDescriptionSpec : FreeSpec({
    "配置場所が2つ以上のとき" - {
        "description が無い place がそれぞれ1件の警告になる" {
            val entries = oneRole {
                ":core:domain".module { "*UseCase".ktFile() }
                ":feature:home".module { "*UseCase".ktFile() }
            }

            warningsOf(entries).map { it.path } shouldBe listOf("core/domain", "feature/home")
        }

        "otherPlaces は自分以外の place を宣言順に並べる" {
            val entries = oneRole {
                ":core:domain".module { "*UseCase".ktFile() }
                ":feature:home".module { "*UseCase".ktFile() }
                ":tool:cli".module { "*UseCase".ktFile() }
            }

            warningsOf(entries).map { it.otherPlaces } shouldBe listOf(
                listOf("feature/home", "tool/cli"),
                listOf("core/domain", "tool/cli"),
                listOf("core/domain", "feature/home"),
            )
        }

        "description を書いた place は警告にならない" {
            val entries = oneRole {
                ":core:domain".module {
                    description = "複数 feature から使われるもの"
                    "*UseCase".ktFile()
                }
                ":feature:home".module { "*UseCase".ktFile() }
            }

            warningsOf(entries).map { it.path } shouldBe listOf("feature/home")
        }

        "両方に description を書けば警告が消える" {
            val entries = oneRole {
                ":core:domain".module {
                    description = "複数 feature から使われるもの"
                    "*UseCase".ktFile()
                }
                ":feature:home".module {
                    description = "その feature 専用のもの"
                    "*UseCase".ktFile()
                }
            }

            missingDescriptionsOf(entries).shouldBeEmpty()
        }

        "役割が違えば place は合算されない" {
            val entries = architecture {
                "group".group {
                    "RoleA" { layout { ":core:domain".module { "*UseCase".ktFile() } } }
                    "RoleB" { layout { ":feature:home".module { "*UseCase".ktFile() } } }
                }
            }.flattenLayout()

            missingDescriptionsOf(entries).shouldBeEmpty()
        }
    }

    "配置場所が1つのとき" - {
        "description が無くても警告にならない" {
            val entries = oneRole { ":core:domain".module { "*UseCase".ktFile() } }

            missingDescriptionsOf(entries).shouldBeEmpty()
        }
    }

    "place として数えないもの" - {
        "中身が空の .module { } は、いくつ書いても place にならない" {
            // All three hold nothing but the `build` / `build.gradle.kts` pair the sugar injects,
            // which every Gradle module has. There is no "which files belong here" to answer.
            val entries = oneRole {
                ":katachi".module { }
                ":katachi-konsist".module { }
                ":architecture-test".module { }
            }

            missingDescriptionsOf(entries).shouldBeEmpty()
        }

        "ディレクトリしか開いていない .module { } は place にならない" {
            val entries = oneRole {
                ":app".module { "res" { } }
                ":core:domain".module { "*UseCase".ktFile() }
            }

            missingDescriptionsOf(entries).shouldBeEmpty()
        }

        "/ 連結だけで書いた layout は place を1つも作らない" {
            val entries = oneRole {
                "core/domain" / "*UseCase".ktFile()
                "feature/home" / "*UseCase".ktFile()
            }

            missingDescriptionsOf(entries).shouldBeEmpty()
        }

        "\":\".module { } の中の src/main や kotlin は place にならない" {
            // The root project has no directory of its own, so its block declares straight into
            // the layout root — and `mainSourceSet` / `kotlin` are `"src/main" { }` and
            // `"kotlin" { }`, katachi's own vocabulary written as plain directory blocks. Marking
            // those would call one role's single home three separate places.
            val entries = oneRole {
                ":".module { mainSourceSet / kotlin / "App".ktFile() }
                ":core:domain".module { "*UseCase".ktFile() }
            }

            missingDescriptionsOf(entries).shouldBeEmpty()
        }
    }

    "ワイルドカードのモジュールキー" - {
        "\":feature:*\".module { } は1つの place として数えられる" {
            val entries = oneRole {
                ":core:ui".module { "*Screen".ktFile() }
                ":feature:*".module { "*Screen".ktFile() }
            }

            warningsOf(entries).map { it.path } shouldBe listOf("core/ui", "feature/*")
        }
    }

    "ファイルの主張とみなすもの" - {
        "anyFile() を持つ .module { } は place になる" {
            val entries = oneRole {
                ":app".module { "generated" { anyFile() } }
                ":core:domain".module { "*UseCase".ktFile() }
            }

            warningsOf(entries).map { it.path } shouldBe listOf("app", "core/domain")
        }

        "自分で書いた ignore() を持つ .module { } は place になる" {
            val entries = oneRole {
                ":app".module { "secrets".ignore() }
                ":core:domain".module { "*UseCase".ktFile() }
            }

            warningsOf(entries).map { it.path } shouldBe listOf("app", "core/domain")
        }
    }
})
