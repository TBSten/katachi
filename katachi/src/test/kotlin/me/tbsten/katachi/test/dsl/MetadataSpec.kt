package me.tbsten.katachi.test.dsl

import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import me.tbsten.katachi.dsl.GroupScope
import me.tbsten.katachi.dsl.MetadataKey
import me.tbsten.katachi.dsl.MetadataScope
import me.tbsten.katachi.dsl.RoleScope
import me.tbsten.katachi.dsl.Title
import me.tbsten.katachi.dsl.architecture
import me.tbsten.katachi.dsl.metadata

/**
 * The two lines a processor author writes to bring a word of their own into the DSL: a key,
 * and a property to set it through. Everything below is written the way a real processor
 * would write it.
 */
private val Owner: MetadataKey<String> = metadata()
private var MetadataScope.owner: String? by Owner

/** A second key of the same type, to show that keys are told apart by identity. */
private val Reviewer: MetadataKey<String> = metadata()
private var RoleScope.reviewer: String? by Reviewer

/** A key holding something that is neither a string nor nullable at the call site. */
private val Since: MetadataKey<Int> = metadata()
private var GroupScope.since: Int? by Since

private val Tags: MetadataKey<List<String>> = metadata()
private var RoleScope.tags: List<String>? by Tags

class MetadataSpec : FreeSpec({
    "役割に付けた metadata を processor が添字で読める" {
        val arch = architecture {
            "domain".group {
                "UseCase" { owner = "platform" }
            }
        }

        arch.allRoles.single()[Owner] shouldBe "platform"
    }

    "group に付けた metadata も添字で読める" {
        val arch = architecture {
            "domain".group { owner = "platform" }
        }

        arch.groups.single()[Owner] shouldBe "platform"
    }

    "何も書かなかった宣言では、そのキーは null になる" {
        val arch = architecture { "domain".group { "UseCase" { } } }

        arch.groups.single()[Owner] shouldBe null
        arch.allRoles.single()[Owner] shouldBe null
    }

    "group に付けた metadata は配下の役割に継承されない" {
        // documented と同じ規則。実効値の算出は読む側（processor）の責務。
        val arch = architecture {
            "domain".group {
                owner = "platform"
                "UseCase" { }
            }
        }

        arch.groups.single()[Owner] shouldBe "platform"
        arch.allRoles.single()[Owner] shouldBe null
    }

    "ネストした group にも継承されない" {
        val arch = architecture {
            "domain".group {
                owner = "platform"
                "model".group { }
            }
        }

        arch.allGroups.map { it.qualifiedName to it[Owner] } shouldContainExactly
            listOf("domain" to "platform", "domain/model" to null)
    }

    "同じ型の別のキーは別の値を持つ" {
        // キーを識別するのはインスタンスであって型でも名前でもない、という一点。
        val arch = architecture {
            "domain".group {
                "UseCase" {
                    owner = "platform"
                    reviewer = "design"
                }
            }
        }

        val role = arch.allRoles.single()
        role[Owner] shouldBe "platform"
        role[Reviewer] shouldBe "design"
    }

    "String 以外の型のキーも、その型のまま読み出せる" {
        val arch = architecture {
            "domain".group {
                since = 2
                "UseCase" { tags = listOf("core", "public") }
            }
        }

        val since: Int? = arch.groups.single()[Since]
        since shouldBe 2
        val tags: List<String>? = arch.allRoles.single()[Tags]
        tags shouldContainExactly listOf("core", "public")
    }

    "同じキーに2回書くと、最後に書いた値が残る" {
        val arch = architecture {
            "domain".group {
                "UseCase" {
                    owner = "platform"
                    owner = "product"
                }
            }
        }

        arch.allRoles.single()[Owner] shouldBe "product"
    }

    "null を書くとそのキーは書かなかったのと同じ状態に戻る" {
        val arch = architecture {
            "domain".group {
                "UseCase" {
                    owner = "platform"
                    owner = null
                }
            }
        }

        arch.allRoles.single()[Owner] shouldBe null
    }

    "宣言のブロック内では、書いた値をそのまま読み返せる" {
        // 糖衣は書き込み専用ではない。processor が既定値を組み立てるのに使える。
        var readBack: String? = "not read"
        architecture {
            "domain".group {
                "UseCase" {
                    owner = "platform"
                    readBack = owner
                }
            }
        }

        readBack shouldBe "platform"
    }

    "MetadataScope の糖衣は group と役割の両方に書ける" {
        val arch = architecture {
            "domain".group {
                owner = "platform"
                "UseCase" { owner = "product" }
            }
        }

        arch.groups.single()[Owner] shouldBe "platform"
        arch.allRoles.single()[Owner] shouldBe "product"
    }

    "katachi が用意するキーと利用者のキーは同じ器に同居する" {
        val arch = architecture {
            "domain".group {
                "UseCase" {
                    title = "ユースケース"
                    owner = "platform"
                }
            }
        }

        val role = arch.allRoles.single()
        role[Title] shouldBe "ユースケース"
        role[Owner] shouldBe "platform"
    }
})
