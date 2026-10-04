package me.tbsten.katachi.test.konsist.declarations

import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import me.tbsten.katachi.dsl.gradle.div
import me.tbsten.katachi.dsl.gradle.kotlin
import me.tbsten.katachi.dsl.gradle.mainSourceSet
import me.tbsten.katachi.dsl.gradle.module
import me.tbsten.katachi.dsl.kotlin.ktFile
import me.tbsten.katachi.konsist.declarations.ktFileContent

/**
 * The `## Kotlin file content` section `ktFileContent { }` puts on its role's page (impl-spec 10):
 * the rules evaluated for the role's example file, as tables.
 */
class KtFileContentDocsSpec : FreeSpec({
    "要点の ViewModel を、役割の例のファイルで表にする" {
        val page = rolePageOf {
            "ViewModel".role {
                example("HomeViewModel")
                layout { "feature" / capture("feature") / "*ViewModel".ktFile() }
                ktFileContent {
                    `package`("com.example.feature.${captureValue("feature")}")
                    `import`("kotlin.**")
                    `import`("com.example.domain.**")
                    not.`import`("android.**").except("android.os.**")
                    annotated("HiltViewModel")
                        .public.`class`(fileName).superclass("ViewModel") {
                            public.`val`("uiState", "StateFlow<${fileName.replace("ViewModel", "UiState")}>")
                            public.`fun`("on*")().returns("Unit")
                            private.anythingElse()
                        }
                }
            }
        }.getValue("ViewModel.md")

        page shouldContain """
            ## Kotlin file content

            Example: `HomeViewModel.kt`

            | Package |
            |---|
            | `com.example.feature.<feature>` |

            | Import | |
            |---|---|
            | `kotlin.**` | optional |
            | `com.example.domain.**` | optional |
            | `android.**` except `android.os.**` | not allowed |

            | Declaration | |
            |---|---|
            | `@HiltViewModel public class HomeViewModel : ViewModel` | required |

            ### Members of `class HomeViewModel`

            | Member | |
            |---|---|
            | `public val uiState: StateFlow<HomeUiState>` | required |
            | `public fun on*(): Unit` | optional |
            | `private` (anything) | optional |
        """.trimIndent()
    }

    "入れ子のクラス・空の { }・禁止の行・anythingElse・matchingDirectory" {
        val page = rolePageOf {
            "UseCase".role {
                example("GetUserUseCase")
                layout { "domain" / "*UseCase".ktFile() }
                ktFileContent {
                    `package`(matchingDirectory)
                    not.`package`("com.example.legacy.**")
                    `interface`(fileName) {
                        `class`("Params") { }
                        operator.`fun`("invoke")
                    }
                    not.`class`("*Impl").except("DefaultImpl")
                    (internal or private).anythingElse()
                    anythingElse()
                }
            }
        }.getValue("UseCase.md")

        page shouldContain """
            | Package |
            |---|
            | (the file's directory) |
            | `com.example.legacy.**` (not allowed) |

            | Declaration | |
            |---|---|
            | `interface GetUserUseCase` | required |
            | `class *Impl` except `DefaultImpl` | not allowed |
            | `(internal or private)` (anything) | optional |
            | (anything) | optional |

            ### Members of `interface GetUserUseCase`

            | Member | |
            |---|---|
            | `class Params` | required |
            | `operator fun invoke(...)` | required |

            ### Members of `class GetUserUseCase.Params`

            No members are allowed.
        """.trimIndent()
    }

    "書いていない種類の表は出ない" {
        val page = rolePageOf {
            "Repository".role {
                example("UserRepository")
                layout { "data" / "*Repository".ktFile() }
                ktFileContent { `import`("kotlinx.**") }
            }
        }.getValue("Repository.md")

        page shouldContain "| Import | |"
        page shouldNotContain "| Package |"
        page shouldNotContain "| Declaration |"
    }

    "例のファイルで何も書かなければ節を出さない" {
        val page = rolePageOf {
            "Repository".role {
                example("UserRepository")
                layout { "data" / "*Repository".ktFile() }
                ktFileContent { if (fileName != "UserRepository") `class`(fileName) }
            }
        }.getValue("Repository.md")

        page shouldNotContain "## Kotlin file content"
    }

    "例が無く、プロジェクトに当たるファイルも無ければ Sample.kt で表にし、そう書く" {
        val page = rolePageOf {
            "Repository".role {
                layout { "data" / "*Repository".ktFile() }
                ktFileContent { `interface`(fileName) }
            }
        }.getValue("Repository.md")

        page shouldContain "Example: none. The tables are shown for a file named `Sample.kt`; add example(...) to the role to show a real one."
        page shouldContain "| `interface Sample` | required |"
    }

    "例が無ければ、プロジェクトの最初のファイルで、その capture の値で表にする" {
        val page = rolePageOf(
            "feature/settings/SettingsViewModel.kt" to "package x\n\nclass SettingsViewModel\n",
        ) {
            "ViewModel".role {
                layout { "feature" / capture("feature") / "*ViewModel".ktFile() }
                ktFileContent { `package`("com.example.${captureValue("feature")}") }
            }
        }.getValue("ViewModel.md")

        page shouldContain "Example: `SettingsViewModel.kt`"
        page shouldContain "| `com.example.settings` |"
    }

    "例の値で利用者のブロックが落ちたら、表の代わりにそう書いて生成は続ける" {
        val page = rolePageOf {
            "Screen".role {
                example("Home")
                layout { "ui" / "*".ktFile() }
                ktFileContent { `class`(fileName.substring(10)) }
            }
        }.getValue("Screen.md")

        page shouldContain """
            ## Kotlin file content

            Example: `Home.kt`

            The rules could not be shown for this example: java.lang.StringIndexOutOfBoundsException:
        """.trimIndent()
    }

    "ワイルドカードのモジュールキーの中の ktFileContent も、節は 1 つ" {
        val page = rolePageOf(
            "feature/home/build.gradle.kts" to "",
            "feature/settings/build.gradle.kts" to "",
        ) {
            "Screen".role {
                example("HomeScreen")
                layout {
                    ":feature:*".module {
                        "build.gradle.kts".file()
                        mainSourceSet / kotlin / "*Screen".ktFile()
                        ktFileContent { `class`(fileName) }
                    }
                }
            }
        }.getValue("Screen.md")

        page.split("## Kotlin file content").size shouldBe 2
    }

    "セルの | はエスケープし、バッククオートを含むコードは 2 つで囲む" {
        val page = rolePageOf {
            "Screen".role {
                example("Home")
                layout { "ui" / "*".ktFile() }
                ktFileContent { `val`("`a|b`") }
            }
        }.getValue("Screen.md")

        page shouldContain "| `` val `a\\|b` `` | required |"
    }

    "not. の行しか無い種類の表には、残りが許されないことを最後の行に出す" {
        val page = rolePageOf {
            "Screen".role {
                example("HomeScreen")
                layout { "src" / "*Screen".ktFile() }
                ktFileContent {
                    not.`package`("com.example.legacy.**")
                    not.`import`("android.**")
                    not.`class`("*Impl")
                }
            }
        }.getValue("Screen.md")

        page shouldContain """
            | Package |
            |---|
            | `com.example.legacy.**` (not allowed) |
            | (anything else) (not allowed) |

            | Import | |
            |---|---|
            | `android.**` | not allowed |
            | (anything else) | not allowed |

            | Declaration | |
            |---|---|
            | `class *Impl` | not allowed |
            | (anything else) | not allowed |
        """.trimIndent()
    }
})
