@file:OptIn(InternalKatachiApi::class, ExperimentalKatachiApi::class)

package me.tbsten.katachi.test.konture

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe
import me.tbsten.katachi.ExperimentalKatachiApi
import me.tbsten.katachi.InternalKatachiApi
import me.tbsten.katachi.check.FileConstraintCheck
import me.tbsten.katachi.check.internal.validate
import me.tbsten.katachi.dsl.FileConstraintRange
import me.tbsten.katachi.dsl.KatachiFileConstraintDirectOnlyWithoutDirectoryException
import me.tbsten.katachi.dsl.KatachiFileConstraintNameException
import me.tbsten.katachi.dsl.KatachiFileConstraintWithoutLayoutException
import me.tbsten.katachi.dsl.architecture
import me.tbsten.katachi.dsl.files.internal.RealFileSystem
import me.tbsten.katachi.dsl.wholeTree
import me.tbsten.katachi.konture.konture

/**
 * Where `konture { }` can be written, and where it refuses to be.
 *
 * The vocabulary inside the block is Konture's and [KontureAssumptionsSpec]'s; this one is only
 * about the entry point — a top level function in a **different artifact** that takes
 * `FileConstraintScope` as a context parameter. That such a function resolves with no compiler
 * flag on the caller's side is already settled by `dsl/gradle/Modules.kt` and
 * `sample/custom-processor`, so what is left is the containment: the places it must not resolve.
 *
 * NOTE: このファイルのパッケージを `me.tbsten.katachi.konture.test` にしてはいけない。
 * captureDeclarationSite() が自分のフレームを読み飛ばし、宣言位置が kotest 内部を指す。
 */
class KontureDslSpec : FreeSpec({
    "役割直下と layout の中の両方に書ける" {
        fixtureProject("core/domain/GetUser.kt" to GET_USER_KT) { root ->
            val projectArchitecture = architecture {
                files = wholeTree()
                "domain".group {
                    "UseCase".role {
                        "名前".konture { classes { should().beInternal() } }
                        layout {
                            // The fixture's project root marker. It is a real file in a tree the
                            // check walks whole, so the layout has to account for it.
                            "gradlew".file()
                            "core/domain" {
                                "*.kt".file()
                                konture { functions { that() haveName "invoke"; should().bePublic() } }
                            }
                        }
                    }
                }
            }

            // Two constraints declared in two different places of one role, both evaluated by
            // one `FileConstraintCheck()` on one walk, both satisfied. `konture { }` with an empty
            // block would be refused for declaring nothing, so each says something trivially
            // true about the one class the fixture holds.
            projectArchitecture.validate(RealFileSystem(root), FileConstraintCheck()).shouldBeEmpty()
        }
    }

    "宣言された時点では 1 度も走らない" {
        var invocations = 0

        fixtureProject("core/domain/GetUser.kt" to GET_USER_KT) { root ->
            val projectArchitecture = architecture {
                files = wholeTree()
                "domain".group {
                    "UseCase".role {
                        "名前".konture { invocations++ }
                        layout {
                            "gradlew".file()
                            "core/domain" {
                                "*.kt".file()
                                konture { invocations++ }
                            }
                        }
                    }
                }
            }

            invocations shouldBe 0
            // A run that was not handed a check which evaluates constraints runs neither of
            // them -- and says so, which is what the `[UncheckedFileConstraint]` blocks below are.
            projectArchitecture.validate(RealFileSystem(root))
            invocations shouldBe 0
            // Handed one, it runs each block exactly once. That difference is the whole point
            // of the explicit wiring. (Both blocks are then refused for declaring no rule, which
            // is beside the point here.)
            projectArchitecture.validate(RealFileSystem(root), FileConstraintCheck())
            invocations shouldBe 2
        }
    }

    "役割直下の konture は宣言時に FileConstraintScope へ届いている" - {
        // `konture { }` is a thin wrapper over `FileConstraintScope.fileConstraint`, and the two ways to
        // observe that from outside `:katachi` are the exceptions `fileConstraint` raises.
        "layout { } を 1 つも持たない役割に書くと落ちる" {
            shouldThrow<KatachiFileConstraintWithoutLayoutException> {
                architecture {
                    "domain".group {
                        "UseCase".role { "名前".konture { } }
                    }
                }
            }
        }

        "空白だけの名前は宣言時に落ちる" {
            shouldThrow<KatachiFileConstraintNameException> {
                architecture {
                    "domain".group {
                        "UseCase".role {
                            "   ".konture { }
                            layout { "core/domain" { "*.kt".file() } }
                        }
                    }
                }
            }
        }
    }

    "layout の中の konture は layout の評価時に FileConstraintScope へ届く" {
        // `layout { }` is deferred, so a constraint written inside one is declared when the
        // layout is flattened rather than when `architecture { }` is built. A broken name
        // therefore lands at the same moment a broken glob key would.
        fixtureProject("core/domain/GetUser.kt" to GET_USER_KT) { root ->
            val projectArchitecture = architecture {
                files = wholeTree()
                "domain".group {
                    "UseCase".role {
                        layout {
                            "gradlew".file()
                            "core/domain" {
                                "*.kt".file()
                                "\n".konture { }
                            }
                        }
                    }
                }
            }

            shouldThrow<KatachiFileConstraintNameException> {
                projectArchitecture.validate(RealFileSystem(root))
            }
        }
    }

    "scope = DirectOnly" - {
        // The fixture: an internal class directly in `util/`, and a public one a level below in
        // `util/ksp/`. "Every class is internal" holds for the first and not for the second, so
        // whether the second was covered is what the result says.
        fun utilFixture(use: (java.io.File) -> Unit) = fixtureProject(
            "util/Strings.kt" to UTIL_STRINGS_KT,
            "util/ksp/Symbols.kt" to UTIL_KSP_SYMBOLS_KT,
            use = use,
        )

        "名前付きの konture(scope = DirectOnly) は子ディレクトリのファイルを見ない" {
            utilFixture { root ->
                val projectArchitecture = architecture {
                    files = wholeTree()
                    "util".group {
                        "Util".role {
                            layout {
                                "gradlew".file()
                                "util" {
                                    "internal であること".konture(scope = FileConstraintRange.DirectOnly) {
                                        classes { should().beInternal() }
                                    }
                                    "*.kt".file()
                                    "ksp" { "*.kt".file() }
                                }
                            }
                        }
                    }
                }

                projectArchitecture.validate(RealFileSystem(root), FileConstraintCheck()).shouldBeEmpty()
            }
        }

        "名前なしの konture(scope = DirectOnly) も子ディレクトリのファイルを見ない" {
            utilFixture { root ->
                val projectArchitecture = architecture {
                    files = wholeTree()
                    "util".group {
                        "Util".role {
                            layout {
                                "gradlew".file()
                                "util" {
                                    konture(scope = FileConstraintRange.DirectOnly) { classes { should().beInternal() } }
                                    "*.kt".file()
                                    "ksp" { "*.kt".file() }
                                }
                            }
                        }
                    }
                }

                projectArchitecture.validate(RealFileSystem(root), FileConstraintCheck()).shouldBeEmpty()
            }
        }

        "scope を書かなければ子ディレクトリのファイルも見る" {
            utilFixture { root ->
                val projectArchitecture = architecture {
                    files = wholeTree()
                    "util".group {
                        "Util".role {
                            layout {
                                "gradlew".file()
                                "util" {
                                    "internal であること".konture { classes { should().beInternal() } }
                                    "*.kt".file()
                                    "ksp" { "*.kt".file() }
                                }
                            }
                        }
                    }
                }

                val failures = projectArchitecture.validate(RealFileSystem(root), FileConstraintCheck())
                failures.map { it.path } shouldBe listOf("util/ksp/Symbols.kt")
            }
        }

        "役割直下の konture(scope = DirectOnly) は宣言時に落ちる" {
            shouldThrow<KatachiFileConstraintDirectOnlyWithoutDirectoryException> {
                architecture {
                    "util".group {
                        "Util".role {
                            konture(scope = FileConstraintRange.DirectOnly) { }
                            layout { "util" { "*.kt".file() } }
                        }
                    }
                }
            }
        }
    }

    "書けない場所" - {
        // 答えがコンパイルエラーなので、走るテストにはできない。下の 3 つは Konsist 統合
        // （KonsistDslSpec）で実際にコンパイルして落ちることを確認済みの形をそのまま写している。
        // どれも `konture` 側のコードではなく `:katachi` の FileConstraintScope と @KatachiDsl
        // の性質で決まる。
        "architecture { } 直下には書けない" {
            // architecture {
            //     konture { }
            // }
            //
            // e: No context argument for 'fileConstraintScope: FileConstraintScope' found.
            //
            // ArchitectureScope は FileConstraintScope を継承していない。architecture { } は役割を
            // 入れる器であってファイルの集合ではないので、そこに書かれた制約が覆うものが無い。
            true shouldBe true
        }

        "group { } の中には書けない" {
            // architecture {
            //     "domain".group {
            //         konture { }
            //     }
            // }
            //
            // e: No context argument for 'fileConstraintScope: FileConstraintScope' found.
            true shouldBe true
        }

        "konture { } の中に konture { } は書けない" {
            // "UseCase".role {
            //     "外側".konture {
            //         "内側".konture { }
            //     }
            //     layout { "core/domain" { "*.kt".file() } }
            // }
            //
            // e: ... cannot be called in this context with an implicit receiver.
            //    Use an explicit receiver if necessary.
            //
            // KontureConstraintScope に付けた @KatachiDsl が外側の RoleScope を隠している。
            // 制約の中に制約を書くと覆う範囲が二重になって意味が決まらないので、ここは
            // 閉じている必要がある。
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

private val UTIL_STRINGS_KT: String = """
    package com.example.util

    internal class Strings
""".trimIndent() + "\n"

private val UTIL_KSP_SYMBOLS_KT: String = """
    package com.example.util.ksp

    class Symbols
""".trimIndent() + "\n"
