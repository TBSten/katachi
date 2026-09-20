package me.tbsten.katachi.test.dsl

import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeSameInstanceAs
import me.tbsten.katachi.dsl.InternalKatachiApi
import me.tbsten.katachi.dsl.LayoutDeclaration
import me.tbsten.katachi.dsl.LayoutScope
import me.tbsten.katachi.dsl.architecture

/**
 * `LayoutDeclaration.block` is marked @InternalKatachiApi, so reading it needs an explicit
 * opt-in. katachi's build gives the test source set no blanket opt-in on purpose.
 */
@OptIn(InternalKatachiApi::class)
private fun LayoutDeclaration.deferredBlock(): LayoutScope.() -> Unit = block

class LayoutDeferralSpec : FreeSpec({
    "architecture { } を評価しただけでは layout { } ブロックが実行されない" {
        var invocations = 0

        val arch = architecture {
            "domain".group {
                "UseCase" {
                    layout { invocations++ }
                }
                "Repository" {
                    layout { invocations++ }
                }
            }
        }

        invocations shouldBe 0
        arch.allRoles.sumOf { it.layouts.size } shouldBe 2
    }

    "宣言した layout ブロックは実行されないまま保持される" {
        val declared: LayoutScope.() -> Unit = { }

        val arch = architecture {
            "domain".group {
                "UseCase" { layout(declared) }
            }
        }

        arch.allRoles.single().layouts.single().deferredBlock() shouldBeSameInstanceAs declared
    }

    "同じ group の役割が複数の layout を持てる" {
        val arch = architecture {
            "domain".group {
                "UseCase" {
                    layout { }
                    layout { }
                }
            }
        }

        arch.allRoles.single().layouts.size shouldBe 2
    }

    "layout を1つも書かない役割も宣言できる" {
        val arch = architecture {
            "domain".group { "UseCase" { } }
        }

        arch.allRoles.single().layouts shouldBe emptyList()
    }
})
