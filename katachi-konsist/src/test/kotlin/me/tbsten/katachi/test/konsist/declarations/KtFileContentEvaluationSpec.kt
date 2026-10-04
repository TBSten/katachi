@file:OptIn(InternalKatachiApi::class)

package me.tbsten.katachi.test.konsist.declarations

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.types.shouldBeInstanceOf
import java.util.Collections
import me.tbsten.katachi.InternalKatachiApi
import me.tbsten.katachi.check.FileConstraintCheck
import me.tbsten.katachi.check.UncheckedFileConstraint
import me.tbsten.katachi.check.UncheckedFileConstraintReason
import me.tbsten.katachi.check.Violation
import me.tbsten.katachi.check.internal.validate
import me.tbsten.katachi.dsl.DeclarationSite
import me.tbsten.katachi.dsl.architecture
import me.tbsten.katachi.dsl.files.internal.RealFileSystem
import me.tbsten.katachi.dsl.gradle.div
import me.tbsten.katachi.dsl.gradle.kotlin
import me.tbsten.katachi.dsl.gradle.mainSourceSet
import me.tbsten.katachi.dsl.gradle.module
import me.tbsten.katachi.dsl.kotlin.ktFile
import me.tbsten.katachi.dsl.wholeTree
import me.tbsten.katachi.konsist.declarations.KatachiKtFileContentCaptureUnavailableException
import me.tbsten.katachi.konsist.declarations.KatachiKtFileContentDuplicateException
import me.tbsten.katachi.konsist.declarations.KatachiKtFileContentModifierException
import me.tbsten.katachi.konsist.declarations.KatachiKtFileContentNothingWrittenException
import me.tbsten.katachi.konsist.declarations.KatachiKtFileContentPatternException
import me.tbsten.katachi.konsist.declarations.KatachiKtFileContentUnknownCaptureException
import me.tbsten.katachi.konsist.declarations.internal.fileNameOf
import me.tbsten.katachi.konsist.declarations.ktFileContent
import me.tbsten.katachi.test.konsist.fixtureProject

/**
 * `ktFileContent { }` registered as a constraint and run by `FileConstraintCheck()`: the block runs
 * once per covered file with that file's `fileName` and `captureValue`, and what is wrong with the
 * definition comes out as `[UncheckedFileConstraint]`.
 *
 * The sources satisfy the rules they are checked against, so these hold whether or not the
 * matching rejects anything.
 */

/** `class <name>`, in a package of its own: one file whose only declaration is named [name]. */
private fun classNamed(name: String): String = "package com.example\n\nclass $name\n"

/** The one `[UncheckedFileConstraint]` of [violations]. */
private fun List<Violation>.singleUnchecked(): UncheckedFileConstraint {
    val unchecked = filterIsInstance<UncheckedFileConstraint>()
    unchecked.size shouldBe 1
    return unchecked.single()
}

class KtFileContentEvaluationSpec : FreeSpec({
    "ファイルごとの評価" - {
        "fileName と captureValue に、そのファイルの値が入る" {
            val seen = Collections.synchronizedList(mutableListOf<String>())
            val violations = ktFileContentRun(
                "feature/home/HomeViewModel.kt" to classNamed("HomeViewModel"),
                "feature/home/Platform.android.kt" to classNamed("Platform"),
                "feature/settings/SettingsViewModel.kt" to classNamed("SettingsViewModel"),
                paths = { "feature" / capture("feature") / "*".ktFile() },
            ) {
                ktFileContent {
                    seen += "$fileName ${captureValue("feature")}"
                    `class`(fileName)
                }
            }

            violations.shouldBeEmpty()
            // Read while the definition is built, then once more when the check starts: both with stand-ins.
            seen.take(2) shouldBe listOf("Sample <feature>", "Sample <feature>")
            seen.drop(2) shouldContainExactlyInAnyOrder listOf(
                "HomeViewModel home",
                "Platform home",
                "SettingsViewModel settings",
            )
        }

        "fileName はファイル名の最初の . より前" {
            fileNameOf("feature/home/Platform.android.kt") shouldBe "Platform"
            fileNameOf("GetUserUseCase.kt") shouldBe "GetUserUseCase"
        }

        "代わりの値でだけ落ちるブロックは、検査の始めでも落とさず、ファイルの値で評価する" {
            val violations = ktFileContentRun(
                "src/HomeScreenView.kt" to classNamed("View"),
                paths = { "src" / "*".ktFile() },
            ) {
                ktFileContent { `class`(fileName.substring(10)) }
            }

            violations.filterIsInstance<UncheckedFileConstraint>().shouldBeEmpty()
        }

        "ファイルの値で利用者のブロックが落ちれば、その例外で UncheckedFileConstraint" {
            val violations = ktFileContentRun(
                "src/HomeScreen.kt" to classNamed("HomeScreen"),
                paths = { "src" / "*".ktFile() },
            ) {
                ktFileContent { `class`(fileName.substring(20)) }
            }

            violations.singleUnchecked().cause.shouldBeInstanceOf<StringIndexOutOfBoundsException>()
        }

        "代わりの値では通り、本当のファイルでだけ誤る行は、そのファイルの検査で出る" {
            val violations = ktFileContentRun(
                "src/HomeScreen.kt" to classNamed("HomeScreen"),
                paths = { "src" / "*".ktFile() },
            ) {
                ktFileContent {
                    if (fileName == "Sample") `class`(fileName) else data.`interface`(fileName)
                }
            }

            val unchecked = violations.singleUnchecked()
            unchecked.reason shouldBe UncheckedFileConstraintReason.Failed
            unchecked.cause.shouldBeInstanceOf<KatachiKtFileContentModifierException>().line shouldBe
                "data interface HomeScreen"
        }
    }

    "capture の名前" - {
        "役割のパスに無い名前は、検査の始めに UnknownCapture" {
            var site: DeclarationSite? = null
            val violations = ktFileContentRun(
                "feature/home/HomeScreen.kt" to classNamed("HomeScreen"),
                paths = { "feature" / capture("feature") / "*".ktFile() },
            ) {
                site = DeclarationSite.capture(); ktFileContent { `class`(captureValue("featur") + "Screen") }
            }

            val unchecked = violations.singleUnchecked()
            unchecked.constraintName shouldBe "ktFileContent"
            unchecked.declaredAt shouldBe site
            val cause = unchecked.cause.shouldBeInstanceOf<KatachiKtFileContentUnknownCaptureException>()
            cause.name shouldBe "featur"
            cause.role shouldBe "Screen"
            cause.available shouldBe listOf("feature")
            cause.declaredAt shouldBe site
            cause.message shouldBe """
                captureValue("featur") in ktFileContent { } at $site names no capture of role Screen.
                The role's layout captures: feature.
                Use one of those names, or add capture("featur") to the layout path.
            """.trimIndent()
        }

        "片方のパスにしか無い capture は、無い方のファイルで CaptureUnavailable" {
            val violations = ktFileContentRun(
                "feature/home/HomeScreen.kt" to classNamed("HomeScreen"),
                "core/CoreScreen.kt" to classNamed("CoreScreen"),
                paths = {
                    "feature" / capture("feature") / "*Screen".ktFile()
                    "core" / "*Screen".ktFile()
                },
            ) {
                ktFileContent { `class`(captureValue("feature").replaceFirstChar { it.uppercase() } + "Screen") }
            }

            val cause = violations.singleUnchecked().cause
                .shouldBeInstanceOf<KatachiKtFileContentCaptureUnavailableException>()
            cause.name shouldBe "feature"
            cause.file shouldBe "core/CoreScreen.kt"
            cause.role shouldBe "Screen"
            cause.message.orEmpty().lines().first() shouldContain "core/CoreScreen.kt (role Screen, ktFileContent { } at "
            cause.message.orEmpty().filter { it.code > 127 } shouldBe ""
        }
    }

    "二重の検出" - {
        "同じ役割の 2 つ目の ktFileContent は Duplicate" {
            var first: DeclarationSite? = null
            var second: DeclarationSite? = null
            val violations = ktFileContentRun(
                "src/HomeScreen.kt" to classNamed("HomeScreen"),
                paths = { "src" / "*".ktFile() },
            ) {
                first = DeclarationSite.capture(); ktFileContent { `class`(fileName) }
                second = DeclarationSite.capture(); ktFileContent { `class`(fileName) }
            }

            val unchecked = violations.singleUnchecked()
            unchecked.declaredAt shouldBe second
            val cause = unchecked.cause.shouldBeInstanceOf<KatachiKtFileContentDuplicateException>()
            cause.firstDeclaredAt shouldBe first
            cause.declaredAt shouldBe second
            cause.message.orEmpty().lines().first() shouldBe
                "Role Screen declares ktFileContent { } twice: at $first and at $second."
        }

        "ワイルドカードのモジュールキーの中のブロックは、モジュールごとに評価されても二重にならない" {
            // The module block runs once per module, so this one call makes one instance per module.
            val seen = Collections.synchronizedList(mutableListOf<String>())
            val violations = fixtureProject(
                "feature/home/build.gradle.kts" to "",
                "feature/home/src/main/kotlin/HomeScreen.kt" to classNamed("HomeScreen"),
                "feature/settings/build.gradle.kts" to "",
                "feature/settings/src/main/kotlin/SettingsScreen.kt" to classNamed("SettingsScreen"),
            ) { root ->
                architecture {
                    files = wholeTree()
                    "Screen" {
                        layout {
                            "gradlew".file()
                            ":feature:*".module {
                                "build.gradle.kts".file()
                                mainSourceSet / kotlin / "*Screen".ktFile()
                                ktFileContent {
                                    seen += fileName
                                    `class`(fileName)
                                }
                            }
                        }
                    }
                }.validate(RealFileSystem(root), FileConstraintCheck())
            }

            violations.filterIsInstance<UncheckedFileConstraint>().shouldBeEmpty()
            seen.filterNot { it == "Sample" } shouldContainExactlyInAnyOrder listOf("HomeScreen", "SettingsScreen")
        }
    }

    "何も書いていないブロック" - {
        "どのファイルでも 1 行も書かなければ NothingWritten" {
            val violations = ktFileContentRun(
                "src/HomeScreen.kt" to classNamed("HomeScreen"),
                paths = { "src" / "*".ktFile() },
            ) {
                ktFileContent { if (fileName == "NoSuchFile") `class`(fileName) }
            }

            val cause = violations.singleUnchecked().cause
                .shouldBeInstanceOf<KatachiKtFileContentNothingWrittenException>()
            cause.role shouldBe "Screen"
        }

        "覆うファイルが 1 つも無ければ何も言わない" {
            // Written in "src" { } rather than on the role, which also covers the fixture's gradlew:
            // a covered file that is not .kt is KatachiKonsistNoKotlinFilesException, as for konsist { }.
            val violations = ktFileContentRun(paths = { "src" { "*".ktFile(); ktFileContent { } } }) { }

            violations.filterIsInstance<UncheckedFileConstraint>().shouldBeEmpty()
        }
    }

    "代わりの値でだけ読めないパターン" - {
        "代わりの値を読んだブロックのパターンの誤りは、定義を読んだ時点では落とさず、本当の値で評価する" {
            val violations = ktFileContentRun(
                "src/HomeViewModel.kt" to "package com.example\n\nclass HomeViewModel\nclass Home\n",
                paths = { "src" / "*ViewModel".ktFile() },
            ) {
                ktFileContent {
                    `class`(fileName)
                    // Empty for the stand-in Sample only: "Home" for the real file.
                    `class`(fileName.dropLast("ViewModel".length))
                }
            }

            violations.shouldBeEmpty()
        }

        "値を読まないブロックのパターンの誤りは、定義を読んだ時点で落とす" {
            shouldThrow<KatachiKtFileContentPatternException> {
                architecture {
                    "Screen" {
                        layout { "src" / "*".ktFile() }
                        ktFileContent { `import`("a..b") }
                    }
                }
            }
        }
    }
})
