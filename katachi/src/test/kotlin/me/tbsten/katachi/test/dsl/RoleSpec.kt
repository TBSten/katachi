package me.tbsten.katachi.test.dsl

import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import me.tbsten.katachi.dsl.RoleExample
import me.tbsten.katachi.dsl.architecture

class RoleSpec : FreeSpec({
    "title を省略した役割は、表示名として役割名がそのまま使われる" {
        val arch = architecture { "domain".group { "UseCase" { } } }
        arch.allRoles.single().title shouldBe "UseCase"
    }

    "title を省略した group は、表示名として group 名がそのまま使われる" {
        val arch = architecture { "domain".group { } }
        arch.groups.single().title shouldBe "domain"
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
        group.title shouldBe "ドメイン"

        val role = arch.allRoles.single()
        role.name shouldBe "UseCase"
        role.title shouldBe "ユースケース"
    }

    "summary を省略した役割の summary は null になる" {
        val arch = architecture { "domain".group { "UseCase" { } } }
        arch.allRoles.single().summary shouldBe null
    }

    "summary を指定すると保持される" {
        val arch = architecture {
            "domain".group {
                "UseCase" { summary = "各画面で発生するアプリ固有の1つの振る舞い" }
            }
        }
        arch.allRoles.single().summary shouldBe "各画面で発生するアプリ固有の1つの振る舞い"
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

        arch.allRoles.single().examples shouldContainExactly listOf(
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

        val example = arch.allRoles.single().examples.single()
        example.name shouldBe "ToggleProductFavorite"
        example.description shouldBe "商品のいいね状態 ... を切り替える"
    }

    "example を1つも呼ばなければ examples は空になる" {
        val arch = architecture { "domain".group { "UseCase" { } } }
        arch.allRoles.single().examples shouldBe emptyList()
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
