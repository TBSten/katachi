@file:OptIn(InternalKatachiApi::class)

package me.tbsten.katachi.test.konsist.declarations

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.shouldBe
import java.io.File
import java.lang.reflect.InvocationTargetException
import me.tbsten.katachi.InternalKatachiApi
import me.tbsten.katachi.check.FileConstraintCheck
import me.tbsten.katachi.check.KatachiArchitectureAssertionError
import me.tbsten.katachi.check.UnsatisfiedFileConstraint
import me.tbsten.katachi.check.Violation
import me.tbsten.katachi.dsl.Architecture
import me.tbsten.katachi.dsl.architecture
import me.tbsten.katachi.dsl.files.KatachiFileSystem
import me.tbsten.katachi.dsl.files.internal.RealFileSystem
import me.tbsten.katachi.dsl.kotlin.ktFile
import me.tbsten.katachi.dsl.wholeTree
import me.tbsten.katachi.konsist.declarations.ktFileContent
import me.tbsten.katachi.test.konsist.fixtureProject

/**
 * The baseline holds back what `ktFileContent { }` rejects today, keyed by the declaration
 * strings of impl-spec 6.1 -- never by a line, so a file that grows above a held-back declaration
 * keeps it held back.
 *
 * The public `assert(...)` walks the project the JVM was started in, and the run that applies a
 * baseline to another tree is internal to `:katachi`; it is reached here by reflection (see
 * [assertWithBaseline]) rather than by opening it up for a spec.
 */

private const val BASELINE: String = "katachi-baseline.json"

/** A ledger holding back the three violations of [VIOLATING_SOURCE], written as katachi writes one. */
private val LEDGER: String = """
    {
      "version": 1,
      "checks": {
        "me.tbsten.katachi.check.FileConstraintCheck": [
          {"rule": "UnsatisfiedFileConstraint", "path": "src/Foo.kt", "role": "Screen", "constraint": "ktFileContent", "declaration": "Foo.isLoading"},
          {"rule": "UnsatisfiedFileConstraint", "path": "src/Foo.kt", "role": "Screen", "constraint": "ktFileContent", "declaration": "FooImpl"},
          {"rule": "UnsatisfiedFileConstraint", "path": "src/Foo.kt", "role": "Screen", "constraint": "ktFileContent", "declaration": "import android.content.Context"}
        ]
      }
    }
""".trimIndent() + "\n"

private const val VIOLATING_SOURCE: String =
    "package com.example\n\nimport android.content.Context\n\nclass Foo {\n    var isLoading = false\n}\n\nclass FooImpl\n"

private val definition: Architecture = architecture {
    files = wholeTree()
    baseline(BASELINE)
    "Screen".role {
        layout {
            "gradlew".file()
            BASELINE.file()
            "src" / "*".ktFile()
        }
        ktFileContent {
            `import`("kotlin.**")
            `class`(fileName) { }
            not.`class`("*Impl")
        }
    }
}

/**
 * `assert(FileConstraintCheck())` against [root], applying the definition's baseline: `:katachi`'s
 * internal `assertWith`, with every environment it takes at its default (read the ledger from
 * disk, update nothing, and write no SARIF report unless `-Dkatachi.report.sarif` asks for one).
 */
private fun assertWithBaseline(root: File): List<Violation> {
    val assertWith = Class.forName("me.tbsten.katachi.check.internal.AssertWithKt").methods.single { method ->
        method.name == "assertWith"
    }
    // What follows the four arguments below is one environment per concern (the baseline's, the
    // SARIF report's), each built from its own defaults, so a new one does not break this spec.
    val environments = assertWith.parameterTypes.drop(4).map { it.getConstructor().newInstance() }
    val fileSystem: KatachiFileSystem = RealFileSystem(root)
    try {
        @Suppress("UNCHECKED_CAST")
        return assertWith.invoke(
            null,
            definition,
            fileSystem,
            listOf(FileConstraintCheck()),
            10,
            *environments.toTypedArray(),
        ) as List<Violation>
    } catch (wrapped: InvocationTargetException) {
        throw wrapped.targetException
    }
}

class KtFileContentBaselineSpec : FreeSpec({
    "台帳にある違反は抑えられる" {
        fixtureProject(BASELINE to LEDGER, "src/Foo.kt" to VIOLATING_SOURCE) { root ->
            assertWithBaseline(root).filterIsInstance<UnsatisfiedFileConstraint>() shouldBe emptyList()
        }
    }

    "行を足して行番号がずれても、抑えたまま" {
        fixtureProject(BASELINE to LEDGER, "src/Foo.kt" to VIOLATING_SOURCE) { root ->
            File(root, "src/Foo.kt").writeText(VIOLATING_SOURCE.replace("class Foo {", "// moved down\n\n\nclass Foo {"))

            assertWithBaseline(root).filterIsInstance<UnsatisfiedFileConstraint>() shouldBe emptyList()
        }
    }

    "台帳に無い新しい違反は落ちる" {
        fixtureProject(BASELINE to LEDGER, "src/Foo.kt" to VIOLATING_SOURCE.replace("class FooImpl", "class FooImpl\nclass BarImpl")) { root ->
            val failure = shouldThrow<KatachiArchitectureAssertionError> { assertWithBaseline(root) }

            failure.violations.filterIsInstance<UnsatisfiedFileConstraint>().map { it.declaration } shouldBe listOf("BarImpl")
        }
    }

    "台帳が無ければ 3 件とも落ち、宣言の文字列は台帳の鍵の形" {
        fixtureProject(BASELINE to "{\"version\": 1, \"checks\": {}}\n", "src/Foo.kt" to VIOLATING_SOURCE) { root ->
            val failure = shouldThrow<KatachiArchitectureAssertionError> { assertWithBaseline(root) }

            failure.violations.filterIsInstance<UnsatisfiedFileConstraint>().map { it.declaration } shouldBe
                listOf("import android.content.Context", "Foo.isLoading", "FooImpl")
        }
    }
})
