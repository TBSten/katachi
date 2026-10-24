@file:OptIn(InternalKatachiApi::class)

package me.tbsten.katachi.test.konsist.declarations

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.string.shouldContain
import me.tbsten.katachi.InternalKatachiApi
import me.tbsten.katachi.check.internal.report
import me.tbsten.katachi.dsl.KatachiFileConstraintNameException
import me.tbsten.katachi.dsl.kotlin.ktFile
import me.tbsten.katachi.konsist.declarations.KatachiKtFileContentDuplicateException
import me.tbsten.katachi.konsist.declarations.ktFileContent

/**
 * `"...".ktFileContent { }`: a name for the block, printed as `<name> (ktFileContent)` in the
 * report; and the one-block-per-role rule on the role's page, as in the check.
 */
private fun classNamed(name: String): String = "package com.example\n\nclass $name\n"

class KtFileContentNamedSpec : FreeSpec({
    "名前を付けた ktFileContent" - {
        "レポートの FileConstraint に「<名前> (ktFileContent)」と出る" {
            val violations = ktFileContentRun(
                "core/domain/GetUserUseCase.kt" to classNamed("Other"),
                paths = { "core/domain" / "*UseCase".ktFile() },
            ) {
                "UseCase の形".ktFileContent { required(`class`, fileName) }
            }

            violations.report() shouldContain "FileConstraint: \"UseCase の形 (ktFileContent)\""
        }

        "名前が無い書き方は今までどおり ktFileContent と出る" {
            val violations = ktFileContentRun(
                "core/domain/GetUserUseCase.kt" to classNamed("Other"),
                paths = { "core/domain" / "*UseCase".ktFile() },
            ) {
                ktFileContent { required(`class`, fileName) }
            }

            violations.report() shouldContain "FileConstraint: \"ktFileContent\""
        }

        "規則を満たすファイルでは名前を付けても違反は出ない" {
            ktFileContentRun(
                "core/domain/GetUserUseCase.kt" to classNamed("GetUserUseCase"),
                paths = { "core/domain" / "*UseCase".ktFile() },
            ) {
                "UseCase の形".ktFileContent { required(`class`, fileName) }
            }.shouldBeEmpty()
        }

        "空白だけの名前は konsist { } と同じく宣言の時に例外になる" {
            shouldThrow<KatachiFileConstraintNameException> {
                ktFileContentRun(paths = { "core" / "*".ktFile() }) { "  ".ktFileContent { required(`class`, fileName) } }
            }
            shouldThrow<KatachiFileConstraintNameException> {
                ktFileContentRun(paths = { "core" / "*".ktFile() }) { "a\nb".ktFileContent { required(`class`, fileName) } }
            }
        }
    }

    "名前の確かめはブロックの評価より先" - {
        "空白だけの名前とブロックの書き間違いが重なると、名前の例外が出る" {
            shouldThrow<KatachiFileConstraintNameException> {
                ktFileContentRun(paths = { "core" / "*".ktFile() }) { "  ".ktFileContent { annotated("X") } }
            }
        }
    }

    "役割ページでの二重の検出" - {
        "1 つの役割に 2 つ書くと、役割ページでも Duplicate になる" {
            shouldThrow<KatachiKtFileContentDuplicateException> {
                rolePageOf {
                    "Screen".role {
                        layout { "src" / "*".ktFile() }
                        "前半".ktFileContent { required(`class`, fileName) }
                        ktFileContent { required(`interface`, fileName) }
                    }
                }
            }
        }

        "役割ページの Constraints に「<名前> (ktFileContent)」が出る" {
            val page = rolePageOf {
                "UseCase".role {
                    example("GetUserUseCase")
                    layout { "domain" / "*UseCase".ktFile() }
                    "UseCase の形".ktFileContent { required(`class`, fileName) }
                }
            }.getValue("UseCase.md")

            page shouldContain "UseCase の形 (ktFileContent)"
        }

        "別々の役割に 1 つずつなら役割ページは作れる" {
            rolePageOf {
                "A".role {
                    layout { "a" / "*".ktFile() }
                    ktFileContent { required(`class`, fileName) }
                }
                "B".role {
                    layout { "b" / "*".ktFile() }
                    ktFileContent { required(`class`, fileName) }
                }
            }
        }
    }
})
