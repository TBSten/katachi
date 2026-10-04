package me.tbsten.katachi.test.konsist.declarations

import com.lemonappdev.konsist.api.KoModifier
import com.lemonappdev.konsist.api.declaration.KoClassDeclaration
import com.lemonappdev.konsist.api.declaration.KoFunctionDeclaration
import com.lemonappdev.konsist.api.declaration.KoPropertyDeclaration
import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import me.tbsten.katachi.konsist.internal.lineOf

/**
 * What the matching of `ktFileContent { }` assumes about Konsist 0.17.3, measured: each case goes
 * red when Konsist reads a file differently, before the matching quietly does.
 */
class KtFileContentKonsistAssumptionsSpec : FreeSpec({
    "fun interface の modifiers に FUN が出る" {
        parsed("package com.example\n\nfun interface Listener {\n    fun on()\n}\n") { file ->
            file.interfaces(includeNested = false).single().modifiers shouldContain KoModifier.FUN
        }
    }

    "主コンストラクタの private val が properties に出て、修飾子と型が読める" {
        parsed("package com.example\n\nclass Holder(private val id: Int, name: String)\n") { file ->
            val property = file.classes(includeNested = false, includeLocal = false).single()
                .properties(includeNested = false).single()
            property.name shouldBe "id"
            property.modifiers shouldBe listOf(KoModifier.PRIVATE)
            property.type?.text shouldBe "Int"
        }
    }

    "名前の無い companion object の名前は Companion で、modifiers に COMPANION" {
        parsed("package com.example\n\nclass Holder {\n    companion object {\n        const val A = 1\n    }\n}\n") { file ->
            val companion = file.classes(includeNested = false, includeLocal = false).single().objects(includeNested = false).single()
            companion.name shouldBe "Companion"
            companion.modifiers shouldContain KoModifier.COMPANION
        }
    }

    "star import の name は * を含まず、isWildcard が true" {
        parsed("package com.example\n\nimport kotlinx.coroutines.*\nimport a.b.C as D\n\nclass X\n") { file ->
            val (star, aliased) = file.imports
            star.name shouldBe "kotlinx.coroutines"
            star.isWildcard shouldBe true
            aliased.name shouldBe "a.b.C"
            aliased.alias?.name shouldBe "D"
        }
    }

    "package 宣言の無いファイルの packagee は null" {
        parsed("class X\n") { file -> file.packagee.shouldBeNull() }
    }

    "推論の型・式本体の戻り値・ブロック本体で書かない戻り値は null" {
        parsed(
            "package com.example\n\nvar x = false\nfun a() = 1\nfun b() {\n}\n",
        ) { file ->
            file.properties(includeNested = false).single().type.shouldBeNull()
            val (a, b) = file.functions(includeNested = false, includeLocal = false)
            a.returnType.shouldBeNull()
            a.hasExpressionBody shouldBe true
            b.returnType.shouldBeNull()
            b.hasBlockBody shouldBe true
        }
    }

    "無名 object の中の関数は、クラスの functions に出ない" {
        parsed(
            "package com.example\n\nclass X {\n    val listener = object : Runnable {\n        override fun run() {}\n    }\n    fun own() {}\n}\n",
        ) { file ->
            val owner = file.classes(includeNested = false, includeLocal = false).single()
            owner.functions(includeNested = false, includeLocal = false).map { it.name } shouldBe listOf("own")
        }
    }

    "declarations(false, false) はクラスの主コンストラクタのプロパティを先に、本体を書いた順に返し、enum の項目と init は種類で落とせる" {
        parsed(
            "package com.example\n\nenum class Mode(val label: String) {\n    A(\"a\"), B(\"b\");\n    init {}\n    fun next() = A\n    val upper get() = label\n}\n",
        ) { file ->
            val mode = file.classes(includeNested = false, includeLocal = false).single()
            val kept = mode.declarations(includeNested = false, includeLocal = false)
                .filter { it is KoPropertyDeclaration || it is KoFunctionDeclaration || it is KoClassDeclaration }
                .map { (it as com.lemonappdev.konsist.api.provider.KoNameProvider).name }
            kept shouldBe listOf("label", "next", "upper")
        }
    }

    "アノテーションの付いた宣言の location は、アノテーションの行を指す" {
        parsed("package com.example\n\n@Suppress(\"X\")\nclass Annotated\n") { file ->
            val annotated = file.classes(includeNested = false, includeLocal = false).single()
            lineOf(annotated, annotated.path) shouldBe 3
            lineOf(annotated.annotations.single(), annotated.path) shouldBe 3
        }
    }

    "KDoc の付いた宣言の location は KDoc の先頭を指し、text は KDoc から始まる" {
        parsed("package com.example\n\n/**\n * Doc.\n */\nclass Documented\n") { file ->
            val documented = file.classes(includeNested = false, includeLocal = false).single()
            lineOf(documented, documented.path) shouldBe 3
            documented.text.startsWith("/**") shouldBe true
        }
    }

    "アノテーションの name は単純名、text は書いたとおり" {
        parsed("package com.example\n\n@androidx.compose.runtime.Composable\n@get:JvmName(\"x\")\nval y = 1\n") { file ->
            val annotations = file.properties(includeNested = false).single().annotations
            annotations.map { it.name } shouldBe listOf("Composable", "JvmName")
            annotations.map { it.text } shouldBe listOf("@androidx.compose.runtime.Composable", "@get:JvmName(\"x\")")
        }
    }

    "拡張プロパティの type は、書いた型ではなく受け手を返す（そのため型はテキストから読む）" {
        parsed("package com.example\n\nval Int.dp: Float get() = 1f\n") { file ->
            val property = file.properties(includeNested = false).single()
            property.isExtension shouldBe true
            property.type?.text shouldBe "Int"
        }
    }

    "context(...) の前のアノテーションは annotations に出ず、text は context の後ろから始まる（そのためテキストから読む）" {
        parsed("package com.example\n\n@Keep\ncontext(logger: Logger)\nfun f() {}\n") { file ->
            val function = file.functions(includeNested = false, includeLocal = false).single()
            function.annotations shouldBe emptyList()
            function.text shouldBe "fun f() {}"
            lineOf(function, function.path) shouldBe 5
        }
    }

    "BOM で始まるファイルの packagee は null、imports は空（そのため BOM を外して読み直す）" {
        parsed("\uFEFFpackage com.example\n\nimport a.B\n\nclass A\n") { file ->
            file.packagee.shouldBeNull()
            file.imports shouldBe emptyList()
        }
    }
})
