package me.tbsten.katachi.test.check

import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeSameInstanceAs
import me.tbsten.katachi.check.FileConstraintCheck
import me.tbsten.katachi.check.internal.validate
import me.tbsten.katachi.dsl.FileConstraint
import me.tbsten.katachi.dsl.gradle.*
import me.tbsten.katachi.dsl.kotlin.ktFile
import me.tbsten.katachi.dsl.kotlin.ktsFile

/**
 * The files each constraint is handed, when one run narrows them once per block.
 *
 * Every constraint of one block shares that block's coverage, so the walk narrows the role's
 * files once per coverage and hands the same list to each of them. What must not change is
 * which files any constraint receives: a constraint of another block, another module or
 * another run still gets exactly its own.
 *
 * NOTE: このファイルのパッケージを `me.tbsten.katachi.check` にしてはいけない。
 * captureDeclarationSite() がライブラリ自身のフレームとして読み飛ばしてしまう。
 */
class FileConstraintFilesSharingSpec : FreeSpec({
    "同じブロックの制約" - {
        "同じブロックに書いた制約は同じファイル一覧を受け取り、絞り込みは1回で済む" {
            val first = RecordingFileConstraint()
            val second = RecordingFileConstraint()
            val third = RecordingFileConstraint()
            val arch = architectureOf {
                "domain".group {
                    "UseCase" {
                        layout {
                            "alpha" {
                                fileConstraint("first", check = first)
                                fileConstraint("second", check = second)
                                fileConstraint("third", check = third)
                                "*.kt".file()
                            }
                        }
                    }
                }
            }

            arch.validate(
                repositoryOf { "alpha" { "B.kt"(); "A.kt"() } },
                FileConstraintCheck(),
            ).shouldBeEmpty()

            val files = first.subjects.single().files
            files shouldBe listOf("alpha/A.kt", "alpha/B.kt")
            second.subjects.single().files shouldBeSameInstanceAs files
            third.subjects.single().files shouldBeSameInstanceAs files
        }

        "受け取った一覧を書き換えようとしても、同じブロックの次の制約には漏れない" {
            val vandal = FileConstraint { subject ->
                runCatching { (subject.files as MutableList<String>).clear() }
                emptyList()
            }
            val after = RecordingFileConstraint()
            val arch = architectureOf {
                "domain".group {
                    "UseCase" {
                        layout {
                            "alpha" {
                                fileConstraint("vandal", check = vandal)
                                fileConstraint("after", check = after)
                                "*.kt".file()
                            }
                        }
                    }
                }
            }

            arch.validate(repositoryOf { "alpha" { "A.kt"(); "B.kt"() } }, FileConstraintCheck())

            after.subjects.single().files shouldBe listOf("alpha/A.kt", "alpha/B.kt")
        }
    }

    "別のブロックの制約" - {
        "役割直下・layout 直下・ディレクトリ内の制約は、それぞれ自分の範囲だけを受け取る" {
            val roleWide = RecordingFileConstraint()
            val layoutWide = RecordingFileConstraint()
            val alphaOnly = RecordingFileConstraint()
            val betaOnly = RecordingFileConstraint()
            val arch = architectureOf {
                "domain".group {
                    "UseCase" {
                        fileConstraint("role wide", check = roleWide)
                        layout {
                            fileConstraint("layout wide", check = layoutWide)
                            "alpha" {
                                fileConstraint("alpha only", check = alphaOnly)
                                "*.kt".file()
                            }
                            "beta" {
                                fileConstraint("beta only", check = betaOnly)
                                "*.kt".file()
                            }
                        }
                        layout { "gamma" / "*.kt".file() }
                    }
                }
            }

            arch.validate(
                repositoryOf {
                    "alpha" { "A.kt"() }
                    "beta" { "B.kt"() }
                    "gamma" { "C.kt"() }
                },
                FileConstraintCheck(),
            ).shouldBeEmpty()

            roleWide.subjects.single().files shouldBe listOf("alpha/A.kt", "beta/B.kt", "gamma/C.kt")
            layoutWide.subjects.single().files shouldBe listOf("alpha/A.kt", "beta/B.kt")
            alphaOnly.subjects.single().files shouldBe listOf("alpha/A.kt")
            betaOnly.subjects.single().files shouldBe listOf("beta/B.kt")
        }

        "別の役割が同じ場所の形を書いても、互いのファイルは受け取らない" {
            val useCases = RecordingFileConstraint()
            val repositories = RecordingFileConstraint()
            val arch = architectureOf {
                "domain".group {
                    "UseCase" {
                        layout {
                            "shared" {
                                fileConstraint("use cases", check = useCases)
                                "*UseCase.kt".file()
                            }
                        }
                    }
                    "Repository" {
                        layout {
                            "shared" {
                                fileConstraint("repositories", check = repositories)
                                "*Repository.kt".file()
                            }
                        }
                    }
                }
            }

            arch.validate(
                repositoryOf { "shared" { "GetUserUseCase.kt"(); "UserRepository.kt"() } },
                FileConstraintCheck(),
            ).shouldBeEmpty()

            useCases.subjects.single().files shouldBe listOf("shared/GetUserUseCase.kt")
            repositories.subjects.single().files shouldBe listOf("shared/UserRepository.kt")
        }

        "ワイルドカードのモジュールキーの制約は、モジュールごとにそのモジュールのファイルだけを受け取る" {
            val recording = RecordingFileConstraint()
            val arch = architectureOf {
                "feature".group {
                    "Screen" {
                        layout {
                            ":".module { "settings.gradle".ktsFile() }
                            ":feature:*".module {
                                fileConstraint("screens", check = recording)
                                mainSourceSet / kotlin / "*Screen".ktFile()
                            }
                        }
                    }
                }
            }

            arch.validate(
                repositoryOf {
                    "build.gradle.kts"()
                    "settings.gradle.kts"()
                    "feature" {
                        "home" { "build.gradle.kts"(); "src/main/kotlin" { "HomeScreen.kt"() } }
                        "cart" { "build.gradle.kts"(); "src/main/kotlin" { "CartScreen.kt"() } }
                    }
                },
                FileConstraintCheck(),
            )

            recording.subjects.map { it.paths to it.files } shouldBe listOf(
                listOf("feature/cart") to listOf("feature/cart/src/main/kotlin/CartScreen.kt"),
                listOf("feature/home") to listOf("feature/home/src/main/kotlin/HomeScreen.kt"),
            )
        }
    }

    "run をまたいだとき" - {
        "同じ定義を別のプロジェクトで検査すると、それぞれのプロジェクトのファイルを受け取る" {
            val recording = RecordingFileConstraint()
            val arch = architectureOf {
                "domain".group {
                    "UseCase" {
                        layout {
                            "alpha" {
                                fileConstraint("alpha only", check = recording)
                                "*.kt".file()
                            }
                        }
                    }
                }
            }

            arch.validate(repositoryOf { "alpha" { "A.kt"() } }, FileConstraintCheck()).shouldBeEmpty()
            arch.validate(repositoryOf { "alpha" { "B.kt"() } }, FileConstraintCheck()).shouldBeEmpty()

            recording.subjects.map { it.files } shouldBe listOf(listOf("alpha/A.kt"), listOf("alpha/B.kt"))
        }
    }
})
