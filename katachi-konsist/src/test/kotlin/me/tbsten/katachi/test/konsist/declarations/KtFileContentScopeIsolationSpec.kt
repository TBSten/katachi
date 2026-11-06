package me.tbsten.katachi.test.konsist.declarations

import com.lemonappdev.konsist.api.declaration.KoFileDeclaration
import com.lemonappdev.konsist.core.util.KotlinFileParser
import io.kotest.assertions.withClue
import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.collections.shouldContainAll
import io.kotest.matchers.collections.shouldNotContain
import io.kotest.matchers.ints.shouldBeGreaterThan
import io.kotest.matchers.shouldBe
import java.io.File
import me.tbsten.katachi.konsist.declarations.AnythingElse
import me.tbsten.katachi.konsist.declarations.ClassBodyScope
import me.tbsten.katachi.konsist.declarations.DeclarationAllow
import me.tbsten.katachi.konsist.declarations.DeclarationListScope
import me.tbsten.katachi.konsist.declarations.DeclarationRequired
import me.tbsten.katachi.konsist.declarations.FileProhibition
import me.tbsten.katachi.konsist.declarations.FunctionLine
import me.tbsten.katachi.konsist.declarations.ImportLine
import me.tbsten.katachi.konsist.declarations.KtFileContentScope
import me.tbsten.katachi.konsist.declarations.Prohibition
import me.tbsten.katachi.konsist.declarations.PropertyLine
import me.tbsten.katachi.konsist.declarations.TypeAliasLine
import me.tbsten.katachi.konsist.declarations.TypeKind
import me.tbsten.katachi.konsist.declarations.TypeLine

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

/** The parameter types of each method of [type] named [name], one list per overload. */
private fun requiredShapesOf(type: Class<*>, name: String): Set<List<Class<*>>> =
    type.methods.filter { it.name == name }.map { it.parameterTypes.toList() }.toSet()

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
        "修飾子・annotated・種類・fileName・captureValue・not・anythingElse・anyArgs・allow・required は宣言の一覧のスコープのメンバ" {
            val words = listOf(
                "public", "protected", "private", "internal", "expect", "actual", "abstract", "open",
                "sealed", "const", "external", "override", "lateinit", "tailrec", "suspend", "inner",
                "enum", "annotation", "companion", "inline", "value", "infix", "operator", "data",
                "annotated", "class", "interface", "object", "type", "fun", "val", "var", "typealias",
                "fileName", "captureValue", "not", "anythingElse", "anyArgs", "allow", "required",
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

        "not の後ろには required が無い。not.required(...) は書けない" {
            memberNamesOf(Prohibition::class.java) shouldNotContain "required"
            memberNamesOf(FileProhibition::class.java) shouldNotContain "required"
        }

        "required は allow と同じ多重定義の並びで、anythingElse を取るものだけが無い" {
            val allow = requiredShapesOf(DeclarationAllow::class.java, "allow")
            val required = requiredShapesOf(DeclarationRequired::class.java, "required")
            required.size shouldBe 77
            required shouldBe allow.filterNot { AnythingElse::class.java in it }.toSet()
        }

        "宣言の行と import の行に optional() は無い。.optional() は書けない" {
            for (type in listOf(TypeLine::class.java, FunctionLine::class.java, PropertyLine::class.java, TypeAliasLine::class.java, ImportLine::class.java)) {
                withClue(type.simpleName) { memberNamesOf(type) shouldNotContain "optional" }
            }
        }

        "`class` は Object.getClass ではなく種類を返すメンバ" {
            DeclarationListScope::class.java.getMethod("getClass").returnType shouldBe TypeKind::class.java
        }
    }
})
