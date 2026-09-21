package me.tbsten.katachi.test.dsl

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.types.shouldBeInstanceOf
import me.tbsten.katachi.KatachiDeclarationException
import me.tbsten.katachi.dsl.DeclarationKind
import me.tbsten.katachi.dsl.KatachiDuplicateDeclarationException
import me.tbsten.katachi.dsl.architecture

class DuplicateDeclarationSpec : FreeSpec({
    // 名前の予約がブロック評価より後だと、まだ評価中の自分自身の外側スコープへ
    // 2件目を差し込めてしまい、重複検出が素通りする。下の2件はその抜け道を塞いでいる。
    "外側のスコープを掴んで、評価中の group と同名の group を差し込んでもエラーになる" {
        val thrown = shouldThrow<KatachiDuplicateDeclarationException> {
            architecture {
                val root = this
                "domain".group {
                    with(root) { "domain".group { } }
                }
            }
        }
        thrown.name shouldBe "domain"
    }

    "外側のスコープを掴んで、評価中の役割と同名の役割を差し込んでもエラーになる" {
        val thrown = shouldThrow<KatachiDuplicateDeclarationException> {
            architecture {
                "domain".group {
                    val group = this
                    "UseCase" {
                        with(group) { "UseCase" { } }
                    }
                }
            }
        }
        thrown.name shouldBe "UseCase"
    }

    "ルート直下に同名の group を2回宣言するとエラーになる" {
        val thrown = shouldThrow<KatachiDuplicateDeclarationException> {
            architecture {
                "domain".group { }
                "domain".group { }
            }
        }
        thrown.name shouldBe "domain"
    }

    "同じ親の下に同名のネスト group を2回宣言するとエラーになる" {
        val thrown = shouldThrow<KatachiDuplicateDeclarationException> {
            architecture {
                "domain".group {
                    "model".group { }
                    "model".group { }
                }
            }
        }
        thrown.name shouldBe "model"
    }

    "親が違えば同名の group を宣言できる" {
        val arch = architecture {
            "domain".group { "model".group { } }
            "data".group { "model".group { } }
        }

        arch.allGroups.map { it.qualifiedName } shouldContainExactly
            listOf("domain", "domain/model", "data", "data/model")
    }

    "同じ group 内に同名の役割を2回宣言するとエラーになる" {
        val thrown = shouldThrow<KatachiDuplicateDeclarationException> {
            architecture {
                "domain".group {
                    "UseCase" { }
                    "UseCase" { }
                }
            }
        }
        thrown.name shouldBe "UseCase"
    }

    "group が違えば同名の役割を宣言できる" {
        val arch = architecture {
            "domain".group { "Repository" { } }
            "data".group { "Repository" { } }
        }

        arch.allRoles.map { it.qualifiedName } shouldContainExactly
            listOf("domain/Repository", "data/Repository")
    }

    "親子の group が同名でも衝突しない" {
        val arch = architecture {
            "model".group { "model".group { } }
        }

        arch.allGroups.map { it.qualifiedName } shouldContainExactly listOf("model", "model/model")
    }

    "KatachiDuplicateDeclarationException は KatachiDeclarationException として捕捉できる" {
        shouldThrow<KatachiDeclarationException> {
            architecture {
                "domain".group { }
                "domain".group { }
            }
        }.shouldBeInstanceOf<KatachiDuplicateDeclarationException>()
    }

    "エラーメッセージに最初の宣言位置と2回目の宣言位置の両方が出る" {
        val thrown = shouldThrow<KatachiDuplicateDeclarationException> {
            architecture {
                "domain".group {
                    "UseCase" { }
                    "UseCase" { }
                }
            }
        }

        thrown.firstDeclaredAt.fileName shouldBe "DuplicateDeclarationSpec.kt"
        thrown.declaredAt.fileName shouldBe "DuplicateDeclarationSpec.kt"
        thrown.firstDeclaredAt.lineNumber shouldBe thrown.declaredAt.lineNumber - 1
        thrown.message!!.shouldContain(thrown.firstDeclaredAt.toString())
        thrown.message!!.shouldContain(thrown.declaredAt.toString())
    }

    // group と役割で文面の形を揃えたことの証拠。ここが崩れたら「同じ例外なのに2通りの
    // 言い回しがある」状態に戻っている。
    "group の重複は kind と scope から組み立てた文面になる" {
        val thrown = shouldThrow<KatachiDuplicateDeclarationException> {
            architecture {
                "domain".group { }
                "domain".group { }
            }
        }

        thrown.kind shouldBe DeclarationKind.Group
        thrown.scope shouldBe "the root of architecture { }"
        thrown.message shouldBe listOf(
            "Duplicate group \"domain\" declared at ${thrown.declaredAt}.",
            "It was already declared at ${thrown.firstDeclaredAt}, in the root of architecture { }.",
            "Group names must be unique among the groups declared under the same parent.",
        ).joinToString("\n")
    }

    "役割の重複も同じ形の文面になる" {
        val thrown = shouldThrow<KatachiDuplicateDeclarationException> {
            architecture {
                "domain".group {
                    "UseCase" { }
                    "UseCase" { }
                }
            }
        }

        thrown.kind shouldBe DeclarationKind.Role
        thrown.scope shouldBe "group \"domain\""
        thrown.message shouldBe listOf(
            "Duplicate role \"UseCase\" declared at ${thrown.declaredAt}.",
            "It was already declared at ${thrown.firstDeclaredAt}, in group \"domain\".",
            "Role names must be unique within a group. The same name may be reused in a different group.",
        ).joinToString("\n")
    }
})
