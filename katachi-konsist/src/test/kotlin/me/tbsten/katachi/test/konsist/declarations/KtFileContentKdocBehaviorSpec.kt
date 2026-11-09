@file:OptIn(InternalKatachiApi::class)

package me.tbsten.katachi.test.konsist.declarations

import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldNotBeEmpty
import io.kotest.matchers.string.shouldContain
import me.tbsten.katachi.InternalKatachiApi
import me.tbsten.katachi.check.internal.reportForTest
import me.tbsten.katachi.dsl.kotlin.ktFile
import me.tbsten.katachi.konsist.declarations.ktFileContent

/**
 * The behaviours the KDoc of `ktFileContent { }` states, one by one: each is the current
 * behaviour, written down so that the KDoc cannot drift from it.
 */
class KtFileContentKdocBehaviorSpec : FreeSpec({
    "superclass の * は 0 文字以上" - {
        "superclass(\"*NavKey\") は NavKey 自身を親に持つクラスも通す" {
            ktFileContentRun(
                "src/A.kt" to "package a\n\nclass A : NavKey\n",
                paths = { "src" / "*".ktFile() },
            ) {
                ktFileContent {
                    `package`("**")
                    required(`class`, "A").superclass("*NavKey")
                }
            }.shouldBeEmpty()
        }

        "superclass(\"*NavKey\") は StartupNavKey を親に持つクラスも通す" {
            ktFileContentRun(
                "src/A.kt" to "package a\n\nclass A : StartupNavKey\n",
                paths = { "src" / "*".ktFile() },
            ) {
                ktFileContent {
                    `package`("**")
                    required(`class`, "A").superclass("*NavKey")
                }
            }.shouldBeEmpty()
        }

        "候補を並べる書き方は 1 つ目の候補にも効く" {
            ktFileContentRun(
                "src/A.kt" to "package a\n\nclass A : Foo\n",
                paths = { "src" / "*".ktFile() },
            ) {
                ktFileContent {
                    `package`("**")
                    required(`class`, "A").superclass("{Foo,*NavKey}")
                }
            }.shouldBeEmpty()
        }

        "候補を並べる書き方は 2 つ目の候補にも効く" {
            ktFileContentRun(
                "src/A.kt" to "package a\n\nclass A : StartupNavKey\n",
                paths = { "src" / "*".ktFile() },
            ) {
                ktFileContent {
                    `package`("**")
                    required(`class`, "A").superclass("{Foo,*NavKey}")
                }
            }.shouldBeEmpty()
        }
    }

    "重ならない候補を並べる書き方" - {
        "superclass(\"{NavKey,*Route}\") は NavKey も HomeRoute も通す" {
            ktFileContentRun(
                "src/A.kt" to "package a\n\nclass A : NavKey\n",
                "src/B.kt" to "package a\n\nclass B : HomeRoute\n",
                paths = { "src" / "*".ktFile() },
            ) {
                ktFileContent {
                    `package`("**")
                    required(`class`, fileName).superclass("{NavKey,*Route}")
                }
            }.shouldBeEmpty()
        }
    }

    "annotated は書かれた名前で当てる" - {
        "import a.B as C の @C は C で書く" {
            ktFileContentRun(
                "src/A.kt" to "package a\n\nimport b.Inject as Wired\n\n@Wired\nclass A\n",
                paths = { "src" / "*".ktFile() },
            ) {
                ktFileContent {
                    `package`("**")
                    `import`("**")
                    required(annotated("Wired"), `class`, "A")
                }
            }.shouldBeEmpty()
        }

        "完全修飾名で書けば alias の先で当たる" {
            ktFileContentRun(
                "src/A.kt" to "package a\n\nimport b.Inject as Wired\n\n@Wired\nclass A\n",
                paths = { "src" / "*".ktFile() },
            ) {
                ktFileContent {
                    `package`("**")
                    `import`("**")
                    required(annotated("b.Inject"), `class`, "A")
                }
            }.shouldBeEmpty()
        }

        "alias の先の単純名で書いても当たらない" {
            val violations = ktFileContentRun(
                "src/A.kt" to "package a\n\nimport b.Inject as Wired\n\n@Wired\nclass A\n",
                paths = { "src" / "*".ktFile() },
            ) {
                ktFileContent {
                    `package`("**")
                    `import`("**")
                    required(annotated("Inject"), `class`, "A")
                }
            }
            violations.shouldNotBeEmpty()
            violations.reportForTest() shouldContain "@Inject"
        }
    }

    "** は 0 階層以上" - {
        "import(\"X.**\") は X 自身のインポートも通す" {
            ktFileContentRun(
                "src/A.kt" to "package a\n\nimport com.example.App\n\nclass A\n",
                paths = { "src" / "*".ktFile() },
            ) {
                ktFileContent {
                    `package`("**")
                    `import`("com.example.App.**")
                    required(`class`, "A")
                }
            }.shouldBeEmpty()
        }

        "package(\"X.**\") は X 自身のパッケージも通す" {
            ktFileContentRun(
                "src/A.kt" to "package com.example\n\nclass A\n",
                paths = { "src" / "*".ktFile() },
            ) {
                ktFileContent {
                    `package`("com.example.**")
                    required(`class`, "A")
                }
            }.shouldBeEmpty()
        }
    }
})
