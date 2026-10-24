package me.tbsten.katachi.test.konsist.declarations

import io.kotest.assertions.withClue
import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.shouldBe
import me.tbsten.katachi.konsist.declarations.internal.supertypesOf

/**
 * What the matching reads off the text where Konsist's parse is not enough (`KotlinCodeText.kt`,
 * `SupertypeText.kt`, `DeclarationText.kt`): the supertypes around comments, string literals and
 * comparisons and whatever the body holds, the type of an extension property, the annotations before
 * a `context(...)`, and a file that starts with a byte order mark.
 */
class KonsistFactsTextSpec : FreeSpec({
    "親の型の一覧の中のコメントは、親の型に入らない" {
        renderedFactsOf(
            """
            package com.example

            class A :
                Base(), // the base
                Other
            class B : /* the base */ Base(), Other
            class C : Base(), /* nested /* comment */ still */ Other
            """.trimIndent(),
        ) shouldBe listOf(
            "class A : Base, Other",
            "class B : Base, Other",
            "class C : Base, Other",
        )
    }

    "ヘッダの中の比較演算子・文字列・文字・ラムダの括弧で、親の型を読み誤らない" {
        renderedFactsOf(
            """
            package com.example

            class A(val ok: Boolean = 1 < 2) : Base()
            class B(val s: String = "<") : Base()
            class C : Base(x >= 0), Other
            class D : Exception(":)"), Other
            class E(p0: Int = { it > 0 }, p1: List<String>)
            class F(val c: Char = '(') : Base()
            class G(val s: String = ${"\"\"\""}a: (b${"\"\"\""}) : Base()
            """.trimIndent(),
        ) shouldBe listOf(
            "class A : Base",
            "  val ok: Boolean",
            "class B : Base",
            "  val s: String",
            "class C : Base, Other",
            "class D : Exception, Other",
            "class E",
            "class F : Base",
            "  val c: Char",
            "class G : Base",
            "  val s: String",
        )
    }

    "where は行頭やタブの後に書いても、親の型に入らない" {
        renderedFactsOf("package com.example\n\nclass A<T> : Bar\nwhere T : Any\nclass B<T> : Bar\twhere T : Any\n") shouldBe listOf(
            "class A : Bar",
            "class B : Bar",
        )
    }

    "親の型は、ヘッダに書いたものだけで、本体に何が書いてあっても変わらない" {
        for ((header, supertypes) in HEADERS) {
            withClue(header) { supertypesOf(header, "A").map { it.written } shouldBe supertypes }
            for (body in BODIES) {
                val text = "$header\n$body"
                withClue(text) { supertypesOf(text, "A").map { it.written } shouldBe supertypes }
            }
        }
    }

    "本体の中のクラスは、外側の文字列や { を気にせず、自分のヘッダから親の型を読む" {
        renderedFactsOf(
            """
            package com.example

            class Outer(val label: String = "{") : Base() {
                val text = "class Outer : Wrong {"
                // class Inner : Wrong {
                class Inner(val f: () -> Unit = { }) : Other() {
                    val s = "}"
                }
                object Single : Marker
            }

            class Last : FreeSpec({
                "a" { }
            })
            """.trimIndent(),
        ) shouldBe listOf(
            "class Outer : Base",
            "  val label: String",
            "  val text",
            "  class Inner : Other",
            "    val f: () -> Unit",
            "    val s",
            "  object Single : Marker",
            "class Last : FreeSpec",
        )
    }

    "型引数と関数型の引数の末尾のカンマは、正規化した型に残らない" {
        val facts = factsOf("package com.example\n\nval m: Map<String, Int,> = TODO()\nval f: (\n    String,\n    Int,\n) -> Unit = TODO()\n")
        facts.declarations.map { it.propertyType?.canonical } shouldBe listOf("Map<String,Int>", "(String,Int)->Unit")
    }

    "拡張プロパティの型は、受け手ではなく書いた型" {
        renderedFactsOf(
            """
            package com.example

            val String.size2: Int get() = length
            val <T> List<T>.second: T get() = this[1]
            val String.inferred get() = 1
            val Context.dataStoreName: String by lazy { "x" }
            var Foo.Bar.Bar: Map<String, Int>
                get() = TODO()
                set(value) {}
            val String?.orEmpty2: String get() = this ?: ""
            val ((Int) -> Unit).name2: String get() = ""
            """.trimIndent(),
        ) shouldBe listOf(
            "val String.size2: Int",
            "val List<T>.second: T",
            "val String.inferred",
            "val Context.dataStoreName: String",
            "var Foo.Bar.Bar: Map<String, Int>",
            "val String?.orEmpty2: String",
            "val ((Int) -> Unit).name2: String",
        )
    }

    "拡張の受け手は、名前の前に書いたとおりに出す（名前の照合には使わない）" {
        renderedFactsOf(
            "package com.example\n\ninternal fun LicensesScreenUiState.Companion.fake(): LicensesScreenUiState = TODO()\n",
        ) shouldBe listOf("internal fun LicensesScreenUiState.Companion.fake(): LicensesScreenUiState")
        judged("package com.example\n\nfun String.fake() {}\n") { allow(`fun`, "fake") } shouldBe emptyList()
    }

    "context(...) の前のアノテーションを読み、行はアノテーションの行" - {
        "行を分けて書いたもの" {
            val facts = factsOf(
                "package com.example\n\nimport x.Keep\n\n/** Doc. */\n@Keep\n@Deprecated(\"a) b\")\ncontext(logger: Logger)\nprivate suspend fun load() {}\n",
            )
            val load = facts.declarations.single()
            renderedFactsOf(
                "package com.example\n\nimport x.Keep\n\n/** Doc. */\n@Keep\n@Deprecated(\"a) b\")\ncontext(logger: Logger)\nprivate suspend fun load() {}\n",
            ) shouldBe listOf("@Keep @Deprecated private suspend fun load()")
            load.line shouldBe 6
            load.annotations[0].qualifiedCandidates.contains("x.Keep") shouldBe true
        }

        "1 行に書いたもの・古い context receivers・クラスのメンバ" {
            val source = "package com.example\n\n@Keep context(Logger) fun f(x: Int) {}\n" +
                "class A {\n    @[Keep Other]\n    context(scope: Scope)\n    fun g() {}\n}\n"
            renderedFactsOf(source) shouldBe listOf(
                "@Keep fun f(x: Int)",
                "class A",
                "  @Keep @Other fun g()",
            )
            val facts = factsOf(source)
            facts.declarations.map { it.line } shouldBe listOf(3, 4)
            facts.declarations[1].members.orEmpty().single().line shouldBe 5
        }

        "アノテーションの無い context(...) は、context の行" {
            factsOf("package com.example\n\nfun a() = run(1)\ncontext(logger: Logger)\nfun f() {}\n").declarations.map { it.line } shouldBe
                listOf(3, 4)
        }

        "context と書いてあるだけで context(...) の無いファイルは、行もアノテーションも書いたまま" {
            val facts = factsOf(
                "package com.example\n\nval note = \"context\"\n@Keep\nval x = listOf(1)\nfun f() {}\n",
            )
            facts.declarations.map { it.line } shouldBe listOf(3, 4, 6)
            renderedFactsOf(
                "package com.example\n\nval note = \"context\"\n@Keep\nval x = listOf(1)\nfun f() {}\n",
            ) shouldBe listOf("val note", "@Keep val x", "fun f()")
        }
    }

    "BOM で始まるファイルの package と import を読む" {
        val facts = factsOf("﻿package com.example\n\nimport a.B\n\nclass A\n")
        facts.packageName shouldBe "com.example"
        facts.packageLine shouldBe 1
        facts.imports.map { it.path to it.line } shouldBe listOf("a.B" to 3)
        facts.declarations.single().line shouldBe 5
    }
})

/** A class header written many ways, each with the supertypes it lists. */
private val HEADERS: List<Pair<String, List<String>>> = listOf(
    "class A : Base(), Other" to listOf("Base", "Other"),
    "class A<T : Comparable<T>>(val value: T) : Base<T>(), Other<T>" to listOf("Base<T>", "Other<T>"),
    "interface A : Base<String>, Other" to listOf("Base<String>", "Other"),
    "class A<T> : Base<T>()\n    where T : Any" to listOf("Base<T>"),
    "class A(val x: Int = 1)" to emptyList(),
    "class A(b: Base) : Base by b" to listOf("Base"),
    "class A :\n    Base(), // the base\n    Other" to listOf("Base", "Other"),
    // A `{` that is not the body's: in a string, a character, a comment, a default value or an argument.
    "class A(val s: String = \"{\") : Base()" to listOf("Base"),
    "class A(val c: Char = '{') : Base()" to listOf("Base"),
    "class A(val f: () -> Unit = { println(\"}\") }) : Base(), Other" to listOf("Base", "Other"),
    "class A : Base({ 1 }), Other" to listOf("Base", "Other"),
    "class A /* { */ : Base() // {" to listOf("Base"),
)

/** Bodies that hold what looks like a header, a `:` or a brace in every way a body can. */
private val BODIES: List<String> = listOf(
    "{}",
    "{ val s = \"}\" }",
    "{ val s = \"{{\" }",
    "{ // class A : Wrong {\n}",
    "{ /* class A : Wrong { */ }",
    "{ val c = '{' }",
    "{ val raw = \"\"\" class A : Wrong { \"\"\" }",
    "{ val t = \"\${\"{\"}\" }",
    "{ class A : Wrong() { class A : Deeper } }",
    "{ companion object : Wrong { } }",
    "{ val x: Wrong = 1 }",
    "{ fun <T> f(x: T): Map<String, () -> Unit> = TODO() }",
    "{ val f = { x: Int -> if (x < 3) { 1 } else { 2 } } }",
    "{ // it's a comment\n }",
)
