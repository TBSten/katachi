@file:OptIn(InternalKatachiApi::class)

package me.tbsten.katachi.test.konsist.declarations

import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import me.tbsten.katachi.InternalKatachiApi
import me.tbsten.katachi.dsl.internal.DeclarationVerdict
import me.tbsten.katachi.konsist.declarations.KtFileContentScope

/** [body] after `package com.example` and the [imports]: the package is line 1, the imports start at line 3. */
private fun kt(imports: List<String>, body: String = "class Sample"): String =
    "package com.example\n\n" + imports.joinToString("") { "import $it\n" } + "\n" + body + "\n"

private fun judged(imports: List<String>, block: KtFileContentScope.() -> Unit) = judgedAndExplained(kt(imports), block = block)

/**
 * The `package` line and the imports of a file as the IDE's JSON explains them: decided by the same functions as the
 * check, so what is not allowed here is exactly what the check rejects.
 */
internal class ImportPackageExplanationSpec : FreeSpec({
    "package の行" - {
        "許す行に当たると Allowed で、その行を添える" {
            val explained = judged(emptyList()) { `package`("com.**"); allow(`class`, "*") }.explanation.packageLine.shouldNotBeNull()

            explained.name shouldBe "com.example"
            explained.line shouldBe 1
            explained.verdict shouldBe DeclarationVerdict.Allowed
            explained.rules.map { it.text }.size shouldBe 1
        }

        "どの行にも当たらないと Unexpected で、行は空" {
            val explained = judged(emptyList()) { `package`("org.**"); allow(`class`, "*") }.explanation.packageLine.shouldNotBeNull()

            explained.verdict shouldBe DeclarationVerdict.Unexpected
            explained.rules shouldBe emptyList()
        }

        "禁止の行に当たると Prohibited" {
            val explained = judged(emptyList()) { `package`("**"); not.`package`("com.example"); allow(`class`, "*") }.explanation.packageLine.shouldNotBeNull()

            explained.verdict shouldBe DeclarationVerdict.Prohibited
            explained.rules.size shouldBe 1
        }

        "package の行を書かないと Unexpected（検査も違反にする）" {
            val run = judged(emptyList()) { allow(`class`, "*") }

            run.explanation.packageLine.shouldNotBeNull().verdict shouldBe DeclarationVerdict.Unexpected
            run.failures.any { it.declaration == "package com.example" } shouldBe true
        }

        "package の無いファイルは行が無いので説明も無い" {
            val run = judgedAndExplained("class Sample\n") { `package`("**"); allow(`class`, "*") }

            run.explanation.packageLine.shouldBeNull()
        }
    }

    "import の行" - {
        "許す行に当たる import は Allowed で、当たらないものは Unexpected。行番号は書いた行" {
            val explained = judged(listOf("kotlin.math.max", "java.util.List")) {
                `package`("**")
                `import`("kotlin.**")
                allow(`class`, "*")
            }.explanation.imports.shouldNotBeNull()

            explained.map { "${it.path} ${it.line} ${it.verdict}" } shouldContainExactly listOf(
                "kotlin.math.max 3 Allowed",
                "java.util.List 4 Unexpected",
            )
        }

        "禁止の行に当たる import は、許す行にも当たっても Prohibited" {
            val explained = judged(listOf("java.util.List")) {
                `package`("**")
                `import`("**")
                not.`import`("java.**")
                allow(`class`, "*")
            }.explanation.imports.shouldNotBeNull()

            explained.single().verdict shouldBe DeclarationVerdict.Prohibited
            explained.single().rules.size shouldBe 1
        }

        "import の行を書かないと、どの import も Unexpected" {
            val explained = judged(listOf("kotlin.math.max")) { `package`("**"); allow(`class`, "*") }.explanation.imports.shouldNotBeNull()

            explained.single().verdict shouldBe DeclarationVerdict.Unexpected
        }

        "import が無いファイルは空の一覧" {
            judged(emptyList()) { `package`("**"); allow(`class`, "*") }.explanation.imports.shouldNotBeNull() shouldBe emptyList()
        }
    }

    "検査との一致: 許されない行は、検査が同じ行で落とすものと同じ" {
        val run = judged(listOf("kotlin.math.max", "java.util.List", "org.junit.Test")) {
            `package`("org.**")
            `import`("kotlin.**")
            not.`import`("org.**")
            allow(`class`, "*")
        }

        val rejected = run.failures.filter { it.declaration?.startsWith("import ") == true || it.declaration?.startsWith("package ") == true }
            .map { "${it.declaration} ${it.line}" }.toSet()
        val notAllowed = buildSet {
            run.explanation.packageLine?.takeUnless { it.verdict.isAllowed }?.let { add("package ${it.name} ${it.line}") }
            run.explanation.imports.orEmpty().filterNot { it.verdict.isAllowed }.forEach { add("import ${it.path} ${it.line}") }
        }
        notAllowed shouldBe rejected
    }
})
