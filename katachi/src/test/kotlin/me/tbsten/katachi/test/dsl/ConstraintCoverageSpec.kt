package me.tbsten.katachi.test.dsl

import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import me.tbsten.katachi.dsl.architecture
import me.tbsten.katachi.dsl.gradle.mainSourceSet
import me.tbsten.katachi.dsl.gradle.module
import me.tbsten.katachi.dsl.kotlin.ktFile
import me.tbsten.katachi.dsl.kotlin.ktsFile
import me.tbsten.katachi.dsl.reportPath

/**
 * Which files a constraint turned out to be about, and which path a report opens with.
 *
 * Where a constraint was written is [ConstraintDeclarationSpec]'s.
 *
 * NOTE: このファイルのパッケージを `me.tbsten.katachi.dsl` にしてはいけない。
 * captureDeclarationSite() がライブラリ自身のフレームとして読み飛ばしてしまう。
 */

/** The `kotlin` source directory, written out so that this spec needs no module package. */
private const val KOTLIN_DIRECTORY: String = "kotlin"

class ConstraintCoverageSpec : FreeSpec({
    "覆う範囲" - {
        "役割直下の制約が全 layout の和集合を覆う" {
            val arch = architecture {
                "domain".group {
                    "UseCase" {
                        constraint("role wide", check = silent())
                        layout { "alpha" / "*.kt".file() }
                        layout { "beta" / "*.kt".file() }
                    }
                }
            }

            val coverage = arch.declaredConstraints().single().coverage
            coverage.covers("alpha/GetUserUseCase.kt") shouldBe true
            coverage.covers("beta/GetUserUseCase.kt") shouldBe true
            coverage.covers("gamma/GetUserUseCase.kt") shouldBe false
        }

        "layout { } 直下の制約はそのブロックのエントリだけを覆う" {
            val arch = architecture {
                "domain".group {
                    "UseCase" {
                        layout {
                            constraint("alpha only", check = silent())
                            "alpha" / "*.kt".file()
                        }
                        layout { "beta" / "*.kt".file() }
                    }
                }
            }

            val coverage = arch.declaredConstraints().single().coverage
            coverage.covers("alpha/GetUserUseCase.kt") shouldBe true
            coverage.covers("beta/GetUserUseCase.kt") shouldBe false
        }

        "ディレクトリブロックの制約はその部分木だけを覆う" {
            val arch = architecture {
                "domain".group {
                    "UseCase" {
                        layout {
                            "alpha" {
                                constraint("alpha only", check = silent())
                                "nested" / "*.kt".file()
                            }
                            "beta" / "*.kt".file()
                        }
                    }
                }
            }

            val coverage = arch.declaredConstraints().single().coverage
            coverage.covers("alpha/nested/GetUserUseCase.kt") shouldBe true
            coverage.covers("beta/GetUserUseCase.kt") shouldBe false
        }

        "anyFile() を書いたディレクトリの直下のファイルも覆う" {
            val arch = architecture {
                "build".group {
                    "Generated" {
                        layout {
                            "generated" {
                                constraint("anything here", check = silent())
                                anyFile()
                            }
                        }
                    }
                }
            }

            val coverage = arch.declaredConstraints().single().coverage
            coverage.covers("generated/Whatever.kt") shouldBe true
            coverage.covers("generated/nested/Whatever.kt") shouldBe false
        }

        "module の制約が build.gradle.kts を覆わない" {
            val arch = architecture {
                "domain".group {
                    "UseCase" {
                        layout {
                            ":core:domain".module {
                                constraint("in core/domain", check = silent())
                                mainSourceSet / KOTLIN_DIRECTORY / "*UseCase".ktFile()
                            }
                        }
                    }
                }
            }

            val coverage = arch.declaredConstraints().single().coverage
            coverage.covers("core/domain/src/main/kotlin/GetUserUseCase.kt") shouldBe true
            coverage.covers("core/domain/build.gradle.kts") shouldBe false
        }

        "ルートプロジェクトの module の制約が build.gradle.kts を覆わない" {
            val arch = architecture {
                "build".group {
                    "Settings" {
                        layout {
                            ":".module {
                                constraint("root project", check = silent())
                                "settings.gradle".ktsFile()
                            }
                        }
                    }
                }
            }

            val coverage = arch.declaredConstraints().single().coverage
            coverage.covers("settings.gradle.kts") shouldBe true
            coverage.covers("build.gradle.kts") shouldBe false
        }

        "ルートプロジェクトの module の制約は layout 全体ではなくそのブロックのエントリだけを覆う" {
            val arch = architecture {
                "build".group {
                    "Settings" {
                        layout {
                            "docs" / "*.md".file()
                            ":".module {
                                constraint("root project", check = silent())
                                "settings.gradle".ktsFile()
                            }
                        }
                    }
                }
            }

            val coverage = arch.declaredConstraints().single().coverage
            coverage.covers("settings.gradle.kts") shouldBe true
            coverage.covers("docs/README.md") shouldBe false
        }

        "自分で書いた build.gradle.kts は覆う" {
            val arch = architecture {
                "build".group {
                    "Scripts" {
                        layout {
                            ":core:domain".module {
                                constraint("build script too", check = silent())
                                "build.gradle".ktsFile()
                            }
                        }
                    }
                }
            }

            arch.declaredConstraints().single().coverage
                .covers("core/domain/build.gradle.kts") shouldBe true
        }
    }

    "ワイルドカードモジュール" - {
        "展開されたモジュールごとに1つずつ、解決済みの実パス付きで宣言される" {
            val arch = architecture {
                "feature".group {
                    "Screen" {
                        layout {
                            ":feature:*".module {
                                constraint("internal であること", check = silent())
                                mainSourceSet / KOTLIN_DIRECTORY / "*Screen".ktFile()
                            }
                        }
                    }
                }
            }

            val declared = arch.declaredConstraints(moduleIndexOf("feature/home", "feature/cart"))
            declared.map { it.layoutPath } shouldBe listOf("feature/cart", "feature/home")
            declared.map { it.paths } shouldBe listOf(listOf("feature/cart"), listOf("feature/home"))
            declared[0].coverage.covers("feature/cart/src/main/kotlin/CartScreen.kt") shouldBe true
            declared[0].coverage.covers("feature/home/src/main/kotlin/HomeScreen.kt") shouldBe false
        }

        "1つもマッチしない役割では制約が1つも宣言されず、例外にもならない" {
            val arch = architecture {
                "feature".group {
                    "Screen" {
                        layout {
                            ":feature:*".module {
                                constraint("internal であること", check = silent())
                                mainSourceSet / KOTLIN_DIRECTORY / "*Screen".ktFile()
                            }
                        }
                    }
                }
            }

            arch.declaredConstraints(moduleIndexOf("app")).shouldBeEmpty()
        }
    }

    "layoutPath" - {
        "役割直下と layout { } 直下では null になる" {
            val arch = architecture {
                "domain".group {
                    "UseCase" {
                        constraint("role wide", check = silent())
                        layout {
                            constraint("layout wide", check = silent())
                            "useCase" / "*.kt".file()
                        }
                    }
                }
            }

            arch.declaredConstraints().map { it.layoutPath } shouldBe listOf(null, null)
        }
    }

    "reportPath" - {
        "ワイルドカードを含まないパスを優先する" {
            val arch = architecture {
                "domain".group {
                    "UseCase" {
                        constraint("role wide", check = silent())
                        layout { "feature" / "*" / "useCase" / "*.kt".file() }
                        layout { "core/domain/useCase" / "*.kt".file() }
                    }
                }
            }

            val declared = arch.declaredConstraints().single()
            declared.paths shouldBe listOf("feature/*/useCase", "core/domain/useCase")
            declared.reportPath shouldBe "core/domain/useCase"
        }

        "候補が1つも無いときは定義ファイル名になり、ドットにならない" {
            val arch = architecture {
                "domain".group {
                    "UseCase" {
                        constraint("role wide", check = silent())
                        layout { }
                    }
                }
            }

            val declared = arch.declaredConstraints().single()
            declared.paths.shouldBeEmpty()
            declared.reportPath shouldBe "ConstraintCoverageSpec.kt"
            declared.reportPath shouldNotBe "."
        }

        "ディレクトリしか宣言していない layout でもドットにならない" {
            val arch = architecture {
                "build".group {
                    "Generated" {
                        constraint("role wide", check = silent())
                        layout { "generated" { } }
                    }
                }
            }

            arch.declaredConstraints().single().reportPath shouldBe "generated"
        }
    }
})
