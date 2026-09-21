package me.tbsten.katachi.test.dsl

import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
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
