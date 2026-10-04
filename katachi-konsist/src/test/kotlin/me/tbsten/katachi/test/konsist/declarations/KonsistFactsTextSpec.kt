package me.tbsten.katachi.test.konsist.declarations

import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.shouldBe

/**
 * What the matching reads off the text where Konsist's parse is not enough (`KotlinCodeText.kt`,
 * `SupertypeText.kt`, `DeclarationText.kt`): the supertypes around comments, string literals and
 * comparisons, the type of an extension property, the annotations before a `context(...)`, and a
 * file that starts with a byte order mark.
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
        judged("package com.example\n\nfun String.fake() {}\n") { `fun`("fake") } shouldBe emptyList()
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
                "@Keep fun f(Int)",
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
    }

    "BOM で始まるファイルの package と import を読む" {
        val facts = factsOf("﻿package com.example\n\nimport a.B\n\nclass A\n")
        facts.packageName shouldBe "com.example"
        facts.packageLine shouldBe 1
        facts.imports.map { it.path to it.line } shouldBe listOf("a.B" to 3)
        facts.declarations.single().line shouldBe 5
    }
})
