@file:Suppress("DEPRECATION")
@file:OptIn(InternalKatachiApi::class)

package me.tbsten.katachi.test.konsist.declarations

import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldNotContain
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import me.tbsten.katachi.InternalKatachiApi
import me.tbsten.katachi.check.internal.report
import me.tbsten.katachi.dsl.DeclarationSite
import me.tbsten.katachi.dsl.gradle.div
import me.tbsten.katachi.dsl.kotlin.ktFile
import me.tbsten.katachi.konsist.declarations.KtFileContentScope
import me.tbsten.katachi.konsist.declarations.internal.failureOf
import me.tbsten.katachi.konsist.declarations.internal.judgeKtFile
import me.tbsten.katachi.konsist.declarations.ktFileContent

/** [body] as a file of package `com.example`: its first line is line 3. */
private fun kt(body: String): String = "package com.example\n\n" + body.trimIndent() + "\n"

/** The fixes [block] gives to the failures of [source], one list per failure, in the order the report lists them. */
private fun fixesOf(source: String, block: KtFileContentScope.() -> Unit): List<List<String>> {
    val rules = rulesOf(fileName = "Sample", block = block)
    return judgeKtFile(factsOf(source), rules).map { failureOf(it, SAMPLE_PATH, rules) }.map { failure -> failure.howToFix.map { it.text } }
}

/**
 * Which lines are required: the ones written with `required(...)`, whatever their name. A line
 * written with `allow(...)` only lets what it matches be, with or without `*` in its name, and an
 * import line always only allows.
 */
class KtFileContentRequiredSpec : FreeSpec({
    "宣言の行" - {
        "allow は名前に * が無くても任意" {
            dumpOf {
                allow(`class`, "Fixed")
                allow(`class`, "*Free")
                allow(`fun`, "main")
                allow(`val`, "TAG")
                allow(`typealias`, "Key")
            } shouldBe """
                optional class{} Fixed
                optional class{} *Free
                optional fun{} main
                optional val{} TAG
                optional typealias{} Key
            """.trimIndent()
        }

        "required は名前に * があってもなくても必須" {
            dumpOf {
                required(`class`, "Fixed")
                required(sealed, type, "*Action")
                required(`fun`, "on*")
                required(`val`, "uiState", "StateFlow<*>")
                required(`typealias`, "Key")
                required(`fun`, `interface`, "*Listener")
            } shouldBe """
                required class{} Fixed
                required class{sealed}|interface{sealed} *Action
                required fun{} on*
                required val{} uiState type=StateFlow<*>
                required typealias{} Key
                required interface{fun} *Listener
            """.trimIndent()
        }

        "\\* は文字の * なので、allow では任意、required では必須のまま" {
            dumpOf {
                allow(`class`, """Star\*""")
                required(`object`, """Other\*""")
            } shouldBe """
                optional class{} Star\*
                required object{} Other\*
            """.trimIndent()
        }

        "fileName や captureValue を埋めた名前も、書いた語で決まる" {
            dumpOf(fileName = "HomeViewModel", captures = mapOf("feature" to "home")) {
                required(`class`, fileName)
                allow(`object`, "${captureValue("feature")}Module")
            } shouldBe """
                required class{} HomeViewModel
                optional object{} homeModule
            """.trimIndent()
        }

        "禁止の行は必須にならない" {
            dumpOf { not.allow(`class`, "Fixed") }.startsWith("prohibited") shouldBe true
            rulesOf { not.allow(`class`, "Fixed") }.firstDeclaration.required shouldBe false
        }

        "クラスの本体の中でも required と allow を並べて書ける" {
            dumpOf {
                required(`class`, "Host") {
                    required(`val`, "uiState")
                    required(`fun`, "on*")
                    allow(`fun`, "load")
                    allow(companion, `object`, "Companion")
                }
            } shouldBe """
                required class{} Host
                  members:
                    required val{} uiState
                    required fun{} on*
                    optional fun{} load
                    optional object{companion} Companion
            """.trimIndent()
        }
    }

    "判定" - {
        "* の無い required は、その名前の宣言が無ければ足りない" {
            judged(kt("class Sample")) {
                `package`("**")
                required(`class`, fileName)
            }.shouldBeEmpty()
            judged(kt("class Other")) {
                `package`("**")
                required(`class`, fileName); allow(`class`, "*")
            } shouldBe
                listOf("Sample | Missing: class Sample")
        }

        "* のある required は、当たる宣言が 1 つでも複数でもよく、0 件なら足りない" {
            val block: me.tbsten.katachi.konsist.declarations.KtFileContentScope.() -> Unit = {
                `package`("**")
                required(sealed, `interface`, "*Action")
                allow(`class`, "*")
            }
            judged(kt("sealed interface HomeAction"), block = block).shouldBeEmpty()
            judged(kt("sealed interface HomeAction\nsealed interface ListAction"), block = block).shouldBeEmpty()
            judged(kt("class Sample"), block = block) shouldBe listOf("*Action | Missing: sealed interface *Action")
        }

        "{A,B} の required は、どれか 1 つがあれば満たし、どれも無ければ足りない" {
            val block: KtFileContentScope.() -> Unit = {
                `package`("**")
                required(`class`, "{Alpha,Beta}")
            }
            judged(kt("class Alpha"), block = block).shouldBeEmpty()
            judged(kt("class Alpha\nclass Beta"), block = block).shouldBeEmpty()
            judged(kt("class Extra"), block = block) shouldBe listOf(
                "Extra (line 3) | Not allowed: class Extra",
                "{Alpha,Beta} | Missing: class {Alpha,Beta}",
            )
        }

        "* のある required に名前だけ当たる宣言は、形違いにまとめず余計と足りないの 2 件" {
            judged(kt("interface HomeAction")) {
                `package`("**")
                required(sealed, `interface`, "*Action")
            } shouldBe listOf(
                "HomeAction (line 3) | Not allowed: interface HomeAction",
                "*Action | Missing: sealed interface *Action",
            )
        }

        "名前がパターンの required の行には、その名前への改名を勧めない" {
            fixesOf(kt("interface HomeAction")) {
                `package`("**")
                required(sealed, `interface`, "*Action")
            }.first() shouldNotContain
                "Rename it to *Action"
            val braces = fixesOf(kt("class Extra")) {
                `package`("**")
                required(`class`, "{Alpha,Beta}")
            }.first()
            braces shouldNotContain "Rename it to {Alpha,Beta}"
            braces shouldContain "Move it to a file of another role"
            // A name that is not a pattern is still offered.
            fixesOf(kt("class Extra")) {
                `package`("**")
                required(`class`, fileName)
            }.first() shouldContain "Rename it to Sample"
        }

        "名前がパターンの required の行が足りないとき、別の形で許された宣言を Found に出さない" {
            val block: KtFileContentScope.() -> Unit = {
                `package`("**")
                required(sealed, type, "*Action")
                allow(data, `class`, "*")
            }
            judged(kt("data class HomeAction"), block = block) shouldBe
                listOf("*Action | Missing: sealed (class or interface) *Action")
            fixesOf(kt("data class HomeAction"), block).single().none { it.startsWith("Change the declaration") } shouldBe true
        }

        "* の無い allow は、その宣言が無くても違反にならない" {
            judged(kt("class Sample")) {
                `package`("**")
                required(`class`, fileName)
                allow(`class`, "Default$fileName")
                allow(`object`, "Companion")
            }.shouldBeEmpty()
        }

        "required と allow が同じ種類に並ぶと、どちらも allow list の 1 行で、ほかの宣言は余計" {
            val block: me.tbsten.katachi.konsist.declarations.KtFileContentScope.() -> Unit = {
                `package`("**")
                required(`class`, fileName)
                allow(`class`, "*Helper")
            }
            judged(kt("class Sample\nclass FormatHelper"), block = block).shouldBeEmpty()
            judged(kt("class Sample\nclass Extra"), block = block) shouldBe listOf("Extra (line 4) | Not allowed: class Extra")
        }

        "required だけを書いた宣言の一覧も、allow list は required の行だけ" {
            judged(kt("class Sample\nclass Extra\nfun free() {}")) {
                `package`("**")
                required(`class`, fileName)
            } shouldBe
                listOf("Extra (line 4) | Not allowed: class Extra", "free (line 5) | Not allowed: fun free()")
        }

        "クラスの本体の中の required も、無ければ足りない" {
            judged(kt("class Sample {\n    fun load() {}\n}")) {
                `package`("**")
                required(`class`, fileName) {
                    required(`val`, "uiState")
                    required(`fun`, "on*")
                    allow(`fun`, "load")
                }
            } shouldBe listOf("Sample.uiState | Missing: val uiState in class Sample", "Sample.on* | Missing: fun on*(...) in class Sample")
        }

        "名前だけ当たる宣言は required でも 1 件の形違いにまとめる" {
            judged(kt("class Sample")) {
                `package`("**")
                required(data, `class`, fileName)
            } shouldBe listOf(
                "Sample (line 3) | Not allowed: class Sample | Expected: data class Sample | Differs in: modifiers (missing: data)",
            )
        }
    }

    "import の行" - {
        "* の有無にかかわらず許すだけで、禁止の行は禁止" {
            dumpOf {
                `import`("com.example.App")
                `import`("androidx.compose.**")
                not.`import`("android.content.Context")
            } shouldBe """
                import com.example.App optional
                import androidx.compose.** optional
                import android.content.Context prohibited
            """.trimIndent()
        }

        "* の無い import の行に当たる import が無くても違反にならない" {
            judged(kt("class Sample")) {
                `package`("**")
                `import`("com.example.App")
                required(`class`, fileName)
            }.shouldBeEmpty()
        }
    }

    "出力の文字" - {
        "足りない宣言の直し方は、required の行を allow にする案内" {
            var line: DeclarationSite? = null
            val violations = ktFileContentRun(
                "src/Home.kt" to "package com.example\n\nobject HomePreview\n",
                paths = { "src" / "*".ktFile() },
            ) {
                ktFileContent {
                    `package`("**")
                    line = DeclarationSite.capture(); required(`class`, fileName)
                    allow(`object`, "*Preview")
                }
            }
            val report = violations.report()
            report shouldContain "Missing: class Home"
            report shouldContain "- If some files do not need it, write allow(...) in place of required(...) at $line"
        }

        "余計な宣言を許す直し方は allow(...) だけで、.optional() を付けない" {
            val violations = ktFileContentRun(
                "src/Home.kt" to "package com.example\n\nclass Home\nclass Extra\n",
                paths = { "src" / "*".ktFile() },
            ) {
                ktFileContent {
                    `package`("**")
                    required(`class`, fileName)
                }
            }
            val report = violations.report()
            report.trimEnd().endsWith("allow(`class`, \"Extra\")") shouldBe true
            report.contains(".optional()") shouldBe false
        }

        "役割ページでは required の行が required、allow の行が optional" {
            val page = rolePageOf {
                "Screen".role {
                    example("HomeScreen")
                    layout { "src" / "*Screen".ktFile() }
                    ktFileContent {
                        `package`("**")
                        `import`("com.example.App")
                        required(`fun`, fileName)
                        allow(`fun`, "${fileName}Preview")
                    }
                }
            }.getValue("Screen.md")
            val rows = page.lines()
            rows shouldContain "| `com.example.App` | optional |"
            rows shouldContain "| `fun HomeScreen(...)` | required |"
            rows shouldContain "| `fun HomeScreenPreview(...)` | optional |"
            rows shouldNotContain "| `com.example.App` | required |"
        }
    }
})
