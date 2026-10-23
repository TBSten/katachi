package me.tbsten.katachi.test.konsist.declarations

import com.lemonappdev.konsist.api.declaration.KoFileDeclaration
import com.lemonappdev.konsist.core.util.KotlinFileParser
import io.kotest.assertions.withClue
import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.collections.shouldContainAll
import io.kotest.matchers.ints.shouldBeGreaterThan
import io.kotest.matchers.shouldBe
import java.io.File
import me.tbsten.katachi.konsist.declarations.ClassBodyScope
import me.tbsten.katachi.konsist.declarations.DeclarationListScope
import me.tbsten.katachi.konsist.declarations.KtFileContentScope
import me.tbsten.katachi.konsist.declarations.TypeKind

/**
 * The vocabulary is seen inside `ktFileContent { }` only: every word is a member of
 * the scope, and the only top-level declaration of the public package is `ktFileContent` itself.
 *
 * "It does not compile outside" is shown the way it can be without running the compiler from a
 * spec: there is nothing at the top level to call, so a word written outside the block has no
 * receiver to resolve against.
 */

/** The public package's own sources, `internal/` left out. Found from wherever the test runs. */
private fun publicPackageSources(): List<File> {
    val fromModule = File("src/main/kotlin/me/tbsten/katachi/konsist/declarations")
    val directory = if (fromModule.isDirectory) fromModule else File("katachi-konsist/$fromModule")
    return directory.listFiles().orEmpty().filter { it.isFile && it.extension == "kt" }.sortedBy { it.name }
}

/** The names of [file]'s top-level functions and properties that are neither private nor internal. */
private fun publicTopLevelNamesOf(file: KoFileDeclaration): List<String> {
    val functions = file.functions(includeNested = false, includeLocal = false)
        .filterNot { it.hasPrivateModifier || it.hasInternalModifier }
        .map { it.name }
    val properties = file.properties(includeNested = false)
        .filterNot { it.hasPrivateModifier || it.hasInternalModifier }
        .map { it.name }
    return functions + properties
}

/** The Kotlin names of the members of [type], read off its JVM methods (getters lose their `get`). */
private fun memberNamesOf(type: Class<*>): Set<String> = type.methods.map { method ->
    val name = method.name
    if (name.startsWith("get") && name.length > 3 && method.parameterCount == 0) {
        name.removePrefix("get").replaceFirstChar { it.lowercase() }
    } else {
        name
    }
}.toSet()

class KtFileContentScopeIsolationSpec : FreeSpec({
    "トップレベル" - {
        "declarations パッケージのトップレベルに公開の関数と値は ktFileContent しかない" {
            val sources = publicPackageSources()
            sources.size shouldBeGreaterThan 0

            val names = sources.flatMap { publicTopLevelNamesOf(KotlinFileParser.getKoFile(it.absoluteFile)) }

            // `ktFileContent { }` and `"...".ktFileContent { }`.
            names shouldBe listOf("ktFileContent", "ktFileContent")
        }
    }

    "スコープのメンバ" - {
        "修飾子・annotated・種類・fileName・captureValue・not・anythingElse・anyArgs は宣言の一覧のスコープのメンバ" {
            val words = listOf(
                "public", "protected", "private", "internal", "expect", "actual", "abstract", "open",
                "sealed", "const", "external", "override", "lateinit", "tailrec", "suspend", "inner",
                "enum", "annotation", "companion", "inline", "value", "infix", "operator", "data",
                "annotated", "class", "interface", "object", "type", "fun", "val", "var", "typealias",
                "fileName", "captureValue", "not", "anythingElse", "anyArgs",
            )

            memberNamesOf(DeclarationListScope::class.java) shouldContainAll words
            memberNamesOf(ClassBodyScope::class.java) shouldContainAll words
        }

        "package・matchingDirectory・import はファイル直下のスコープだけのメンバ" {
            val fileOnly = listOf("package", "matchingDirectory", "import")

            memberNamesOf(KtFileContentScope::class.java) shouldContainAll fileOnly
            withClue("クラスの { } からは package と import を書けない") {
                memberNamesOf(ClassBodyScope::class.java).intersect(fileOnly.toSet()) shouldBe emptySet()
            }
        }

        "`class` は Object.getClass ではなく種類を返すメンバ" {
            DeclarationListScope::class.java.getMethod("getClass").returnType shouldBe TypeKind::class.java
        }
    }
})
