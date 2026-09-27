package me.tbsten.katachi.test.dsl

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import me.tbsten.katachi.dsl.FileConstraintRange
import me.tbsten.katachi.dsl.KatachiFileConstraintDirectOnlyCoversNothingException
import me.tbsten.katachi.dsl.KatachiFileConstraintDirectOnlyWithoutDirectoryException
import me.tbsten.katachi.dsl.architecture
import me.tbsten.katachi.dsl.gradle.mainSourceSet
import me.tbsten.katachi.dsl.gradle.module
import me.tbsten.katachi.dsl.kotlin.ktFile
import me.tbsten.katachi.dsl.kotlin.ktsFile

/**
 * `fileConstraint(scope = FileConstraintRange.DirectOnly)`: a constraint that covers only what its own directory
 * declares directly, not what the directories below it declare.
 *
 * NOTE: このファイルのパッケージを `me.tbsten.katachi.dsl` にしてはいけない。
 * captureDeclarationSite() がライブラリ自身のフレームとして読み飛ばしてしまう。
 */

/** The `kotlin` source directory, written out so that this spec needs no module package. */
private const val KOTLIN_DIRECTORY: String = "kotlin"

class FileConstraintDirectOnlySpec : FreeSpec({
    "覆う範囲" - {
        "ネストしたディレクトリブロックのファイルは覆わない" {
            val arch = architecture {
                "util".group {
                    "Util" {
                        layout {
                            "util" {
                                fileConstraint("stdlib only", scope = FileConstraintRange.DirectOnly, check = silent())
                                "*.kt".file()
                                "ksp" { "*.kt".file() }
                            }
                        }
                    }
                }
            }

            val coverage = arch.declaredFileConstraints().single().coverage
            coverage.covers("util/Strings.kt") shouldBe true
            coverage.covers("util/ksp/Symbols.kt") shouldBe false
        }

        "/ で1段下に宣言したファイルもネストしたブロックと同じく覆わない" {
            val arch = architecture {
                "util".group {
                    "Util" {
                        layout {
                            "util" {
                                fileConstraint("stdlib only", scope = FileConstraintRange.DirectOnly, check = silent())
                                "*.kt".file()
                                "ksp" / "*.kt".file()
                            }
                        }
                    }
                }
            }

            val coverage = arch.declaredFileConstraints().single().coverage
            coverage.covers("util/Strings.kt") shouldBe true
            coverage.covers("util/ksp/Symbols.kt") shouldBe false
        }

        "scope を書かないか Subtree なら従来どおり部分木全体を覆う" {
            val arch = architecture {
                "util".group {
                    "Util" {
                        layout {
                            "util" {
                                fileConstraint("subtree", check = silent())
                                fileConstraint("explicit subtree", scope = FileConstraintRange.Subtree, check = silent())
                                fileConstraint("direct", scope = FileConstraintRange.DirectOnly, check = silent())
                                "*.kt".file()
                                "ksp" { "*.kt".file() }
                            }
                        }
                    }
                }
            }

            val (subtree, explicitSubtree, direct) = arch.declaredFileConstraints()
            subtree.name shouldBe "subtree"
            subtree.coverage.covers("util/ksp/Symbols.kt") shouldBe true
            explicitSubtree.coverage.covers("util/ksp/Symbols.kt") shouldBe true
            direct.name shouldBe "direct"
            direct.coverage.covers("util/ksp/Symbols.kt") shouldBe false
        }

        "ブロック自身の anyFile() は覆い、子ディレクトリの anyFile() は覆わない" {
            val arch = architecture {
                "build".group {
                    "Generated" {
                        layout {
                            "generated" {
                                fileConstraint("top level only", scope = FileConstraintRange.DirectOnly, check = silent())
                                anyFile()
                                "nested" { anyFile() }
                            }
                        }
                    }
                }
            }

            val coverage = arch.declaredFileConstraints().single().coverage
            coverage.covers("generated/Whatever.kt") shouldBe true
            coverage.covers("generated/nested/Whatever.kt") shouldBe false
        }

        "** を含む宣言は直下のファイル宣言として数えない" {
            val arch = architecture {
                "doc".group {
                    "Doc" {
                        layout {
                            "doc" {
                                fileConstraint("index only", scope = FileConstraintRange.DirectOnly, check = silent())
                                "README.md".file()
                                "**/*.md".file()
                            }
                        }
                    }
                }
            }

            val coverage = arch.declaredFileConstraints().single().coverage
            coverage.covers("doc/README.md") shouldBe true
            coverage.covers("doc/guide/Intro.md") shouldBe false
        }

        "module ブロックでは直下のファイル宣言だけを覆い、build.gradle.kts は覆わない" {
            val arch = architecture {
                "domain".group {
                    "UseCase" {
                        layout {
                            ":core:domain".module {
                                fileConstraint("module root", scope = FileConstraintRange.DirectOnly, check = silent())
                                "README.md".file()
                                mainSourceSet / KOTLIN_DIRECTORY / "*UseCase".ktFile()
                            }
                        }
                    }
                }
            }

            val coverage = arch.declaredFileConstraints().single().coverage
            coverage.covers("core/domain/README.md") shouldBe true
            coverage.covers("core/domain/src/main/kotlin/GetUserUseCase.kt") shouldBe false
            coverage.covers("core/domain/build.gradle.kts") shouldBe false
        }

        "自分で書いた build.gradle.kts は module ブロックの直下として覆う" {
            val arch = architecture {
                "build".group {
                    "Scripts" {
                        layout {
                            ":core:domain".module {
                                fileConstraint("build script", scope = FileConstraintRange.DirectOnly, check = silent())
                                "build.gradle".ktsFile()
                            }
                        }
                    }
                }
            }

            arch.declaredFileConstraints().single().coverage
                .covers("core/domain/build.gradle.kts") shouldBe true
        }

        "layoutPath と paths は DirectOnly でも変わらない" {
            val arch = architecture {
                "util".group {
                    "Util" {
                        layout {
                            "util" {
                                fileConstraint("stdlib only", scope = FileConstraintRange.DirectOnly, check = silent())
                                "*.kt".file()
                            }
                        }
                    }
                }
            }

            val declared = arch.declaredFileConstraints().single()
            declared.layoutPath shouldBe "util"
            declared.paths shouldBe listOf("util")
        }
    }

    "ディレクトリを持たない場所では書けない" - {
        "役割直下に書くと architecture { } の評価中に落ちる" {
            val thrown = shouldThrow<KatachiFileConstraintDirectOnlyWithoutDirectoryException> {
                architecture {
                    "util".group {
                        "Util" {
                            fileConstraint("stdlib only", scope = FileConstraintRange.DirectOnly, check = silent())
                            layout { "util" { "*.kt".file() } }
                        }
                    }
                }
            }

            thrown.name shouldBe "stdlib only"
            thrown.declaredAt.fileName shouldBe "FileConstraintDirectOnlySpec.kt"
            thrown.message shouldContain "FileConstraintDirectOnlySpec.kt:"
        }

        "layout { } 直下に書くと layout の評価時に落ちる" {
            val arch = architecture {
                "util".group {
                    "Util" {
                        layout {
                            fileConstraint(scope = FileConstraintRange.DirectOnly, check = silent())
                            "*.kt".file()
                        }
                    }
                }
            }

            val thrown = shouldThrow<KatachiFileConstraintDirectOnlyWithoutDirectoryException> {
                arch.declaredFileConstraints()
            }
            thrown.name shouldBe null
        }

        "\":\".module { } の中に書くと layout の評価時に落ちる" {
            val arch = architecture {
                "build".group {
                    "Settings" {
                        layout {
                            ":".module {
                                fileConstraint("root project", scope = FileConstraintRange.DirectOnly, check = silent())
                                "settings.gradle".ktsFile()
                            }
                        }
                    }
                }
            }

            shouldThrow<KatachiFileConstraintDirectOnlyWithoutDirectoryException> {
                arch.declaredFileConstraints()
            }
        }
    }

    "覆うものが無い DirectOnly" - {
        "直下にファイル宣言が無いと layout の評価時に落ちる" {
            val arch = architecture {
                "util".group {
                    "Util" {
                        layout {
                            "util" {
                                fileConstraint("stdlib only", scope = FileConstraintRange.DirectOnly, check = silent())
                                "ksp" { "*.kt".file() }
                            }
                        }
                    }
                }
            }

            val thrown = shouldThrow<KatachiFileConstraintDirectOnlyCoversNothingException> {
                arch.declaredFileConstraints()
            }
            thrown.name shouldBe "stdlib only"
            thrown.layoutPath shouldBe "util"
            thrown.declaredAt.fileName shouldBe "FileConstraintDirectOnlySpec.kt"
        }

        "module が足す build.gradle.kts だけでは覆うものがあることにならない" {
            val arch = architecture {
                "domain".group {
                    "UseCase" {
                        layout {
                            ":core:domain".module {
                                fileConstraint("module root", scope = FileConstraintRange.DirectOnly, check = silent())
                                mainSourceSet / KOTLIN_DIRECTORY / "*UseCase".ktFile()
                            }
                        }
                    }
                }
            }

            shouldThrow<KatachiFileConstraintDirectOnlyCoversNothingException> {
                arch.declaredFileConstraints()
            }.layoutPath shouldBe "core/domain"
        }

        "DirectOnly でない制約は覆うものが無くても従来どおり落ちない" {
            val arch = architecture {
                "build".group {
                    "Generated" {
                        layout {
                            "generated" {
                                fileConstraint("nothing declared", check = silent())
                            }
                        }
                    }
                }
            }

            arch.declaredFileConstraints().single().name shouldBe "nothing declared"
        }
    }
})
