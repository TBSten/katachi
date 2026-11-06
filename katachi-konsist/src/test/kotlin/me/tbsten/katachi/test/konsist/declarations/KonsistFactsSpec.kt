package me.tbsten.katachi.test.konsist.declarations

import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.collections.shouldContainAll
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe

/**
 * What the matching reads off a Kotlin file through Konsist (`KonsistFacts.kt`): written back as
 * the report writes a declaration, so that a case states everything read at once.
 */
class KonsistFactsSpec : FreeSpec({
    "種類と修飾子" {
        renderedFactsOf(
            """
            package com.example

            data class A(val id: Int)
            sealed interface B
            enum class C { X, Y }
            value class D(val raw: String)
            annotation class E
            fun interface F { fun on() }
            object G
            data object H
            typealias I = String
            private const val J = 1
            internal lateinit var k: String
            suspend inline fun l() {}
            """.trimIndent(),
        ) shouldBe listOf(
            "data class A",
            "  val id: Int",
            "sealed interface B",
            "enum class C",
            "  entry X",
            "  entry Y",
            "value class D",
            "  val raw: String",
            "annotation class E",
            "fun interface F",
            "  fun on()",
            "object G",
            "data object H",
            "typealias I = String",
            "private const val J",
            "internal lateinit var k: String",
            "suspend inline fun l()",
        )
    }

    "親の型" {
        renderedFactsOf(
            """
            package com.example

            /** A class X : NotThis, in a comment. */
            @Suppress("a: b")
            class A : androidx.lifecycle.ViewModel()
            class B(val id: Int) : Handler by Noop, Base<Int>(1), Other
            class C<T : Comparable<T>>(val t: T) : Repository<User> where T : Any
            interface D : (String) -> Unit
            object E : Runnable { override fun run() {} }
            class F {
                companion object : Factory<F>
            }
            """.trimIndent(),
        ) shouldBe listOf(
            "@Suppress class A : androidx.lifecycle.ViewModel",
            "class B : Handler, Base<Int>, Other",
            "  val id: Int",
            "class C : Repository<User>",
            "  val t: T",
            "interface D : (String) -> Unit",
            "object E : Runnable",
            "  override fun run()",
            "class F",
            "  companion object Companion : Factory<F>",
        )
    }

    "親の型は名前だけのものと型引数ごとのものを持ち、パッケージを外す" {
        val supertypes = factsOf("package com.example\n\nclass A : com.example.Repository<com.example.User>\n")
            .declarations.single().supertypes.single()
        supertypes.written shouldBe "com.example.Repository<com.example.User>"
        supertypes.withoutArguments shouldBe "Repository"
        supertypes.canonical shouldBe "Repository<User>"
    }

    "プロパティの型・引数・戻り値" {
        renderedFactsOf(
            """
            package com.example

            abstract class A {
                val a: kotlinx.coroutines.flow.StateFlow< Int >
                var b = false
                fun c(x: String, vararg y: Int): Unit {}
                fun d() = 1
                fun e() {}
                abstract fun f()
                fun String.g(): Int = 1
            }
            """.trimIndent(),
        ) shouldBe listOf(
            "abstract class A",
            "  val a: kotlinx.coroutines.flow.StateFlow< Int >",
            "  var b",
            "  fun c(x: String, vararg y: Int): Unit",
            "  fun d()",
            "  fun e()",
            "  abstract fun f()",
            "  fun String.g(): Int",
        )
    }

    "戻り値はブロック本体と本体なしで Unit を補い、式本体では補わない" {
        val functions = factsOf(
            "package com.example\n\ninterface A {\n    fun a()\n    fun b() {}\n    fun c() = 1\n}\n",
        ).declarations.single().members.orEmpty()
        functions.map { it.returnType?.written } shouldBe listOf("Unit", "Unit", null)
        functions.map { it.returnTypeWritten } shouldBe listOf(false, false, false)
    }

    "メンバ: 入れ子の enum・companion・主コンストラクタ・enum の項目・init。無名 object の中は出ない" {
        renderedFactsOf(
            """
            package com.example

            class A(private val id: Int) {
                init { println() }
                enum class Mode { X, Y; fun next() = X }
                companion object Named
                val listener = object : Runnable { override fun run() {} }
            }
            """.trimIndent(),
        ) shouldBe listOf(
            "class A",
            "  private val id: Int",
            "  init",
            "  enum class Mode",
            "    entry X",
            "    entry Y",
            "    fun next()",
            "  companion object Named",
            "  val listener",
        )
    }

    "行" {
        val facts = factsOf("package com.example\n\nimport a.B\n\nclass A {\n    fun f() {}\n}\n")
        facts.packageLine shouldBe 1
        facts.imports.single().line shouldBe 3
        facts.declarations.single().line shouldBe 5
        facts.declarations.single().members.orEmpty().single().line shouldBe 6
    }

    "KDoc の付いた宣言の行は、KDoc ではなく宣言（アノテーションがあればその行）" {
        val facts = factsOf("package com.example\n\n/**\n * Doc.\n */\n@Suppress(\"X\")\nclass A {\n    /** Member. */\n    fun f() {}\n}\n")
        facts.declarations.single().line shouldBe 6
        facts.declarations.single().members.orEmpty().single().line shouldBe 9
    }

    "import は書いたパス、star import は .*、alias は見ない" {
        factsOf("package com.example\n\nimport a.b.*\nimport c.D as E\n\nclass X\n").imports.map { it.path } shouldBe
            listOf("a.b.*", "c.D")
    }

    "package の無いファイル" {
        val facts = factsOf("class X\n")
        facts.packageName.shouldBeNull()
        facts.packageLine.shouldBeNull()
    }

    "アノテーションの完全修飾名の候補" - {
        "単純名は、明示の import か alias があればそれだけ、無ければ star import・同じ package・既定の package から" {
            val annotation = factsOf(
                """
                package com.example

                import androidx.compose.runtime.Composable
                import other.Real as Preview
                import lib.*

                @Composable @Preview @JvmInline @Marker
                class X
                """.trimIndent(),
            ).declarations.single().annotations

            annotation.map { it.simpleName } shouldBe listOf("Composable", "Preview", "JvmInline", "Marker")
            annotation[0].qualifiedCandidates shouldBe setOf("androidx.compose.runtime.Composable")
            annotation[1].qualifiedCandidates shouldBe setOf("other.Real")
            annotation[2].qualifiedCandidates shouldContainAll setOf("kotlin.jvm.JvmInline", "lib.JvmInline", "com.example.JvmInline")
            annotation[3].qualifiedCandidates shouldContainAll setOf("com.example.Marker", "lib.Marker")
        }

        "明示の import が同じ package の名前を隠す" {
            judged("package com.example\n\nimport other.Marker\n\n@Marker\nclass Sample\n") {
                `package`("**")
                `import`("**")
                required(annotated("com.example.Marker"), `class`, fileName)
            }.size shouldBe 1
            judged("package com.example\n\nimport other.Marker\n\n@Marker\nclass Sample\n") {
                `package`("**")
                `import`("**")
                required(annotated("other.Marker"), `class`, fileName)
            } shouldBe emptyList()
        }

        "完全修飾名で書いたものは、その名前だけ。use-site target は外す" {
            val annotations = factsOf("package com.example\n\n@a.b.Marker\n@get:JvmName(\"x\")\nval y = 1\n").declarations.single().annotations
            annotations[0].qualifiedCandidates shouldBe setOf("a.b.Marker")
            annotations[1].simpleName shouldBe "JvmName"
        }
    }

    "名前は、識別子として読めなければバッククオートで囲んで出す" {
        renderedFactsOf("package com.example\n\nfun `load the data`() {}\n") shouldBe listOf("fun `load the data`()")
    }
})
