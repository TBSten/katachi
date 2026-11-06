package me.tbsten.katachi.test.dsl

import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import me.tbsten.katachi.dsl.architecture
import me.tbsten.katachi.dsl.gradle.gradle
import me.tbsten.katachi.dsl.gradle.mainSourceSet
import me.tbsten.katachi.dsl.kotlin.ktFile
import me.tbsten.katachi.dsl.kotlin.ktsFile
import me.tbsten.katachi.dsl.internal.reportPath

/**
 * Which files a constraint turned out to be about, and which path a report opens with.
 *
 * Where a constraint was written is [FileConstraintDeclarationSpec]'s.
 *
 * NOTE: このファイルのパッケージを `me.tbsten.katachi.dsl` にしてはいけない。
 * captureDeclarationSite() がライブラリ自身のフレームとして読み飛ばしてしまう。
 */

/** The `kotlin` source directory, written out so that this spec needs no module package. */
private const val KOTLIN_DIRECTORY: String = "kotlin"

class FileConstraintCoverageSpec : FreeSpec({
    "覆う範囲" - {
        "役割直下の制約が全 layout の和集合を覆う" {
            val arch = architecture {
                "domain".group {
                    "UseCase".role {
                        fileConstraint("role wide", check = silent())
                        layout { "alpha" / "*.kt".file() }
                        layout { "beta" / "*.kt".file() }
                    }
                }
            }

            val coverage = arch.declaredFileConstraints().single().coverage
            coverage.covers("alpha/GetUserUseCase.kt") shouldBe true
            coverage.covers("beta/GetUserUseCase.kt") shouldBe true
            coverage.covers("gamma/GetUserUseCase.kt") shouldBe false
        }

        "束縛の無い同名のパスは * と同じに覆い、束縛で落ちるファイルは覆わない" {
            val arch = architecture {
                "domain".group {
                    "Screen".role {
                        fileConstraint("bound", check = silent())
                        layout { "feature" / capture("f") / capture("f") / "*.kt".file() }
                    }
                }
            }

            val coverage = arch.declaredFileConstraints().single().coverage
            coverage.covers("feature/home/home/A.kt") shouldBe true
            coverage.covers("feature/home/settings/A.kt") shouldBe false
        }

        "layout { } 直下の制約はそのブロックのエントリだけを覆う" {
            val arch = architecture {
                "domain".group {
                    "UseCase".role {
                        layout {
                            fileConstraint("alpha only", check = silent())
                            "alpha" / "*.kt".file()
                        }
                        layout { "beta" / "*.kt".file() }
                    }
                }
            }

            val coverage = arch.declaredFileConstraints().single().coverage
            coverage.covers("alpha/GetUserUseCase.kt") shouldBe true
            coverage.covers("beta/GetUserUseCase.kt") shouldBe false
        }

        "ディレクトリブロックの制約はその部分木だけを覆う" {
            val arch = architecture {
                "domain".group {
                    "UseCase".role {
                        layout {
                            "alpha" {
                                fileConstraint("alpha only", check = silent())
                                "nested" / "*.kt".file()
                            }
                            "beta" / "*.kt".file()
                        }
                    }
                }
            }

            val coverage = arch.declaredFileConstraints().single().coverage
            coverage.covers("alpha/nested/GetUserUseCase.kt") shouldBe true
            coverage.covers("beta/GetUserUseCase.kt") shouldBe false
        }

        "anyFile() を書いたディレクトリの直下のファイルも覆う" {
            val arch = architecture {
                "build".group {
                    "Generated".role {
                        layout {
                            "generated" {
                                fileConstraint("anything here", check = silent())
                                anyFile()
                            }
                        }
                    }
                }
            }

            val coverage = arch.declaredFileConstraints().single().coverage
            coverage.covers("generated/Whatever.kt") shouldBe true
            coverage.covers("generated/nested/Whatever.kt") shouldBe false
        }

        "宣言していない build.gradle.kts は覆わない" {
            val arch = architecture {
                "domain".group {
                    "UseCase".role {
                        layout {
                            "core/domain" {
                                fileConstraint("in core/domain", check = silent())
                                mainSourceSet / KOTLIN_DIRECTORY / "*UseCase".ktFile()
                            }
                        }
                    }
                }
            }

            val coverage = arch.declaredFileConstraints().single().coverage
            coverage.covers("core/domain/src/main/kotlin/GetUserUseCase.kt") shouldBe true
            coverage.covers("core/domain/build.gradle.kts") shouldBe false
        }

        "gradle() の BuildScript の制約は build.gradle.kts と build.gradle を覆い、build は覆わない" {
            val arch = architecture {
                gradle {
                    buildScript { fileConstraint("build scripts", check = silent()) }
                }
            }

            val coverage = arch.declaredFileConstraints(moduleIndexOf("app")).single().coverage
            coverage.covers("app/build.gradle.kts") shouldBe true
            coverage.covers("app/build.gradle") shouldBe true
            coverage.covers("app/build/x") shouldBe false
        }

        "layout { } 直下の制約も宣言していない build.gradle.kts は覆わない" {
            val arch = architecture {
                "build".group {
                    "Settings".role {
                        layout {
                            fileConstraint("root project", check = silent())
                            "settings.gradle".ktsFile()
                        }
                    }
                }
            }

            val coverage = arch.declaredFileConstraints().single().coverage
            coverage.covers("settings.gradle.kts") shouldBe true
            coverage.covers("build.gradle.kts") shouldBe false
        }

        "自分で書いた build.gradle.kts は覆う" {
            val arch = architecture {
                "build".group {
                    "Scripts".role {
                        layout {
                            "core/domain" {
                                fileConstraint("build script too", check = silent())
                                "build.gradle".ktsFile()
                            }
                        }
                    }
                }
            }

            arch.declaredFileConstraints().single().coverage
                .covers("core/domain/build.gradle.kts") shouldBe true
        }
    }

    "layoutPath" - {
        "役割直下と layout { } 直下では null になる" {
            val arch = architecture {
                "domain".group {
                    "UseCase".role {
                        fileConstraint("role wide", check = silent())
                        layout {
                            fileConstraint("layout wide", check = silent())
                            "useCase" / "*.kt".file()
                        }
                    }
                }
            }

            arch.declaredFileConstraints().map { it.layoutPath } shouldBe listOf(null, null)
        }
    }

    "reportPath" - {
        "ワイルドカードを含まないパスを優先する" {
            val arch = architecture {
                "domain".group {
                    "UseCase".role {
                        fileConstraint("role wide", check = silent())
                        layout { "feature" / "*" / "useCase" / "*.kt".file() }
                        layout { "core/domain/useCase" / "*.kt".file() }
                    }
                }
            }

            val declared = arch.declaredFileConstraints().single()
            declared.paths shouldBe listOf("feature/*/useCase", "core/domain/useCase")
            declared.reportPath shouldBe "core/domain/useCase"
        }

        "候補が1つも無いときは定義ファイル名になり、ドットにならない" {
            val arch = architecture {
                "domain".group {
                    "UseCase".role {
                        fileConstraint("role wide", check = silent())
                        layout { }
                    }
                }
            }

            val declared = arch.declaredFileConstraints().single()
            declared.paths.shouldBeEmpty()
            declared.reportPath shouldBe "FileConstraintCoverageSpec.kt"
            declared.reportPath shouldNotBe "."
        }

        "ディレクトリしか宣言していない layout でもドットにならない" {
            val arch = architecture {
                "build".group {
                    "Generated".role {
                        fileConstraint("role wide", check = silent())
                        layout { "generated" { } }
                    }
                }
            }

            arch.declaredFileConstraints().single().reportPath shouldBe "generated"
        }
    }
})
