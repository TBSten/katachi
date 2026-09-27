package me.tbsten.katachi.test.dsl

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe
import me.tbsten.katachi.dsl.FileConstraintFailure
import me.tbsten.katachi.dsl.FileConstraintSubject
import me.tbsten.katachi.dsl.DeclarationSite
import me.tbsten.katachi.dsl.FileConstraint
import me.tbsten.katachi.dsl.KatachiFileConstraintNameException
import me.tbsten.katachi.dsl.KatachiFileConstraintWithoutLayoutException
import me.tbsten.katachi.dsl.internal.ModuleIndex
import me.tbsten.katachi.dsl.ModuleResolver
import me.tbsten.katachi.dsl.architecture
import me.tbsten.katachi.dsl.internal.flattenLayout
import me.tbsten.katachi.dsl.gradle.mainSourceSet
import me.tbsten.katachi.dsl.gradle.module
import me.tbsten.katachi.dsl.kotlin.ktFile

/**
 * Where a constraint was written, and what refuses to be written at all.
 *
 * What a constraint ends up *covering* is [FileConstraintCoverageSpec]'s.
 *
 * NOTE: このファイルのパッケージを `me.tbsten.katachi.dsl` にしてはいけない。
 * captureDeclarationSite() がライブラリ自身のフレームとして読み飛ばしてしまい、
 * 宣言位置が kotest 内部を指すようになる。
 */

/** Records how often it was evaluated. Nothing in step 4 evaluates one, which is the point. */
private class CountingFileConstraint : FileConstraint {
    var invocations: Int = 0

    override fun evaluate(subject: FileConstraintSubject): List<FileConstraintFailure> {
        invocations++
        return emptyList()
    }
}

/** The `kotlin` source directory, written out so that this spec needs no module package. */
private const val KOTLIN_DIRECTORY: String = "kotlin"

class FileConstraintDeclarationSpec : FreeSpec({
    "宣言位置" - {
        "名前を省略しても利用者の行を指す" {
            val arch = architecture {
                "domain".group {
                    "UseCase" {
                        fileConstraint(check = silent())
                        layout { "useCase" / "*.kt".file() }
                    }
                }
            }

            // この行番号はファイル内の位置に依存する。上のブロックを動かしたら直すこと。
            arch.declaredFileConstraints().single().declaredAt shouldBe
                DeclarationSite("FileConstraintDeclarationSpec.kt", 50)
        }

        "名前を付けても利用者の行を指す" {
            val arch = architecture {
                "domain".group {
                    "UseCase" {
                        layout {
                            "useCase" {
                                fileConstraint("invoke を持つこと", check = silent())
                                "*.kt".file()
                            }
                        }
                    }
                }
            }

            // この行番号はファイル内の位置に依存する。
            val declared = arch.declaredFileConstraints().single()
            declared.name shouldBe "invoke を持つこと"
            declared.declaredAt shouldBe DeclarationSite("FileConstraintDeclarationSpec.kt", 67)
        }

        "declaredAt を明示で渡せる" {
            val arch = architecture {
                "domain".group {
                    "UseCase" {
                        fileConstraint("x", DeclarationSite("DomainRules.kt", 12), check = silent())
                        layout { "useCase" / "*.kt".file() }
                    }
                }
            }

            arch.declaredFileConstraints().single().declaredAt shouldBe
                DeclarationSite("DomainRules.kt", 12)
        }
    }

    "宣言しただけでは走らない" - {
        "architecture { } の構築でも layout の評価でも FileConstraint は呼ばれない" {
            val counting = CountingFileConstraint()

            val arch = architecture {
                "domain".group {
                    "UseCase" {
                        fileConstraint("role wide", check = counting)
                        layout {
                            "useCase" {
                                fileConstraint("in a block", check = counting)
                                "*.kt".file()
                            }
                        }
                    }
                }
            }

            counting.invocations shouldBe 0
            arch.declaredFileConstraints().size shouldBe 2
            counting.invocations shouldBe 0
        }
    }

    "制約名の検証" - {
        "空白だけの名前は宣言時に落ちる" {
            val thrown = shouldThrow<KatachiFileConstraintNameException> {
                architecture {
                    "domain".group {
                        "UseCase" {
                            fileConstraint("   ", check = silent())
                            layout { "useCase" / "*.kt".file() }
                        }
                    }
                }
            }

            thrown.name shouldBe "   "
            thrown.declaredAt.fileName shouldBe "FileConstraintDeclarationSpec.kt"
        }

        "改行を含む名前は宣言時に落ちる" {
            val thrown = shouldThrow<KatachiFileConstraintNameException> {
                architecture {
                    "domain".group {
                        "UseCase" {
                            fileConstraint("invoke を\n持つこと", check = silent())
                            layout { "useCase" / "*.kt".file() }
                        }
                    }
                }
            }

            thrown.name shouldBe "invoke を\n持つこと"
            // 文面自体が1行に畳まれていないと、レポートの形が壊れる。
            thrown.message.orEmpty().lines().first() shouldBe
                "Invalid constraint name \"invoke を\\n持つこと\" declared at ${thrown.declaredAt}."
        }

        "layout { } の中の名前は、layout が評価されたときに落ちる" {
            // layout ブロックは遅延されるので、中の値の誤りは平坦化が最初に見られる瞬間になる。
            // 壊れた glob キーと同じ扱い。
            val arch = architecture {
                "domain".group {
                    "UseCase" {
                        layout { "useCase" { fileConstraint("   ", check = silent()) } }
                    }
                }
            }

            shouldThrow<KatachiFileConstraintNameException> { arch.flattenLayout() }
                .name shouldBe "   "
        }
    }

    "layout を持たない役割" - {
        "制約を書くと宣言時に落ちる" {
            val thrown = shouldThrow<KatachiFileConstraintWithoutLayoutException> {
                architecture {
                    "domain".group {
                        "UseCase" { fileConstraint("invoke を持つこと", check = silent()) }
                    }
                }
            }

            thrown.role shouldBe "UseCase"
            thrown.declaredAt.fileName shouldBe "FileConstraintDeclarationSpec.kt"
        }

        "制約を書かなければ落ちない" {
            architecture {
                "domain".group { "UseCase" { } }
            }.allRoles.single().layouts.shouldBeEmpty()
        }
    }

    "flattenLayout の出力は制約の有無で変わらない" - {
        "module を含む layout のエントリが完全に一致する" {
            val withFileConstraints = architecture {
                "domain".group {
                    "UseCase" {
                        fileConstraint("role wide", check = silent())
                        layout {
                            ":core:domain".module {
                                fileConstraint("in core/domain", check = silent())
                                mainSourceSet / KOTLIN_DIRECTORY / "*UseCase".ktFile()
                            }
                        }
                    }
                }
            }
            val withoutFileConstraints = architecture {
                "domain".group {
                    "UseCase" {
                        layout {
                            ":core:domain".module {
                                mainSourceSet / KOTLIN_DIRECTORY / "*UseCase".ktFile()
                            }
                        }
                    }
                }
            }

            withFileConstraints.flattenLayout().shape() shouldBe withoutFileConstraints.flattenLayout().shape()
            withFileConstraints.flattenLayout().shape() shouldBe listOf(
                "core [Directory]",
                "core/domain [Directory]",
                "core/domain/build [Ignore]",
                "core/domain/build.gradle.kts [File] required",
                "core/domain/src [Directory]",
                "core/domain/src/main [Directory]",
                "core/domain/src/main/kotlin [Directory]",
                "core/domain/src/main/kotlin/*UseCase.kt [File]",
            )
        }

        "Role.flattenLayout も一致する" {
            val withFileConstraint = architecture {
                "domain".group {
                    "UseCase" {
                        layout {
                            "useCase" {
                                fileConstraint("invoke を持つこと", check = silent())
                                "*UseCase".ktFile()
                            }
                        }
                    }
                }
            }
            val withoutFileConstraint = architecture {
                "domain".group {
                    "UseCase" {
                        layout { "useCase" { "*UseCase".ktFile() } }
                    }
                }
            }

            val index = ModuleIndex.unresolved(ModuleResolver.Conventional)
            withFileConstraint.allRoles.single().flattenLayout(index).shape() shouldBe
                withoutFileConstraint.allRoles.single().flattenLayout(index).shape()
        }
    }
})
