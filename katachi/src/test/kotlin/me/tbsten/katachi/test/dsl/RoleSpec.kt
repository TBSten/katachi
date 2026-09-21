package me.tbsten.katachi.test.dsl

import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import me.tbsten.katachi.dsl.Description
import me.tbsten.katachi.dsl.Examples
import me.tbsten.katachi.dsl.RoleExample
import me.tbsten.katachi.dsl.Summary
import me.tbsten.katachi.dsl.Title
import me.tbsten.katachi.dsl.architecture

class RoleSpec : FreeSpec({
    "title を省略した役割には Title が入らず、読む側が役割名にフォールバックする" {
        val role = architecture { "domain".group { "UseCase" { } } }.allRoles.single()
        role[Title] shouldBe null
        (role[Title] ?: role.name) shouldBe "UseCase"
    }

    "title を省略した group には Title が入らず、読む側が group 名にフォールバックする" {
        val group = architecture { "domain".group { } }.groups.single()
        group[Title] shouldBe null
        (group[Title] ?: group.name) shouldBe "domain"
    }

    "title を指定すると識別子と表示名が分離される" {
        val arch = architecture {
            "domain".group {
                title = "ドメイン"
                "UseCase" { title = "ユースケース" }
            }
        }

        val group = arch.groups.single()
        group.name shouldBe "domain"
        group[Title] shouldBe "ドメイン"

        val role = arch.allRoles.single()
        role.name shouldBe "UseCase"
        role[Title] shouldBe "ユースケース"
    }

    "summary を省略した役割の Summary は null になる" {
        val arch = architecture { "domain".group { "UseCase" { } } }
        arch.allRoles.single()[Summary] shouldBe null
    }

    "summary を指定すると保持される" {
        val arch = architecture {
            "domain".group {
                "UseCase" { summary = "各画面で発生するアプリ固有の1つの振る舞い" }
            }
        }
        arch.allRoles.single()[Summary] shouldBe "各画面で発生するアプリ固有の1つの振る舞い"
    }

    "description を省略した役割の Description は null になる" {
        val arch = architecture { "domain".group { "UseCase" { } } }
        arch.allRoles.single()[Description] shouldBe null
    }

    "description を指定すると Description から読める" {
        val arch = architecture {
            "domain".group {
                "UseCase" { description = "UI からは UseCase だけを呼ぶ。" }
            }
        }
        arch.allRoles.single()[Description] shouldBe "UI からは UseCase だけを呼ぶ。"
    }

    // 表の1セルに入る summary と違い、description は本文になるので改行が意味を持つ。
    // trimIndent() した文字列がそのまま入ることを1文字ずつ固定する。
    "description の複数行が改行ごとそのまま保たれる" {
        val arch = architecture {
            "domain".group {
                "UseCase" {
                    description = """
                        UI からは UseCase だけを呼び、Repository を直接触らない。

                        ### やっていいこと
                        - 複数の Repository をまたぐ

                        ### やってはいけないこと
                        - Android の型に依存する
                    """.trimIndent()
                }
            }
        }

        arch.allRoles.single()[Description] shouldBe listOf(
            "UI からは UseCase だけを呼び、Repository を直接触らない。",
            "",
            "### やっていいこと",
            "- 複数の Repository をまたぐ",
            "",
            "### やってはいけないこと",
            "- Android の型に依存する",
        ).joinToString("\n")
    }

    "summary と description は互いに独立している" {
        val arch = architecture {
            "domain".group {
                "OnlySummary" { summary = "1行の概要" }
                "OnlyDescription" { description = "本文だけ" }
                "Both" {
                    summary = "1行の概要"
                    description = "本文"
                }
            }
        }

        arch.allRoles.map { it[Summary] to it[Description] } shouldContainExactly listOf(
            "1行の概要" to null,
            null to "本文だけ",
            "1行の概要" to "本文",
        )
    }

    "example を複数回呼ぶと、呼んだ順にすべて保持される" {
        val arch = architecture {
            "domain".group {
                "UseCase" {
                    example("GetRecommendedProductListUseCase", "おすすめの商品リストを取得する")
                    example("ToggleProductFavorite", "商品のいいね状態を切り替える")
                    example("SignOutUseCase", "サインアウトする")
                }
            }
        }

        arch.allRoles.single()[Examples].orEmpty() shouldContainExactly listOf(
            RoleExample("GetRecommendedProductListUseCase", "おすすめの商品リストを取得する"),
            RoleExample("ToggleProductFavorite", "商品のいいね状態を切り替える"),
            RoleExample("SignOutUseCase", "サインアウトする"),
        )
    }

    "example の説明文に ... を含めても名前と説明が正しく分かれる" {
        val arch = architecture {
            "domain".group {
                "UseCase" {
                    example("ToggleProductFavorite", "商品のいいね状態 ... を切り替える")
                }
            }
        }

        val example = arch.allRoles.single()[Examples].orEmpty().single()
        example.name shouldBe "ToggleProductFavorite"
        example.description shouldBe "商品のいいね状態 ... を切り替える"
    }

    "example を1つも呼ばなければ Examples は書き込まれない" {
        val arch = architecture { "domain".group { "UseCase" { } } }
        arch.allRoles.single()[Examples] shouldBe null
    }

    "役割は自分が属する group のパスを保持する" {
        val arch = architecture {
            "domain".group {
                "model".group { "Entity" { } }
            }
        }

        val role = arch.allRoles.single()
        role.groupPath shouldContainExactly listOf("domain", "model")
        role.qualifiedName shouldBe "domain/model/Entity"
    }
})
