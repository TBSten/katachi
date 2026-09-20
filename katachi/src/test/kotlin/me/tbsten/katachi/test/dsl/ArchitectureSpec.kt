package me.tbsten.katachi.test.dsl

import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.kotest.core.spec.style.FreeSpec
import me.tbsten.katachi.dsl.Architecture
import me.tbsten.katachi.dsl.architecture

/**
 * NOTE: every spec of katachi lives under `me.tbsten.katachi.test.`. A spec placed in
 * `me.tbsten.katachi.dsl` would be mistaken for katachi's own frames and the captured
 * declaration sites would point into the test runner.
 */
class ArchitectureSpec : FreeSpec({
    "architecture { } の戻り値は Architecture 型である" {
        val result = architecture { }
        result.shouldBeInstanceOf<Architecture>()
    }

    "ルートブロック内で宣言した group と役割が、追加の登録操作なしでモデルに含まれる" {
        val arch = architecture {
            "domain".group {
                "UseCase" { }
                "Repository" { }
            }
        }

        arch.groups.map { it.name } shouldContainExactly listOf("domain")
        arch.allRoles.map { it.qualifiedName } shouldContainExactly
            listOf("domain/UseCase", "domain/Repository")
    }

    "ArchitectureScope の拡張関数を別ファイルに定義し、ルートブロックから呼んで役割を登録できる" {
        val arch = architecture {
            domainRoles()
            dataRoles()
        }

        arch.allGroups.map { it.qualifiedName } shouldContainExactly
            listOf("domain", "domain/model", "data")
        arch.allRoles.map { it.qualifiedName } shouldContainExactly
            listOf("domain/UseCase", "domain/model/Entity", "data/Repository")
    }

    "拡張関数を呼ばなかった場合、その group と役割はモデルに含まれない" {
        val arch = architecture {
            domainRoles()
            // dataRoles() を呼ばない
        }

        arch.allGroups.map { it.qualifiedName } shouldContainExactly listOf("domain", "domain/model")
        arch.allRoles.none { it.name == "Repository" } shouldBe true
    }

    "group をネストして宣言でき、親子関係がモデルに保持される" {
        val arch = architecture {
            "domain".group {
                "model".group {
                    "value".group {
                        "ValueObject" { }
                    }
                }
                "UseCase" { }
            }
        }

        val domain = arch.groups.single()
        domain.groups.map { it.name } shouldContainExactly listOf("model")
        domain.roles.map { it.name } shouldContainExactly listOf("UseCase")

        val model = domain.groups.single()
        model.path shouldContainExactly listOf("domain", "model")
        model.groups.single().qualifiedName shouldBe "domain/model/value"

        arch.allGroups.map { it.qualifiedName } shouldContainExactly
            listOf("domain", "domain/model", "domain/model/value")
        arch.allRoles.map { it.qualifiedName } shouldContainExactly
            listOf("domain/UseCase", "domain/model/value/ValueObject")
    }

    "同じ役割名でも group が違えば両方がモデルに残る" {
        val arch = architecture {
            "domain".group { "Repository" { } }
            "data".group { "Repository" { } }
        }

        arch.allRoles.map { it.qualifiedName } shouldContainExactly
            listOf("domain/Repository", "data/Repository")
    }
})
