package me.tbsten.katachi.test.konsist.declarations

import io.kotest.assertions.throwables.shouldNotThrowAny
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.shouldBe
import me.tbsten.katachi.dsl.DeclarationSite
import me.tbsten.katachi.dsl.architecture
import me.tbsten.katachi.dsl.kotlin.ktFile
import me.tbsten.katachi.konsist.declarations.KatachiKtFileContentLineException
import me.tbsten.katachi.konsist.declarations.KatachiKtFileContentModifierException
import me.tbsten.katachi.konsist.declarations.KatachiKtFileContentPatternException
import me.tbsten.katachi.konsist.declarations.ktFileContent

/**
 * `ktFileContent { }` runs its block once with stand-in values as soon as it is called, so a line no
 * declaration can match fails while `architecture { }` is being built, pointing at the line.
 *
 * Each site is captured on the same line as the DSL call it is compared with, so the comparison
 * is exact rather than "somewhere in this file".
 */
class KtFileContentDeclarationTimeSpec : FreeSpec({
    "architecture { } を組んだ時点で例外になる" - {
        "付けられない修飾子は、その行を指す" {
            var site: DeclarationSite? = null
            val error = shouldThrow<KatachiKtFileContentModifierException> {
                architecture {
                    "UiState" {
                        layout { "*UiState".ktFile() }
                        ktFileContent {
                            site = DeclarationSite.capture(); data.`interface`(fileName)
                        }
                    }
                }
            }

            error.declaredAt shouldBe site
            error.line shouldBe "data interface Sample"
        }

        "読めないパターンは、そのパターンを書いた行を指す" {
            var site: DeclarationSite? = null
            val error = shouldThrow<KatachiKtFileContentPatternException> {
                architecture {
                    "Screen" {
                        layout { "*Screen".ktFile() }
                        ktFileContent {
                            `import`("kotlin.**")
                            site = DeclarationSite.capture(); `import`("androidx..compose")
                        }
                    }
                }
            }

            error.declaredAt shouldBe site
        }

        "行の書き方の誤りは、その行を指す" {
            var site: DeclarationSite? = null
            val error = shouldThrow<KatachiKtFileContentLineException> {
                architecture {
                    "Screen" {
                        layout { "*Screen".ktFile() }
                        ktFileContent {
                            site = DeclarationSite.capture(); `import`("kotlin.**").except("kotlin.io.**")
                        }
                    }
                }
            }

            error.declaredAt shouldBe site
        }

        "クラスの { } の中の行も、その行を指す" {
            var site: DeclarationSite? = null
            val error = shouldThrow<KatachiKtFileContentModifierException> {
                architecture {
                    "Screen" {
                        layout { "*Screen".ktFile() }
                        ktFileContent {
                            `class`(fileName) {
                                site = DeclarationSite.capture(); abstract.open.`fun`("render")
                            }
                        }
                    }
                }
            }

            error.declaredAt shouldBe site
            error.declaredAt.fileName shouldBe "KtFileContentDeclarationTimeSpec.kt"
        }

        "代わりの値でしか通らない分岐の誤りも、その時点で出る" {
            shouldThrow<KatachiKtFileContentModifierException> {
                architecture {
                    "Screen" {
                        layout { "*Screen".ktFile() }
                        ktFileContent {
                            if (fileName == "Sample") data.`interface`(fileName) else `class`(fileName)
                        }
                    }
                }
            }
        }
    }

    "代わりの値" - {
        "fileName は Sample、captureValue(x) は <x> で 1 回だけ評価する" {
            val seen = mutableListOf<String>()
            architecture {
                "Screen" {
                    layout { "*Screen".ktFile() }
                    ktFileContent {
                        seen += "$fileName ${captureValue("feature")} ${captureValue("anything")}"
                        `class`(fileName)
                    }
                }
            }

            seen shouldBe listOf("Sample <feature> <anything>")
        }

        "代わりの値で利用者のブロックが katachi のものでない例外を投げても architecture { } は通る" {
            shouldNotThrowAny {
                architecture {
                    "Screen" {
                        layout { "*Screen".ktFile() }
                        ktFileContent {
                            `class`(captureValue("feature").substring(20))
                        }
                    }
                }
            }
        }
    }
})
