@file:OptIn(InternalKatachiApi::class)

package me.tbsten.katachi.test.konsist

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe
import me.tbsten.katachi.InternalKatachiApi
import me.tbsten.katachi.check.KonsistCheck
import me.tbsten.katachi.check.validate
import me.tbsten.katachi.dsl.KatachiConstraintNameException
import me.tbsten.katachi.dsl.KatachiConstraintWithoutLayoutException
import me.tbsten.katachi.dsl.architecture
import me.tbsten.katachi.dsl.wholeTree
import me.tbsten.katachi.fs.RealFileSystem
import me.tbsten.katachi.konsist.konsist

/**
 * Where `konsist { }` can be written, and where it refuses to be.
 *
 * The vocabulary inside the block is [KonsistAssumptionsSpec]'s; this one is only about the
 * entry point — a top level function in a **different artifact** that takes `ConstraintScope`
 * as a context parameter. That such a function resolves with no compiler flag on the caller's
 * side is already settled by `dsl/gradle/Modules.kt` and `sample/jvm`, so what is left is the
 * containment: the places it must not resolve.
 *
 * NOTE: このファイルのパッケージを `me.tbsten.katachi.konsist.test` にしてはいけない。
 * captureDeclarationSite() が自分のフレームを読み飛ばし、宣言位置が kotest 内部を指す。
 */
class KonsistDslSpec : FreeSpec({
    "役割直下と layout の中の両方に書ける" {
        fixtureProject("core/domain/GetUser.kt" to GET_USER_KT) { root ->
            val projectArchitecture = architecture {
                files = wholeTree()
                "domain".group {
                    "UseCase" {
                        "名前".konsist { classes().must { it.hasInternalModifier } }
                        layout {
                            // The fixture's project root marker. It is a real file in a tree the
                            // check walks whole, so the layout has to account for it.
                            "gradlew".file()
                            "core/domain" {
                                "*.kt".file()
                                konsist { classes().mustNot { it.hasPublicOrDefaultModifier } }
                            }
                        }
                    }
                }
            }

            // Two constraints declared in two different places of one role, both evaluated by
            // one `KonsistCheck()` on one walk, both satisfied. `konsist { }` with an empty
            // block would be refused for expecting nothing, so each says something trivially
            // true about the one class the fixture holds.
            projectArchitecture.validate(RealFileSystem(root), KonsistCheck()).shouldBeEmpty()
        }
    }

    "宣言された時点では 1 度も走らない" {
        var invocations = 0

        fixtureProject("core/domain/GetUser.kt" to GET_USER_KT) { root ->
            val projectArchitecture = architecture {
                files = wholeTree()
                "domain".group {
                    "UseCase" {
                        "名前".konsist { invocations++ }
                        layout {
                            "gradlew".file()
                            "core/domain" {
                                "*.kt".file()
                                konsist { invocations++ }
                            }
                        }
                    }
                }
            }

            invocations shouldBe 0
            // A run that was not handed a check which evaluates constraints runs neither of
            // them -- and says so, which is what the `[UncheckedConstraint]` blocks below are.
            projectArchitecture.validate(RealFileSystem(root))
            invocations shouldBe 0
            // Handed one, it runs each block exactly once. That difference is the whole point
            // of the explicit wiring.
            projectArchitecture.validate(RealFileSystem(root), KonsistCheck())
            invocations shouldBe 2
        }
    }

    "役割直下の konsist は宣言時に ConstraintScope へ届いている" - {
        // `konsist { }` is a thin wrapper over `ConstraintScope.constraint`, and the two ways to
        // observe that from outside `:katachi` are the exceptions `constraint` raises. Reading
        // the declarations back is `@InternalKatachiApi` *and* internal, so it is not available
        // across the module boundary by design -- see the TODO above.
        "layout { } を 1 つも持たない役割に書くと落ちる" {
            shouldThrow<KatachiConstraintWithoutLayoutException> {
                architecture {
                    "domain".group {
                        "UseCase" { "名前".konsist { } }
                    }
                }
            }
        }

        "空白だけの名前は宣言時に落ちる" {
            shouldThrow<KatachiConstraintNameException> {
                architecture {
                    "domain".group {
                        "UseCase" {
                            "   ".konsist { }
                            layout { "core/domain" { "*.kt".file() } }
                        }
                    }
                }
            }
        }
    }

    "layout の中の konsist は layout の評価時に ConstraintScope へ届く" {
        // `layout { }` is deferred, so a constraint written inside one is declared when the
        // layout is flattened rather than when `architecture { }` is built. A broken name
        // therefore lands at the same moment a broken glob key would.
        fixtureProject("core/domain/GetUser.kt" to GET_USER_KT) { root ->
            val projectArchitecture = architecture {
                files = wholeTree()
                "domain".group {
                    "UseCase" {
                        layout {
                            "gradlew".file()
                            "core/domain" {
                                "*.kt".file()
                                "\n".konsist { }
                            }
                        }
                    }
                }
            }

            shouldThrow<KatachiConstraintNameException> {
                projectArchitecture.validate(RealFileSystem(root))
            }
        }
    }

    "書けない場所" - {
        // 答えがコンパイルエラーなので、走るテストにはできない。下の 3 つは実際に
        // コンパイルして落ちることを確認済み（Kotlin 2.4.10、:katachi-konsist:compileTestKotlin。
        // 引用しているのはその実出力）。閉じ込めが緩んだらコメントではなく次の回の
        // compile-fail 確認で気づく。
        "architecture { } 直下には書けない" {
            // architecture {
            //     konsist { }
            // }
            //
            // e: No context argument for 'scope: ConstraintScope' found.
            //
            // ArchitectureScope は ConstraintScope を継承していない。architecture { } は役割を
            // 入れる器であってファイルの集合ではないので、そこに書かれた制約が覆うものが無い。
            true shouldBe true
        }

        "group { } の中には書けない" {
            // architecture {
            //     "domain".group {
            //         konsist { }
            //     }
            // }
            //
            // e: No context argument for 'scope: ConstraintScope' found.
            //
            // GroupScope も同じ理由で ConstraintScope を継承していない。group が持つのは役割で、
            // ファイルを持つのはその下の役割の layout。
            true shouldBe true
        }

        "konsist { } の中に konsist { } は書けない" {
            // "UseCase" {
            //     "外側".konsist {
            //         "内側".konsist { }
            //     }
            //     layout { "core/domain" { "*.kt".file() } }
            // }
            //
            // e: 'context(scope: ConstraintScope) fun String.konsist(block: KonsistScope.() ->
            //    Unit): Unit' cannot be called in this context with an implicit receiver.
            //    Use an explicit receiver if necessary.
            //
            // 上の 2 つと**文面が違う**のが肝心で、こちらは外側の RoleScope が context 引数の
            // 候補として見えてはいるが @DslMarker に遮られている、という意味。KonsistScope に
            // 付けた @KatachiDsl がそれをやっている: 実際に外して同じコードをコンパイルすると
            // **通る**ことを確認済み（確認後に戻してある）。制約の中に制約を書くと覆う範囲が
            // 二重になって意味が決まらないので、ここは閉じている必要がある。
            true shouldBe true
        }
    }
})

private val GET_USER_KT: String = """
    package com.example.core.domain

    internal class GetUser {
        operator fun invoke() = Unit
    }
""".trimIndent() + "\n"
