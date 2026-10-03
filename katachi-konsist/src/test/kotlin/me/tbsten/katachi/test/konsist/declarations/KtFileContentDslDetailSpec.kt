package me.tbsten.katachi.test.konsist.declarations

import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.shouldBe
import me.tbsten.katachi.konsist.declarations.KtFileContentScope

/*
 * The rest of the ways of writing a line compile, and build the model they mean: names filled in
 * per file, what is chained after a kind, required and optional, prohibited lines and
 * `anythingElse()`, members. Compared through `dump()`, as `KtFileContentDslSpec` does.
 */
class KtFileContentDslDetailSpec : FreeSpec({
    "名前 — fileName と captureValue" - {
        val block: KtFileContentScope.() -> Unit = {
            `package`("com.example.feature.${captureValue("feature")}")
            `class`(fileName) {
                `val`("uiState", "StateFlow<${fileName.replace("ViewModel", "UiState")}>")
            }
        }

        "ファイルごとに評価するので、ファイルの本当の値が入る" {
            dumpOf(fileName = "HomeViewModel", captures = mapOf("feature" to "home"), block = block) shouldBe """
                package com.example.feature.home
                required class{} HomeViewModel
                  members:
                    required val{} uiState type=StateFlow<HomeUiState>
            """.trimIndent()

            dumpOf(fileName = "SettingsViewModel", captures = mapOf("feature" to "settings"), block = block) shouldBe """
                package com.example.feature.settings
                required class{} SettingsViewModel
                  members:
                    required val{} uiState type=StateFlow<SettingsUiState>
            """.trimIndent()
        }

        "if でファイルによって中身を変えられる" {
            val navigator: KtFileContentScope.() -> Unit = {
                if (fileName.startsWith("Default")) {
                    `class`(fileName).superclass(fileName.removePrefix("Default"))
                } else {
                    `interface`(fileName).superclass("Navigator")
                }
            }

            dumpOf(fileName = "DefaultHomeScreenNavigator", block = navigator) shouldBe
                "required class{} DefaultHomeScreenNavigator supers=[HomeScreenNavigator]"
            dumpOf(fileName = "HomeScreenNavigator", block = navigator) shouldBe
                "required interface{} HomeScreenNavigator supers=[Navigator]"
        }
    }

    "後ろにつなぐもの" - {
        "引数・戻り値・プロパティの型" {
            val dump = dumpOf {
                `fun`("load")()
                `fun`("save")("String", "Int")
                `fun`("on*")(anyArgs())
                `fun`("free")
                public.`fun`("on*")().returns("Unit")
                `val`("state", "StateFlow<*>")
                `var`("count")
            }

            dump shouldBe """
                required fun{} load params=()
                required fun{} save params=(String, Int)
                optional fun{} on*
                required fun{} free
                optional fun{public} on* params=() returns=Unit
                required val{} state type=StateFlow<*>
                required var{} count
            """.trimIndent()
        }

        "親の型はいくつも書け、{ } は種類の後ろにも superclass の後ろにも付けられる" {
            val dump = dumpOf {
                `class`("A").superclass("B", "C").superclass("D")
                `class`("E") { `val`("x") }
                `class`("F").superclass("G") { `val`("y") }
            }

            dump shouldBe """
                required class{} A supers=[B, C, D]
                required class{} E
                  members:
                    required val{} x
                required class{} F supers=[G]
                  members:
                    required val{} y
            """.trimIndent()
        }
    }

    "必須と任意" - {
        "* を含まない import は必須、含むものは任意" {
            dumpOf {
                `import`("com.example.App")
                `import`("androidx.compose.**")
            } shouldBe """
                import com.example.App required
                import androidx.compose.** optional
            """.trimIndent()
        }
    }

    "禁止と「ほかは何でもよい」" - {
        "not と except、anythingElse" {
            val dump = dumpOf(fileName = "HomeScreen", captures = mapOf("featurePackage" to "home")) {
                `import`("**")
                not.`import`("com.example.feature.**")
                    .except("com.example.feature.${captureValue("featurePackage")}.**")

                `class`(fileName)
                private.anythingElse()
            }

            dump shouldBe """
                import ** optional
                import com.example.feature.** prohibited except [com.example.feature.home.**]
                required class{} HomeScreen
                anythingElse {private}
            """.trimIndent()
        }

        "宣言とメンバの禁止の行。修飾子のない anythingElse は何でも" {
            val dump = dumpOf {
                not.`class`("*Impl").except("DefaultImpl", "LegacyImpl")
                `class`("Holder") {
                    not.public.`var`("*")
                    not.annotated("Deprecated").`fun`("*")
                    (internal or private).anythingElse()
                }
                anythingElse()
            }

            dump shouldBe """
                prohibited class{} *Impl except=[DefaultImpl, LegacyImpl]
                required class{} Holder
                  members:
                    prohibited var{public} *
                    prohibited fun{@Deprecated} *
                    anythingElse {internal}|{private}
                anythingElse {}
            """.trimIndent()
        }
    }

    "メンバ" - {
        "UseCase の interface と Default の実装" {
            val dump = dumpOf(fileName = "GetUserUseCase") {
                `interface`(fileName) {
                    operator.`fun`("invoke")
                }
                `class`("Default$fileName").superclass(fileName) {
                    override.`fun`("invoke")
                    private.anythingElse()
                }
            }

            dump shouldBe """
                required interface{} GetUserUseCase
                  members:
                    required fun{operator} invoke
                required class{} DefaultGetUserUseCase supers=[GetUserUseCase]
                  members:
                    required fun{override} invoke
                    anythingElse {private}
            """.trimIndent()
        }

        "UiState は val だけ" {
            dumpOf(fileName = "HomeUiState") {
                data.`class`(fileName) {
                    `val`("*")
                }
            } shouldBe """
                required class{data} HomeUiState
                  members:
                    optional val{} *
            """.trimIndent()
        }

        "入れ子のクラスにも { } を付けられ、クラスの中だけの修飾子が使える" {
            val dump = dumpOf {
                `class`(fileName) {
                    `class`("Mode") { `val`("*") }
                    enum.`class`("*Kind")
                    inner.`class`("Inner")
                    companion.`object`("Companion").optional()
                    protected.abstract.`fun`("render")
                    lateinit.`var`("*")
                }
            }

            dump shouldBe """
                required class{} Sample
                  members:
                    required class{} Mode
                      members:
                        optional val{} *
                    optional class{enum} *Kind
                    required class{inner} Inner
                    optional object{companion} Companion
                    required fun{protected abstract} render
                    optional var{lateinit} *
            """.trimIndent()
        }
    }

    "テンプレートと同じ骨組みの例" - {
        "capture の値を埋めた親の型" {
            dumpOf(fileName = "UserRepositoryImpl", captures = mapOf("name" to "User")) {
                `class`(fileName).superclass("${captureValue("name")}Repository") {
                    override.`fun`("*")
                }
            } shouldBe """
                required class{} UserRepositoryImpl supers=[UserRepository]
                  members:
                    optional fun{override} *
            """.trimIndent()
        }
    }
})
