package me.tbsten.katachi.test.konsist.declarations

import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import me.tbsten.katachi.konsist.declarations.DeclarationListScope
import me.tbsten.katachi.konsist.declarations.TypeKind

/*
 * Every way of writing a line that the design of `ktFileContent { }` shows compiles, and builds the
 * model it means: a whole file, which kinds are allow lists, package, import, the kinds, modifiers
 * and annotations here; the rest in `KtFileContentDslDetailSpec`. The model is compared as a
 * whole, through `dump()`, so a field the line did not mean to set is caught as well as one it did.
 */

/** A name of the user's own for a combination of kinds: one extension, usable in both scopes. */
private val DeclarationListScope.classOrInterface: TypeKind get() = `class` or `interface`

class KtFileContentDslSpec : FreeSpec({
    "要点の例（ViewModel のファイル）" - {
        "package・import・アノテーション・親の型・メンバが Kotlin のファイルと同じ並びで模型になる" {
            val dump = dumpOf(fileName = "HomeViewModel") {
                `package`("com.example.feature.**")

                `import`("kotlin.**")
                `import`("kotlinx.coroutines.**")
                `import`("androidx.**")
                `import`("com.example.domain.**")

                annotated("HiltViewModel")
                    .public.`class`(fileName).superclass("ViewModel") {
                        public.`val`("uiState", "StateFlow<${fileName.replace("ViewModel", "UiState")}>")
                        public.`fun`("on*")().returns("Unit")
                        private.anythingElse()
                    }
            }

            dump shouldBe """
                package com.example.feature.**
                import kotlin.** optional
                import kotlinx.coroutines.** optional
                import androidx.** optional
                import com.example.domain.** optional
                required class{@HiltViewModel public} HomeViewModel supers=[ViewModel]
                  members:
                    required val{public} uiState type=StateFlow<HomeUiState>
                    optional fun{public} on* params=() returns=Unit
                    anythingElse {private}
            """.trimIndent()
        }
    }

    "書いた種類だけが allow list" - {
        "宣言だけを書けば package と import は見ない" {
            val rules = rulesOf(fileName = "GetUserUseCase") { `interface`(fileName) }

            rules.dump() shouldBe "required interface{} GetUserUseCase"
            rules.packages.shouldBeEmpty()
            rules.imports.shouldBeEmpty()
        }

        "import だけを書けば宣言は見ない" {
            val rules = rulesOf {
                `import`("**")
                not.`import`("android.**")
            }

            rules.dump() shouldBe """
                import ** optional
                import android.** prohibited
            """.trimIndent()
            rules.declarations.shouldBeNull()
        }

        "{ } を付けないクラスの行はメンバを見ない。空の { } はメンバを 1 つも置かない" {
            val dump = dumpOf {
                `class`("Free")
                `class`("Empty") { }
            }

            dump shouldBe """
                required class{} Free
                required class{} Empty
                  members: (none)
            """.trimIndent()
        }

        "何も書かなければ空の模型になる" {
            rulesOf { }.isEmpty shouldBe true
        }
    }

    "package" - {
        "パスのパターンと matchingDirectory" {
            val dump = dumpOf {
                `package`("com.example.feature.**")
                `package`(matchingDirectory)
                not.`package`("com.example.legacy.**").except("com.example.legacy.compat")
            }

            dump shouldBe """
                package com.example.feature.**
                package (matching directory)
                not package com.example.legacy.** except [com.example.legacy.compat]
            """.trimIndent()
        }
    }

    "import" - {
        "許可の ** と、後ろの層の禁止" {
            val dump = dumpOf {
                `import`("**")
                not.`import`("me.tbsten.katachi.docs.**")
                not.`import`("me.tbsten.katachi.template.**")
            }

            dump shouldBe """
                import ** optional
                import me.tbsten.katachi.docs.** prohibited
                import me.tbsten.katachi.template.** prohibited
            """.trimIndent()
        }
    }

    "宣言の種類" - {
        "種類ごとの行と type" {
            val dump = dumpOf {
                `class`("A")
                `interface`("B")
                `object`("C")
                type("D")
                `fun`("e")
                `val`("f")
                `var`("g")
                `typealias`("H")
            }

            dump shouldBe """
                required class{} A
                required interface{} B
                required object{} C
                required class{}|interface{}|object{} D
                required fun{} e
                required val{} f
                required var{} g
                required typealias{} H
            """.trimIndent()
        }

        "種類は値なので or で組める" {
            val dump = dumpOf(fileName = "HomeUiState") {
                (`class` or `interface`)(fileName)
                (public.data.`class` or internal.`object`)("*State")
                (`val` or `var`)("*")
            }

            dump shouldBe """
                required class{}|interface{} HomeUiState
                optional class{public data}|object{internal} *State
                optional val{}|var{} *
            """.trimIndent()
        }

        "利用者の拡張プロパティがファイル直下とクラスの中の両方で使える" {
            val dump = dumpOf(fileName = "HomeUiState") {
                classOrInterface(fileName) {
                    classOrInterface("*")
                }
            }

            dump shouldBe """
                required class{}|interface{} HomeUiState
                  members:
                    optional class{}|interface{} *
            """.trimIndent()
        }

        "fun interface は `fun`.`interface`" {
            dumpOf { `fun`.`interface`("*Listener") } shouldBe "optional interface{fun} *Listener"
        }
    }

    "修飾子とアノテーション" - {
        "修飾子は . でつなぎ、or でどれかになる" {
            val dump = dumpOf(fileName = "HomeNavKey") {
                public.data.`object`(fileName)
                (internal or private).data.`object`("*")
                data.public.`class`("Same")
            }

            dump shouldBe """
                required object{public data} HomeNavKey
                optional object{internal data}|object{private data} *
                required class{public data} Same
            """.trimIndent()
        }

        "annotated は前につなぎ、行頭の . で次の行に続けても同じ宣言に付く" {
            val dump = dumpOf(fileName = "HomeScreen") {
                annotated("HiltViewModel")
                    .public.`class`(fileName).superclass("ViewModel")

                annotated("Composable").public.`fun`(fileName)
                annotated("Serializable").data.`object`(fileName)
                (annotated("Preview") or annotated("PreviewLightDark")).private.`fun`("*Preview")
                annotated("Serializable")(`object` or `class`)(fileName)
            }

            dump shouldBe """
                required class{@HiltViewModel public} HomeScreen supers=[ViewModel]
                required fun{@Composable public} HomeScreen
                required object{@Serializable data} HomeScreen
                optional fun{@Preview private}|fun{@PreviewLightDark private} *Preview
                required object{@Serializable}|class{@Serializable} HomeScreen
            """.trimIndent()
        }

        "annotated をいくつ書いても、すべて付いている必要がある" {
            val dump = dumpOf {
                annotated("A", "B").`class`("One")
                annotated("A").annotated("B").`class`("Two")
                annotated("androidx.compose.runtime.Composable").`fun`("Three")
            }

            dump shouldBe """
                required class{@A @B} One
                required class{@A @B} Two
                required fun{@androidx.compose.runtime.Composable} Three
            """.trimIndent()
        }

        "単独の行の annotated は次の宣言に付かない（隠れた状態を持たない）" {
            val dump = dumpOf {
                annotated("Orphan")
                `class`("A")
            }

            dump shouldBe "required class{} A"
        }

        "修飾子は種類にも、or で組んだ種類にもかけられる" {
            val dump = dumpOf {
                const(`val` or `var`)("*")
                sealed.type("*Action")
                public(data.`class`)("Wrapped")
            }

            dump shouldBe """
                optional val{const} *
                optional class{sealed}|interface{sealed} *Action
                required class{public data} Wrapped
            """.trimIndent()
        }
    }
})
