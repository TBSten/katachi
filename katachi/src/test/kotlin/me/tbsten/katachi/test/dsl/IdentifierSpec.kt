package me.tbsten.katachi.test.dsl

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.types.shouldBeInstanceOf
import me.tbsten.katachi.dsl.InvalidIdentifierException
import me.tbsten.katachi.dsl.KatachiDeclarationException
import me.tbsten.katachi.dsl.architecture

/** name -> なぜ不正か。テスト名に使う。 */
private val invalidNames = listOf(
    "ユースケース" to "日本語",
    "use case" to "空白",
    "1domain" to "数字始まり",
    "-domain" to "ハイフン始まり",
    "_domain" to "アンダースコア始まり",
    "" to "空文字",
    "use.case" to "ドットを含む",
)

private val validNames = listOf("UseCase", "domain", "a", "Use-Case_2", "R2")

class IdentifierSpec : FreeSpec({
    "group 名" - {
        invalidNames.forEach { (name, reason) ->
            "$reason の group 名 \"$name\" は DSL 評価時に失敗する" {
                shouldThrow<InvalidIdentifierException> {
                    architecture { name.group { } }
                }.name shouldBe name
            }
        }

        validNames.forEach { name ->
            "\"$name\" は group 名として受け付けられる" {
                architecture { name.group { } }.groups.single().name shouldBe name
            }
        }
    }

    "役割名" - {
        invalidNames.forEach { (name, reason) ->
            "$reason の役割名 \"$name\" は DSL 評価時に失敗する" {
                shouldThrow<InvalidIdentifierException> {
                    architecture { "domain".group { name { } } }
                }.name shouldBe name
            }
        }

        validNames.forEach { name ->
            "\"$name\" は役割名として受け付けられる" {
                architecture { "domain".group { name { } } }
                    .allRoles.single().name shouldBe name
            }
        }
    }

    "InvalidIdentifierException は KatachiDeclarationException として捕捉できる" {
        shouldThrow<KatachiDeclarationException> {
            architecture { "1domain".group { } }
        }.shouldBeInstanceOf<InvalidIdentifierException>()
    }

    "エラーメッセージに不正な名前・宣言位置・許される文字集合が含まれる" {
        val thrown = shouldThrow<InvalidIdentifierException> {
            architecture { "use case".group { } }
        }

        thrown.declaredAt.fileName shouldBe "IdentifierSpec.kt"
        thrown.message!!.shouldContain("\"use case\"")
        thrown.message!!.shouldContain("[A-Za-z][A-Za-z0-9_-]*")
        thrown.message!!.shouldContain("IdentifierSpec.kt:")
    }
})
