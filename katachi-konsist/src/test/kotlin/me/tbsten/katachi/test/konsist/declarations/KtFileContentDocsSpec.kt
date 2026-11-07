package me.tbsten.katachi.test.konsist.declarations

import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import me.tbsten.katachi.dsl.gradle.kotlin
import me.tbsten.katachi.dsl.gradle.mainSourceSet
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
                    required(annotated("HiltViewModel"), public, `class`, fileName).superclass("ViewModel") {
                            required(public, `val`, "uiState", "StateFlow<${fileName.replace("ViewModel", "UiState")}>")
                            allow(public, `fun`, "on*")().returns("Unit")
                            allow(private, anythingElse)
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

            ### Members of `@HiltViewModel public class HomeViewModel`

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
                    required(`interface`, fileName) {
                        required(`class`, "Params") { }
                        required(operator, `fun`, "invoke")
                    }
                    not.allow(`class`, "*Impl").except("DefaultImpl")
                    allow(internal or private, anythingElse)
                    allow(anythingElse)
                }
            }
        }.getValue("UseCase.md")

        page shouldContain """
            | Package |
            |---|
            | (the file's directory) |
            | `com.example.legacy.**` (not allowed) |

            No import line is written, so no import is allowed.

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

    "書いていない種類は表の代わりに、何も許さないと 1 行で書く" {
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
        page shouldContain "No package line is written, so no package may be declared."
        page shouldContain "No declaration line is written, so nothing may be declared in the file."
    }

    "{ } の無いクラスの行は、メンバを何も許さないと書く。関数の行には書かない" {
        val page = rolePageOf {
            "Repository".role {
                example("UserRepository")
                layout { "data" / "*Repository".ktFile() }
                ktFileContent {
                    required(`interface`, fileName)
                    allow(private, `fun`, "helper")
                }
            }
        }.getValue("Repository.md")

        page shouldContain "### Members of `interface UserRepository`\n\nNo members are allowed."
        page shouldNotContain "Members of `fun"
    }

    "メンバの見出しは、行の修飾子と種類を書いたとおりに出す" {
        val page = rolePageOf {
            "Ui".role {
                example("AppButton")
                layout { "ui" / "*".ktFile() }
                ktFileContent {
                    allow(public, enum, `class`, "AppButton*") { allow(anythingElse) }
                    allow(data or enum or value, `class`, "Health")
                }
            }
        }.getValue("Ui.md")

        page shouldContain "### Members of `public enum class AppButton*`"
        page shouldContain "### Members of `(data or enum or value) class Health`"
    }

    "例のファイルで何も書かなくても節を出し、どの種類も何も許さないと書く" {
        val page = rolePageOf {
            "Repository".role {
                example("UserRepository")
                layout { "data" / "*Repository".ktFile() }
                ktFileContent { if (fileName != "UserRepository") required(`class`, fileName) }
            }
        }.getValue("Repository.md")

        page shouldContain """
            ## Kotlin file content

            Example: `UserRepository.kt`

            No package line is written, so no package may be declared.

            No import line is written, so no import is allowed.

            No declaration line is written, so nothing may be declared in the file.
        """.trimIndent()
    }

    "例が無く、プロジェクトに当たるファイルも無ければ Sample.kt で表にし、そう書く" {
        val page = rolePageOf {
            "Repository".role {
                layout { "data" / "*Repository".ktFile() }
                ktFileContent { required(`interface`, fileName) }
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
                ktFileContent { allow(`class`, fileName.substring(10)) }
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
                    "feature/*" {
                        "build.gradle.kts".file()
                        mainSourceSet / kotlin / "*Screen".ktFile()
                        ktFileContent { required(`class`, fileName) }
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
                ktFileContent { required(`val`, "`a|b`") }
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
                    not.allow(`class`, "*Impl")
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

    "enum の項目・init・ファイル注釈・actualsIn も表に出す" {
        val page = rolePageOf {
            "Platform".role {
                example("Platform")
                layout { "src" / "*".ktFile() }
                ktFileContent {
                    `package`("com.example")
                    fileAnnotation("Suppress(*)")
                    not.fileAnnotation("JvmName")
                    actualsIn("androidMain", "iosMain")
                    allow(enum, `class`, "Color") { allow(entry, "*") }
                    allow(`class`, "Session") {
                        allow(init)
                        allow(private, constructor)("token: String")
                    }
                }
            }
        }.getValue("Platform.md")

        page shouldContain """
            | File annotation | |
            |---|---|
            | `@file:Suppress(*)` | optional |
            | `@file:JvmName` | not allowed |

            Each `expect` declaration needs an `actual` one in: `androidMain`, `iosMain`
        """.trimIndent()
        page shouldContain """
            ### Members of `enum class Color`

            | Member | |
            |---|---|
            | `entry *` | optional |
        """.trimIndent()
        page shouldContain """
            | Member | |
            |---|---|
            | `init` | optional |
            | `private constructor(token: String)` | optional |
        """.trimIndent()
    }
})
